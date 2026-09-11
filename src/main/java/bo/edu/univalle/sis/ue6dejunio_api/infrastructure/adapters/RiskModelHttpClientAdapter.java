package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskFeatures;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskModelClient;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.RiskModelRejectedException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.RiskModelUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The model, over HTTP.
 *
 * <p>One direction: this side calls, the model answers, and nothing is registered anywhere. The
 * model service can be down, restarted or moved without leaving a stale callback behind.
 */
@Component
public class RiskModelHttpClientAdapter implements IRiskModelClient {

    private static final Logger log = LoggerFactory.getLogger(RiskModelHttpClientAdapter.class);

    /**
     * The most vectors the model will take in one request.
     *
     * <p>Its own limit, declared on the endpoint, and it rejects the whole request for being one
     * over. A sweep of the school is thousands of vectors, so sending the batch as it comes is not
     * a slow path — it is a run that fails every time, and fails as a validation error about a
     * field nobody wrote.
     */
    private static final int MAX_BATCH = 500;

    /**
     * How much of a refusal to keep. FastAPI's own {@code detail} is one short sentence, but a
     * schema rejection echoes the input back, and that input is a whole class's marks.
     */
    private static final int MAX_REASON = 500;

    /** Enough to see the pattern. A refused batch of five hundred is five hundred of the same bug. */
    private static final int SHAPES_LOGGED = 3;

    private final RestClient restClient;
    private final String token;

    /**
     * Prefers the application's own {@code RestClient.Builder} when one is configured, so this
     * client reads JSON the way the rest of the application does. Optional rather than required:
     * this application does not currently define one, and a missing builder is a default worth
     * falling back to, not a reason for the whole context to refuse to start.
     */
    @Autowired
    public RiskModelHttpClientAdapter(
        ObjectProvider<RestClient.Builder> builders,
        @Value("${app.prediction.url}") String baseUrl,
        @Value("${app.prediction.token}") String token
    ) {
        this(builders.getIfAvailable(RestClient::builder), baseUrl, token);
    }

    /** Takes the builder directly, so a test can put a stub server behind this client. */
    RiskModelHttpClientAdapter(RestClient.Builder builder, String baseUrl, String token) {
        this.restClient = builder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
        this.token = token;
    }

    @Override
    public List<RiskScore> predictBatch(List<RiskFeatures> batch) {
        if (batch.isEmpty()) {
            return List.of();
        }

        List<RiskScore> scores = new ArrayList<>(batch.size());
        for (int from = 0; from < batch.size(); from += MAX_BATCH) {
            List<RiskFeatures> chunk = batch.subList(from, Math.min(from + MAX_BATCH, batch.size()));
            scores.addAll(scoreChunk(chunk));
        }
        return scores;
    }

    private List<RiskScore> scoreChunk(List<RiskFeatures> chunk) {
        List<PredictionResponse> answers = send(chunk);

        if (answers == null || answers.size() != chunk.size()) {
            throw new RiskModelUnavailableException(
                "The model answered " + (answers == null ? "nothing" : answers.size() + " scores")
                    + " for " + chunk.size() + " vectors. Position is the only thing tying a score "
                    + "to its student, so a different length cannot be read as a partial result.");
        }

        return answers.stream().map(RiskModelHttpClientAdapter::toScore).toList();
    }

    /**
     * The call itself, and the only place a transport failure is turned into a domain one.
     *
     * <p>Narrow on purpose. Wrapping the whole method in {@code catch (Exception)} also catches the
     * failures this class raises deliberately — a bad status, a short answer, a category the model
     * was not trained on — and re-labels every one of them "could not reach the model service",
     * which is the one thing that did not happen. The real reason then exists nowhere.
     */
    private List<PredictionResponse> send(List<RiskFeatures> chunk) {
        try {
            return restClient.post()
                .uri("/predict/batch")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new BatchRequest(chunk.stream().map(FeatureDto::from).toList()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    String reason = reasonFrom(response);
                    log.error("The model answered {} to a batch of {}: {}",
                        response.getStatusCode(), chunk.size(), reason);

                    String message = "The model answered " + response.getStatusCode()
                        + " to a batch of " + chunk.size() + " vectors: " + reason
                        + ". Sent " + shapeOf(chunk);

                    // A 4xx is a refusal, not an outage: the service answered, and it answered that
                    // this batch breaks a rule it enforces. Retrying cannot change that, and saying
                    // "unavailable" sends somebody to restart a process that never stopped.
                    throw response.getStatusCode().is4xxClientError()
                        ? new RiskModelRejectedException(message)
                        : new RiskModelUnavailableException(message);
                })
                .body(new ParameterizedTypeReference<List<PredictionResponse>>() {});
        } catch (RiskModelUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Could not reach the model service", e);
            throw new RiskModelUnavailableException("Could not reach the model service.", e);
        }
    }

    /**
     * How many marks went into each dimension, for the first few vectors of a refused batch.
     *
     * <p>Counts and not marks, on purpose. A refusal is logged, and a log is not a place for a
     * class's grades — but the question a refusal raises is whether the four lists were populated
     * on this side at all, and a count answers exactly that while a grade answers nothing. With it,
     * "the model says the vector is empty" and "the vector left here full" are distinguishable,
     * which is the difference between a bug in the assembler and a bug on the wire.
     */
    private static String shapeOf(List<RiskFeatures> chunk) {
        return chunk.stream()
            .limit(SHAPES_LOGGED)
            .map(v -> "[being=" + v.being().size() + " knowing=" + v.knowing().size()
                + " doing=" + v.doing().size() + " deciding=" + v.deciding().size()
                + " attendance=" + (v.attendancePct() == null ? "null" : "set")
                + " planned=" + v.plannedCriteria() + "]")
            .collect(Collectors.joining(", "));
    }

    /**
     * Why the model refused, in its own words.
     *
     * <p>FastAPI puts it in {@code detail}, and for this endpoint that string names the offending
     * vectors. It is read here because the body is a one-shot stream: past this handler the answer
     * is gone, and the only fact worth having is gone with it.
     *
     * <p>Never throws. This runs while another failure is already being reported, and a client that
     * dies reading the explanation replaces a diagnosable error with an unrelated one.
     */
    private static String reasonFrom(ClientHttpResponse response) {
        try (InputStream body = response.getBody()) {
            String raw = new String(body.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return "no body";
            }
            // Trimmed because the whole batch can come back inside a validation error, and the
            // marks of a class are not something to copy into a log line.
            return raw.length() > MAX_REASON ? raw.substring(0, MAX_REASON) + "…" : raw;
        } catch (IOException | RuntimeException e) {
            return "body unreadable: " + e.getMessage();
        }
    }

    private static RiskScore toScore(PredictionResponse answer) {
        RiskLevel level = RiskLevel.fromModel(answer.riskLevel())
            .orElseThrow(() -> new RiskModelUnavailableException(
                "The model answered a category this system does not know: " + answer.riskLevel()
                    + ". A retrained model with a new category has to be taught here before its "
                    + "answers can be stored."));
        return new RiskScore(level, answer.pReprueba(), answer.pSobresaliente());
    }

    /** The endpoint takes the vectors wrapped in {@code items}; it answers with a bare list. */
    private record BatchRequest(List<FeatureDto> items) {
    }

    /**
     * One vector as the model spells it.
     *
     * <p>{@code gestion} is deliberately not sent. Absent, the model normalises against the
     * weighting in force, which is the only one this system's marks are ever on — it exists for
     * historical spreadsheets, and naming it here would be declaring a scale nobody is using.
     */
    private record FeatureDto(
        List<BigDecimal> being,
        List<BigDecimal> knowing,
        List<BigDecimal> doing,
        List<BigDecimal> deciding,
        @JsonProperty("attendance_pct") BigDecimal attendancePct,
        @JsonProperty("criterios_planificados") Integer criteriosPlanificados
    ) {
        static FeatureDto from(RiskFeatures features) {
            return new FeatureDto(
                features.being(),
                features.knowing(),
                features.doing(),
                features.deciding(),
                features.attendancePct(),
                features.plannedCriteria());
        }
    }

    /**
     * The two probabilities the school reads, and nothing else.
     *
     * <p>The model also answers {@code probabilidades}, all four classes, which this side has no
     * use for: the level it chose is already the argmax of them. Unknown properties are ignored
     * rather than mapped, so retraining the model with a fifth class or a new diagnostic field does
     * not turn every prediction into a parse failure — and so this client does not depend on how
     * the converters that happen to serve it were configured.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PredictionResponse(
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("p_reprueba") BigDecimal pReprueba,
        @JsonProperty("p_sobresaliente") BigDecimal pSobresaliente
    ) {
    }
}
