package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec: Production hardening (P2) — swagger disabled, error bodies omit message/binding detail when
 * the `prod` profile is layered on top of the `it` datasource profile.
 */
@ActiveProfiles({"it", "prod"})
class ProdProfileHardeningIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    @Test
    void swaggerApiDocs_unavailableInProd() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(
                        r -> {
                            int sc = r.getResponse().getStatus();
                            org.assertj.core.api.Assertions.assertThat(sc).isIn(404, 503);
                        });
    }

    @Test
    void swaggerUi_unavailableInProd() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(
                        r -> {
                            int sc = r.getResponse().getStatus();
                            org.assertj.core.api.Assertions.assertThat(sc).isIn(404, 503);
                        });
    }

    @Test
    void validationError_omitsMessageAndBindingDetails_inProd() throws Exception {
        String body = json.writeValueAsString(new LoginBody("not-an-email", "secret123"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    private record LoginBody(String email, String password) {}
}
