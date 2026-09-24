package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec: a request parameter the API refuses is the caller's mistake, answered 400.
 *
 * <p>Every listing in this API pages with {@code @Min}/{@code @Max} on the parameters and
 * {@code @Validated} on the controller class. That combination validates through the AOP proxy and
 * throws {@link jakarta.validation.ConstraintViolationException}, which is not one of Spring's own
 * {@code ErrorResponse} types — so the catch-all fell through to 500 and reported the caller asking
 * for too many rows as a server failure. Fifteen controllers page this way, so this is pinned once
 * on the handler rather than fifteen times on the controllers.
 */
class RequestValidationErrorIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    /**
     * Levels list with no ownership guard of its own: what is under test is the paging, not access.
     */
    private static final String ANY_LISTING = "/api/levels";

    @Test
    void pagingLimitPastTheCeiling_returns400_not500() throws Exception {
        UUID director = seedUser("Director", false);

        mvc.perform(
                        get(ANY_LISTING + "?limit=500")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagingOffsetBelowTheFirstPage_returns400_not500() throws Exception {
        UUID director = seedUser("Director", false);

        mvc.perform(
                        get(ANY_LISTING + "?offset=0")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sortDirectionThatIsNeitherAscNorDesc_returns400_not500() throws Exception {
        UUID director = seedUser("Director", false);

        mvc.perform(
                        get(ANY_LISTING + "?sort=sideways")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isBadRequest());
    }

    // The body says what the caller has to change. A 400 with no shape is a wall.
    @Test
    void namesTheParameterItRefused() throws Exception {
        UUID director = seedUser("Director", false);

        mvc.perform(
                        get(ANY_LISTING + "?limit=500")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("limit"));
    }
}
