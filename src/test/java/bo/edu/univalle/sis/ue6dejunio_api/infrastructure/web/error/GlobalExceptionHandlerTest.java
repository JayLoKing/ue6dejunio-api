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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

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
    @Test
    void aRowTheDatabaseRefusesAsDuplicateAnswers409() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
            new DataIntegrityViolationException("uq_adaptation_plan_student"),
            requestTo("/api/adaptations"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path()).isEqualTo("/api/adaptations");
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

    // The message names the constraint, and a constraint name is a schema detail: it tells the
    // caller nothing they can act on and tells an attacker the shape of the tables.
    @Test
    void doesNotHandTheConstraintNameBackToTheCaller() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
            new DataIntegrityViolationException("uq_adaptation_plan_student"),
            requestTo("/api/adaptations"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).doesNotContain("uq_adaptation_plan_student");
    }
}
