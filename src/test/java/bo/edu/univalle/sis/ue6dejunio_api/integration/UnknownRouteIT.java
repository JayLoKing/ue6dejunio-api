package bo.edu.univalle.sis.ue6dejunio_api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec: the catch-all exception handler must not turn Spring's own web exceptions into 500.
 * A path with no handler is a client error, not an outage — before this was pinned down, every
 * unmapped URL answered 500 and hid real server failures in the same bucket.
 */
class UnknownRouteIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    @Test
    void unknownPath_returns404_not500() throws Exception {
        UUID director = seedUser("Director", false);

        mvc.perform(get("/api/does-not-exist")
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isNotFound());
    }

    @Test
    void unsupportedMethodOnKnownPath_returns405_not500() throws Exception {
        UUID director = seedUser("Director", false);

        mvc.perform(patch("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isMethodNotAllowed());
    }
}
