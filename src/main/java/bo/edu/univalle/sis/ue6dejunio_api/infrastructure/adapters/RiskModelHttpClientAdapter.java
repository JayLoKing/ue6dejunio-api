package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.RiskModelRejectedException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.RiskModelUnavailableException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskFeatures;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskModelClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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

    /**
     * Enough to see the pattern. A refused batch of five hundred is five hundred of the same bug.
     */
    private static final int SHAPES_LOGGED = 3;

    /**
     * For reading the model's error bodies, and nothing else.
     *
     * <p>Its own mapper rather than the application's, on purpose: parsing another service's
     * failure must not shift because somebody changed how this application serializes its own
     * responses. Defaults are the whole configuration — it reads two well-known fields out of a
     * document this side did not write.
     */
    private static final JsonMapper ERROR_JSON = JsonMapper.builder().build();

    private final RestClient restClient;
    private final String token;

    /**
     * Prefers the application's own {@code RestClient.Builder} when one is configured, so this
     * client reads JSON the way the rest of the application does. Optional rather than required:
     * this application does not currently define one, and a missing builder is a default worth
     * falling back to, not a reason for the whole context to refuse to start.
     *
     * @param connectTimeout how long to wait for the model to accept a connection. Short: either
     *     the process is listening on the other side of loopback or it is not.
     * @param readTimeout how long to wait for it to answer once it has. Generous by comparison,
     *     because the first call of the day loads TensorFlow before it predicts anything, and a
     *     timeout that fires on a cold start would make the feature look broken every morning.
     */
    @Autowired
    public RiskModelHttpClientAdapter(
            ObjectProvider<RestClient.Builder> builders,
            @Value("${app.prediction.url}") String baseUrl,
            @Value("${app.prediction.token}") String token,
            @Value("${app.prediction.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.prediction.read-timeout:60s}") Duration readTimeout) {
        this(
                builders.getIfAvailable(RestClient::builder)
                        .requestFactory(requestFactory(connectTimeout, readTimeout)),
                baseUrl,
                token);
    }

    /**
     * The transport, pinned on both counts the defaults get wrong.
     *
     * <p>Set on the production path only. The package-private constructor leaves the transport to
     * whoever builds it, so a test can put a stub behind this client.
     */
    private static JdkClientHttpRequestFactory requestFactory(Duration connect, Duration read) {
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(predictionHttpClient(connect));
        // The connect timeout belongs to the client and covers only the handshake. Everything after
        // it — a model that accepted the socket and then went quiet — is bounded here or not at
        // all.
        factory.setReadTimeout(read);
        return factory;
    }

    /**
     * A client that offers HTTP/1.1 and nothing else, because that is all the model speaks.
     *
     * <p>Java's own client defaults to HTTP/2. Over plaintext that is an h2c upgrade, and uvicorn —
     * which serves the model — does not implement it: it logs "Unsupported upgrade request" and
     * falls back to 1.1, but the request body does not survive the exchange. FastAPI then rejects
     * the call for a missing body, and the vector this side spent three queries assembling is never
     * seen by anything. The error names a field and not a protocol, which is exactly what makes it
     * expensive to find.
     *
     * <p>It also waits forever by default. The sweep runs outside a transaction and calls the model
     * once per chunk of five hundred, so a service that accepts the connection and then stops
     * answering parks a thread per chunk with nothing to end it — and the run neither finishes nor
     * fails, which is the worse of the two.
     */
    static HttpClient predictionHttpClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
    }

    /** Takes the builder directly, so a test can put a stub server behind this client. */
    RiskModelHttpClientAdapter(RestClient.Builder builder, String baseUrl, String token) {
        this.restClient =
                builder.baseUrl(baseUrl)
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
            List<RiskFeatures> chunk =
                    batch.subList(from, Math.min(from + MAX_BATCH, batch.size()));
            scores.addAll(scoreChunk(chunk));
        }
        return scores;
    }

    private List<RiskScore> scoreChunk(List<RiskFeatures> chunk) {
        List<PredictionResponse> answers = send(chunk);

        if (answers == null || answers.size() != chunk.size()) {
            throw new RiskModelUnavailableException(
                    "The model answered "
                            + (answers == null ? "nothing" : answers.size() + " scores")
                            + " for "
                            + chunk.size()
                            + " vectors. Position is the only thing tying a score "
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
            return restClient
                    .post()
                    .uri("/predict/batch")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(new BatchRequest(chunk.stream().map(FeatureDto::from).toList()))
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            (request, response) -> {
                                String reason = reasonFrom(response);
                                log.error(
                                        "The model answered {} to a batch of {}: {}",
                                        response.getStatusCode(),
                                        chunk.size(),
                                        reason);

                                String message =
                                        "The model answered "
                                                + response.getStatusCode()
                                                + " to a batch of "
                                                + chunk.size()
                                                + " vectors: "
                                                + reason
                                                + ". Sent "
                                                + shapeOf(chunk);

                                // A 4xx is a refusal, not an outage: the service answered, and it
                                // answered that
                                // this batch breaks a rule it enforces. Retrying cannot change
                                // that, and saying
                                // "unavailable" sends somebody to restart a process that never
                                // stopped.
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
                .map(
                        v ->
                                "[being="
                                        + v.being().size()
                                        + " knowing="
                                        + v.knowing().size()
                                        + " doing="
                                        + v.doing().size()
                                        + " deciding="
                                        + v.deciding().size()
                                        + " attendance="
                                        + (v.attendancePct() == null ? "null" : "set")
                                        + " planned="
                                        + v.plannedCriteria()
                                        + "]")
                .collect(Collectors.joining(", "));
    }

    /**
     * Why the model refused, in its own words — and only the words that explain it.
     *
     * <p>FastAPI answers two shapes under {@code detail}. A raised refusal puts a sentence there,
     * and for this endpoint that sentence names the offending vectors. A schema rejection puts a
     * list of errors instead, each carrying {@code loc} and {@code msg} — the diagnosis — beside
     * {@code input}, which is the batch echoed back: the marks of real students.
     *
     * <p>So this reads the explanation and leaves the evidence. {@code input} never reaches a log,
     * truncated or otherwise, because a shorter excerpt of a child's grades is still a child's
     * grades. A body of an unrecognised shape is reported by size alone for the same reason: an
     * unknown shape is one nobody has checked for private data.
     *
     * <p>Never throws. This runs while another failure is already being reported, and a client that
     * dies reading the explanation replaces a diagnosable error with an unrelated one.
     */
    private static String reasonFrom(ClientHttpResponse response) {
        byte[] raw;
        try (InputStream body = response.getBody()) {
            raw = body.readAllBytes();
        } catch (IOException | RuntimeException e) {
            return "body unreadable: " + e.getClass().getSimpleName();
        }

        if (raw.length == 0) {
            return "no body";
        }

        try {
            JsonNode detail = ERROR_JSON.readTree(raw).path("detail");
            if (detail.isTextual()) {
                return trimmed(detail.stringValue());
            }
            if (detail.isArray()) {
                return trimmed(describeValidationErrors(detail));
            }
        } catch (RuntimeException e) {
            // Falls through to the size-only report: an unparseable body is one whose contents
            // nobody has classified, and quoting it is how the marks get out through the path
            // that was never reasoned about.
            log.debug("The model's error body was not JSON this side understands", e);
        }

        return "unreadable body of " + raw.length + " bytes";
    }

    /** Each validation error as {@code where: what}, with the rejected value left behind. */
    private static String describeValidationErrors(JsonNode errors) {
        List<String> described = new ArrayList<>();
        for (JsonNode error : errors) {
            StringJoiner where = new StringJoiner(".");
            for (JsonNode part : error.path("loc")) {
                where.add(part.asString());
            }
            described.add(where + ": " + error.path("msg").asString());
        }
        return String.join("; ", described);
    }

    private static String trimmed(String reason) {
        return reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) + "…" : reason;
    }

    private static RiskScore toScore(PredictionResponse answer) {
        RiskLevel level =
                RiskLevel.fromModel(answer.riskLevel())
                        .orElseThrow(
                                () ->
                                        new RiskModelUnavailableException(
                                                "The model answered a category this system does not know: "
                                                        + answer.riskLevel()
                                                        + ". A retrained model with a new category has to be taught here before its "
                                                        + "answers can be stored."));
        return new RiskScore(level, answer.pFail(), answer.pOutstanding());
    }

    /** The endpoint takes the vectors wrapped in {@code items}; it answers with a bare list. */
    private record BatchRequest(List<FeatureDto> items) {}

    /**
     * One vector as the model spells it.
     *
     * <p>{@code gestion} is deliberately not sent. Absent, the model normalises against the
     * weighting in force, which is the only one this system's marks are ever on — it exists for
     * historical spreadsheets, and naming it here would be declaring a scale nobody is using.
     *
     * <p>The wire names the model speaks are Spanish; the components here are named as the domain
     * names them. The {@code @JsonProperty} annotations are the whole translation, so a reader of
     * this class never has to hold two vocabularies at once to follow a field.
     */
    private record FeatureDto(
            List<BigDecimal> being,
            List<BigDecimal> knowing,
            List<BigDecimal> doing,
            List<BigDecimal> deciding,
            @JsonProperty("attendance_pct") BigDecimal attendancePct,
            @JsonProperty("criterios_planificados") Integer plannedCriteria) {
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
            @JsonProperty("p_reprueba") BigDecimal pFail,
            @JsonProperty("p_sobresaliente") BigDecimal pOutstanding) {}
}
