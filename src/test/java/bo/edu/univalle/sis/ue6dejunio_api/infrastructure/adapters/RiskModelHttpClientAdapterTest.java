package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskFeatures;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.RiskModelRejectedException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.RiskModelUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The wire contract, against a stub of the model service.
 *
 * <p>Everything here is a promise made by another process in another language: the field names it
 * reads, the shape it answers with, and the size of batch it will accept. Nothing in Java fails at
 * compile time when one of those changes, so it is checked here or it is discovered in production.
 */
class RiskModelHttpClientAdapterTest {

    private static final String BASE_URL = "http://localhost:8001";
    private static final String TOKEN = "s3cret";

    private MockRestServiceServer server;
    private RiskModelHttpClientAdapter adapter;

    @BeforeEach
    void buildAdapter() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new RiskModelHttpClientAdapter(builder, BASE_URL, TOKEN);
    }

    private static RiskFeatures vector() {
        return new RiskFeatures(
            UUID.randomUUID(), UUID.randomUUID(), 1,
            List.of(new BigDecimal("8")),
            List.of(new BigDecimal("30"), new BigDecimal("35")),
            List.of(new BigDecimal("25")),
            List.of(new BigDecimal("4")),
            new BigDecimal("87.50"), 8);
    }

    private static String answers(int count, String level) {
        return IntStream.range(0, count)
            .mapToObj(i -> """
                {"risk_level":"%s","p_reprueba":0.0128,"p_sobresaliente":0.1059,
                 "probabilidades":{"EnRiesgo":0.3,"RiesgoCritico":0.0128,"SinRiesgo":0.5,
                 "Sobresaliente":0.1059}}""".formatted(level))
            .collect(Collectors.joining(",", "[", "]"));
    }

    @Test
    void predictBatch_nothingToScore_neverCallsTheModel() {
        assertThat(adapter.predictBatch(List.of())).isEmpty();
        server.verify();
    }

    /**
     * The field names are the model's, not this system's. It reads {@code attendance_pct} and
     * {@code criterios_planificados}; sending {@code attendancePct} is not a rejected request, it
     * is an accepted one where that feature silently arrives absent.
     */
    @Test
    void predictBatch_sendsTheVectorUnderTheNamesTheModelReads() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer " + TOKEN))
            .andExpect(jsonPath("$.items[0].being[0]").value(8))
            .andExpect(jsonPath("$.items[0].knowing[1]").value(35))
            .andExpect(jsonPath("$.items[0].doing[0]").value(25))
            .andExpect(jsonPath("$.items[0].deciding[0]").value(4))
            .andExpect(jsonPath("$.items[0].attendance_pct").value(87.50))
            .andExpect(jsonPath("$.items[0].criterios_planificados").value(8))
            .andRespond(withSuccess(answers(1, "EnRiesgo"), MediaType.APPLICATION_JSON));

        adapter.predictBatch(List.of(vector()));

        server.verify();
    }

    @Test
    void predictBatch_readsTheTwoProbabilitiesAndTheCategory() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withSuccess(answers(1, "RiesgoCritico"), MediaType.APPLICATION_JSON));

        List<RiskScore> scores = adapter.predictBatch(List.of(vector()));

        assertThat(scores).singleElement().satisfies(score -> {
            assertThat(score.level()).isEqualTo(RiskLevel.RIESGO_CRITICO);
            assertThat(score.pFail()).isEqualByComparingTo("0.0128");
            assertThat(score.pOutstanding()).isEqualByComparingTo("0.1059");
        });
    }

    /**
     * The endpoint caps a request at 500 vectors and rejects the whole thing for being one over. A
     * sweep of the school is thousands, so an unchunked batch is not a slow run — it is a run that
     * fails every time.
     */
    @Test
    void predictBatch_moreVectorsThanTheModelAccepts_isSplitIntoRequestsItWillTake() {
        List<RiskFeatures> batch = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            batch.add(vector());
        }
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andExpect(jsonPath("$.items.length()").value(500))
            .andRespond(withSuccess(answers(500, "SinRiesgo"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andExpect(jsonPath("$.items.length()").value(500))
            .andRespond(withSuccess(answers(500, "SinRiesgo"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andExpect(jsonPath("$.items.length()").value(200))
            .andRespond(withSuccess(answers(200, "EnRiesgo"), MediaType.APPLICATION_JSON));

        List<RiskScore> scores = adapter.predictBatch(batch);

        assertThat(scores).hasSize(1200);
        assertThat(scores.get(1199).level()).isEqualTo(RiskLevel.EN_RIESGO);
        server.verify();
    }

    /** The chunks come back in the order they were sent, or every score lands on a stranger. */
    @Test
    void predictBatch_keepsTheChunksInTheOrderTheVectorsWereGiven() {
        List<RiskFeatures> batch = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            batch.add(vector());
        }
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withSuccess(answers(500, "SinRiesgo"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withSuccess(answers(1, "RiesgoCritico"), MediaType.APPLICATION_JSON));

        List<RiskScore> scores = adapter.predictBatch(batch);

        assertThat(scores.get(499).level()).isEqualTo(RiskLevel.SIN_RIESGO);
        assertThat(scores.get(500).level()).isEqualTo(RiskLevel.RIESGO_CRITICO);
    }

    /**
     * The status has to survive into the message. Caught by a blanket handler and relabelled
     * "could not reach the model service", a 422 about a mark over its dimension's cap becomes
     * indistinguishable from the service being down, and the real reason exists nowhere.
     */
    @Test
    void predictBatch_theModelRejectsTheBatch_saysWhatItAnswered() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> adapter.predictBatch(List.of(vector())))
            .isInstanceOf(RiskModelRejectedException.class)
            .hasMessageContaining("422");
    }

    /**
     * The model says why in the body, and that sentence is the whole diagnosis.
     *
     * <p>A 422 from this endpoint names the offending items — "Items sin nota en alguna dimension:
     * [0]" — which is the difference between knowing the batch disagreed with the model's own rule
     * and knowing nothing at all. Read and discarded, the one fact worth having is thrown away at
     * the only moment it exists, and whoever debugs it next has to reproduce the call by hand.
     */
    @Test
    void predictBatch_theModelSaysWhyItRefused_keepsThatReason() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":\"Items sin nota en alguna dimension: [0]\"}"));

        assertThatThrownBy(() -> adapter.predictBatch(List.of(vector())))
            .isInstanceOf(RiskModelRejectedException.class)
            .hasMessageContaining("Items sin nota en alguna dimension: [0]");
    }

    /**
     * What this side believed it was sending, next to what the model says it received.
     *
     * <p>Counts and not marks. The two only disagree through serialization, and the question that
     * settles it is whether the four lists were populated here at all — which a count answers and a
     * student's grades do not. Without it the same refusal has two unrelated causes, a vector this
     * side assembled short and a body that lost its fields on the wire, and no way to choose.
     */
    @Test
    void predictBatch_refused_saysWhatShapeItBelievedItWasSending() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> adapter.predictBatch(List.of(vector())))
            .isInstanceOf(RiskModelRejectedException.class)
            .hasMessageContaining("being=1")
            .hasMessageContaining("knowing=2")
            .hasMessageContaining("doing=1")
            .hasMessageContaining("deciding=1")
            .hasMessageContaining("planned=8");
    }

    /** Counts, never marks: a refusal is logged, and a log is not a place for a class's grades. */
    @Test
    void predictBatch_refused_doesNotPutTheMarksThemselvesInTheMessage() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> adapter.predictBatch(List.of(vector())))
            .isInstanceOf(RiskModelRejectedException.class)
            .hasMessageNotContaining("87.50")
            .hasMessageNotContaining("35");
    }

    /**
     * A schema refusal answers with the input that broke it, and that input is a class's marks.
     *
     * <p>FastAPI's validation errors carry {@code loc}, {@code msg} and {@code input}. The first
     * two are the diagnosis; the third is the vectors echoed back — the grades of real children,
     * on their way into a log file that outlives the request and gets copied into bug reports.
     * Kept out entirely rather than truncated: a shorter excerpt of a student's marks is still a
     * student's marks.
     */
    @Test
    void predictBatch_refusedOnSchema_keepsTheDiagnosisAndDropsTheMarks() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {"detail":[{"type":"less_than_equal",
                                "loc":["body","items",0,"attendance_pct"],
                                "msg":"Input should be less than or equal to 100",
                                "input":137.5}]}"""));

        assertThatThrownBy(() -> adapter.predictBatch(List.of(vector())))
            .isInstanceOf(RiskModelRejectedException.class)
            .hasMessageContaining("body.items.0.attendance_pct")
            .hasMessageContaining("Input should be less than or equal to 100")
            .hasMessageNotContaining("137.5");
    }

    /**
     * An answer this side cannot parse says how big it was and nothing else.
     *
     * <p>Echoing an unrecognised body verbatim is how the marks get out through the one path that
     * was never reasoned about. If the shape is unknown, so is whether it holds anything private.
     */
    @Test
    void predictBatch_refusedWithAnUnreadableBody_quotesNoneOfIt() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Ana Quispe 87.5 / Luis Mamani 42.0"));

        assertThatThrownBy(() -> adapter.predictBatch(List.of(vector())))
            .isInstanceOf(RiskModelRejectedException.class)
            .hasMessageNotContaining("Ana Quispe")
            .hasMessageNotContaining("87.5")
            .hasMessageContaining("unreadable");
    }

    /**
     * A refusal and an outage are different problems for different people.
     *
     * <p>A 4xx means the model was reached, understood the request and refused it: the batch this
     * side built disagrees with the rule the model enforces, and that is a defect here. A 5xx or a
     * dead socket means nobody could answer. Collapsing both into "the model is unavailable" sends
     * a teacher to restart a service that was running the whole time.
     */
    @Test
    void predictBatch_theModelIsDown_isNotTheSameFailureAsBeingRefused() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.predictBatch(List.of(vector())))
            .isInstanceOf(RiskModelUnavailableException.class)
            .isNotInstanceOf(RiskModelRejectedException.class);
    }

    @Test
    void predictBatch_theModelIsBroken_saysWhatItAnswered() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.predictBatch(List.of(vector())))
            .isInstanceOf(RiskModelUnavailableException.class)
            .hasMessageContaining("500");
    }

    /** Position is the pairing. A short answer is not a partial result, it is a misfiling. */
    @Test
    void predictBatch_fewerAnswersThanVectors_refusesToPairThemUp() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withSuccess(answers(1, "SinRiesgo"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.predictBatch(List.of(vector(), vector())))
            .isInstanceOf(RiskModelUnavailableException.class)
            .hasMessageContaining("1 scores for 2 vectors");
    }

    /**
     * A category this side has never been taught is a retrained model, not a typo. Guessing at it
     * would store a level nobody trained and that the column's CHECK would refuse anyway.
     */
    @Test
    void predictBatch_aCategoryThisSystemDoesNotKnow_refusesToGuess() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withSuccess(answers(1, "RiesgoModerado"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.predictBatch(List.of(vector())))
            .isInstanceOf(RiskModelUnavailableException.class)
            .hasMessageContaining("RiesgoModerado");
    }

    /** A diagnostic field added on the model's side must not turn every prediction into a failure. */
    @Test
    void predictBatch_anAnswerCarryingFieldsThisSideDoesNotRead_isStillRead() {
        server.expect(requestTo(BASE_URL + "/predict/batch"))
            .andRespond(withSuccess("""
                [{"risk_level":"SinRiesgo","p_reprueba":0.02,"p_sobresaliente":0.10,
                  "probabilidades":{"SinRiesgo":0.88},"modelo":"tfdf-2026-09","latencia_ms":12}]""",
                MediaType.APPLICATION_JSON));

        assertThat(adapter.predictBatch(List.of(vector())))
            .singleElement()
            .satisfies(score -> assertThat(score.level()).isEqualTo(RiskLevel.SIN_RIESGO));
    }
}
