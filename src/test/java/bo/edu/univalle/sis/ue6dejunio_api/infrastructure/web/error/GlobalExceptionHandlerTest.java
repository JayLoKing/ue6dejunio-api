package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.error;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ErrorResponse;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler("always", "always");

    /** Everything the handler logs while one test runs. */
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    private ch.qos.logback.classic.Logger handlerLogger() {
        return ((LoggerContext) LoggerFactory.getILoggerFactory())
            .getLogger(GlobalExceptionHandler.class);
    }

    @BeforeEach
    void listenToTheHandlersLog() {
        logged.start();
        handlerLogger().addAppender(logged);
    }

    @AfterEach
    void stopListening() {
        handlerLogger().detachAppender(logged);
        logged.stop();
    }

    private static MockHttpServletRequest requestTo(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(uri);
        return req;
    }

    /**
     * The half of a race the database turns away.
     *
     * <p>Uniqueness is now held by a constraint rather than by a look-before-you-write, which is
     * the point — but it means the losing caller learns about it from Postgres instead of from the
     * service. Without this mapping that arrives as a 500: the caller is told the server broke when
     * what happened is that someone else got there first, which is exactly the 409 the service
     * itself answers in the ordinary case.
     */
    /** What a unique violation looks like once Hibernate has wrapped it: SQL state 23505. */
    private static DataIntegrityViolationException duplicateThroughJpa() {
        return new DataIntegrityViolationException("could not execute statement",
            new SQLException("duplicate key", "23505"));
    }

    /**
     * The same conflict, translated by the other half of the framework.
     *
     * <p>JdbcTemplate turns a unique violation into this subclass while Hibernate produces the
     * plain parent, and every write in this application goes through JPA. Keyed on the type alone,
     * the handler would answer 500 to every real duplicate — which is why both paths are pinned.
     */
    private static DuplicateKeyException duplicateThroughJdbcTemplate() {
        return new DuplicateKeyException("uq_adaptation_plan_student");
    }

    @Test
    void aRowTheDatabaseRefusesAsDuplicateAnswers409() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
            duplicateThroughJpa(), requestTo("/api/adaptations"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path()).isEqualTo("/api/adaptations");
    }

    @Test
    void aDuplicateTranslatedByJdbcTemplateAnswers409Too() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
            duplicateThroughJdbcTemplate(), requestTo("/api/adaptations"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // Losing a race is not an outage, so it is not an ERROR — but the cause still has to survive,
    // or a duplicate that keeps happening leaves nothing to look at.
    @Test
    void aLostRaceIsRecordedWithoutBeingCalledAnOutage() {
        handler.handleDataIntegrity(duplicateThroughJpa(), requestTo("/api/adaptations"));

        assertThat(logged.list).singleElement()
            .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    /**
     * Only a duplicate is the caller's doing.
     *
     * <p>{@link DataIntegrityViolationException} is the parent of every integrity failure Spring
     * translates: a NOT NULL, a foreign key or a check violation is this code writing a row it had
     * no business writing. Answering those 409 would tell the caller to resolve a conflict that is
     * not theirs, and they would retry forever against a bug nobody could see.
     */
    @Test
    void anIntegrityFailureThatIsNotADuplicateStaysAServerFailure() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
            new DataIntegrityViolationException("not-null constraint",
                new SQLException("null value in column \"names\"", "23502")),
            requestTo("/api/students"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(logged.list).singleElement()
            .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }

    // An integrity failure with no SQL state to read is not evidence of a conflict. Guessing 409
    // would hand a server bug back as the caller's problem, which is the failure worth avoiding.
    @Test
    void anIntegrityFailureThatNamesNoSqlStateStaysAServerFailure() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
            new DataIntegrityViolationException("something went wrong"),
            requestTo("/api/students"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * The one failure nobody can explain from the outside.
     *
     * <p>The body a 500 returns says "Error interno del servidor" and nothing else — on purpose,
     * because the caller must not be handed the internals. That leaves the log as the only place
     * the cause survives, and the handler wrote nothing there: every unexpected failure in
     * production died silent, with no stacktrace anywhere and no way to tell which request caused
     * it.
     */
    @Test
    void anUnexpectedFailureIsLoggedWithItsPathAndItsCause() {
        handler.handleGeneric(new IllegalStateException("boom"), requestTo("/api/pdc"));

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("/api/pdc");
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    /**
     * A path with no handler is the caller asking for something that is not there, and the handler
     * already answers it 404. Logging it at ERROR would fill the log with other people's typos and
     * bury the failures worth reading.
     */
    @Test
    void aRouteWithNoHandlerIsAnsweredNotLogged() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
            new NoHandlerFoundException("GET", "/api/does-not-exist", new HttpHeaders()),
            requestTo("/api/does-not-exist"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(logged.list).isEmpty();
    }

    /**
     * Not every exception Spring names is the caller's fault.
     *
     * <p>The branch that trusts Spring's own status covers 404 and 405, but also carries server
     * failures: a request that times out waiting on an async result answers 503. Those are the
     * server breaking under load, and they used to return through that branch without a line in
     * the log — the same silence this handler was just taught to break, one branch over.
     */
    @Test
    void aServerFailureSpringAlreadyNamedIsLoggedToo() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
            new AsyncRequestTimeoutException(), requestTo("/api/pdc"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("/api/pdc");
        });
    }

    // The message names the constraint, and a constraint name is a schema detail: it tells the
    // caller nothing they can act on and tells an attacker the shape of the tables.
    @Test
    void doesNotHandTheConstraintNameBackToTheCaller() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
            duplicateThroughJdbcTemplate(), requestTo("/api/adaptations"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).doesNotContain("uq_adaptation_plan_student");
    }
}
