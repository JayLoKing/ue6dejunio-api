package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.error;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler("always", "always");

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
