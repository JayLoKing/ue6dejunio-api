package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Externalized configuration for the login/global/change-password rate limiters. */
@Component
@ConfigurationProperties("app.security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private String ipResolver = "direct";
    private List<String> trustedProxies = new ArrayList<>();
    private final Bucket login = new Bucket(5, 5);
    private final Bucket global = new Bucket(100, 100);
    private final Bucket changePassword = new Bucket(5, 5);
    private final Bucket forgotPassword = new Bucket(5, 5);
    private final Bucket resetPassword = new Bucket(5, 5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIpResolver() {
        return ipResolver;
    }

    public void setIpResolver(String ipResolver) {
        this.ipResolver = ipResolver;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    public Bucket getLogin() {
        return login;
    }

    public Bucket getGlobal() {
        return global;
    }

    public Bucket getChangePassword() {
        return changePassword;
    }

    public Bucket getForgotPassword() {
        return forgotPassword;
    }

    public Bucket getResetPassword() {
        return resetPassword;
    }

    /** A single limiter's tunable capacity/refill parameters. */
    public static class Bucket {
        private int capacity;
        private int refillPerMinute;

        public Bucket() {}

        public Bucket(int capacity, int refillPerMinute) {
            this.capacity = capacity;
            this.refillPerMinute = refillPerMinute;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getRefillPerMinute() {
            return refillPerMinute;
        }

        public void setRefillPerMinute(int refillPerMinute) {
            this.refillPerMinute = refillPerMinute;
        }
    }
}
