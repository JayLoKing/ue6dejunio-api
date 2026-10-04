package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * The password-reset mail links to the web client. The fallback used when FRONTEND_RESET_URL is
 * unset must hit the port the Vite dev server listens on (5300) and the route the web actually
 * defines ({@code /auth/reset-password}); otherwise the link in the mail lands on a dead page.
 */
class FrontendResetUrlDefaultTest {

    @Test
    void resetUrlFallback_pointsAtTheWebResetRoute() throws IOException {
        Properties props = new Properties();
        try (InputStream in =
                getClass().getClassLoader().getResourceAsStream("application.properties")) {
            props.load(in);
        }

        String value = props.getProperty("app.frontend.reset-url");

        assertThat(value)
                .isEqualTo("${FRONTEND_RESET_URL:http://localhost:5300/auth/reset-password}");
    }
}
