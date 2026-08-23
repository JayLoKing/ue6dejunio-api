package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.config;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit.ClientIpResolver;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit.RateLimitProperties;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit.RateLimitStore;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit.RateLimitingFilter;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security.JwtAuthConverter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/api/auth/login",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/health"
    };

    @Bean
    public SecurityFilterChain filterChain(
        HttpSecurity http,
        JwtAuthConverter jwtAuthConverter,
        RateLimitingFilter rateLimitingFilter,
        @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource
    ) throws Exception {
        http
            .cors(c -> c.configurationSource(corsSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers("/api/users/**").hasRole("Director")
                // The secretariat is a school-wide READ actor. Every rule that includes it below is
                // GET-only and precedes the broader rule for the same path, so Secretary never
                // matches a write rule. These rules only decide which roles reach the handler;
                // row-level scope is enforced by @PreAuthorize on each read.
                .requestMatchers(HttpMethod.GET, "/api/courses/*/attendance-stats")
                    .hasAnyRole("Director", "Teacher", "Secretary")
                .requestMatchers(HttpMethod.GET, "/api/courses/*/overview")
                    .hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/courses/**").hasRole("Director")
                .requestMatchers("/api/levels/**").hasRole("Director")
                .requestMatchers("/api/grades/**").hasRole("Director")
                .requestMatchers("/api/subjects/**").hasRole("Director")
                .requestMatchers("/api/parallels/**").hasRole("Director")
                .requestMatchers("/api/trimester-periods/**").hasRole("Director")
                .requestMatchers("/api/catalog/**").authenticated()
                .requestMatchers("/api/notifications/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/course-enrollments/**")
                    .hasAnyRole("Director", "Teacher", "Secretary")
                .requestMatchers("/api/course-enrollments/**").hasAnyRole("Director", "Teacher")
                .requestMatchers(HttpMethod.GET, "/api/students/**")
                    .hasAnyRole("Director", "Teacher", "Secretary")
                .requestMatchers("/api/students/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/criteria/**").hasAnyRole("Director", "Teacher")
                .requestMatchers(HttpMethod.GET, "/api/scores/**")
                    .hasAnyRole("Director", "Teacher", "Secretary")
                .requestMatchers("/api/scores/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/assessment-events/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/assessment-scores/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/adaptations/**").hasAnyRole("Director", "Teacher")
                .requestMatchers(HttpMethod.GET, "/api/attendance/**")
                    .hasAnyRole("Director", "Teacher", "Secretary")
                .requestMatchers("/api/attendance/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/teachers/**").hasAnyRole("Director", "Teacher")
                .requestMatchers(HttpMethod.GET, "/api/gradebook/**")
                    .hasAnyRole("Director", "Teacher", "Secretary")
                .requestMatchers("/api/gradebook/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/pdc/**").hasAnyRole("Director", "Teacher")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthConverter)));
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public RateLimitingFilter rateLimitingFilter(
        RateLimitProperties properties, ClientIpResolver ipResolver, RateLimitStore store
    ) {
        return new RateLimitingFilter(properties, ipResolver, store);
    }

    /**
     * {@link RateLimitingFilter} is already wired into the security chain via
     * {@link #filterChain}. Disabling its servlet-container auto-registration prevents Spring
     * Boot from also registering it as a plain servlet {@code Filter} bean, which would otherwise
     * run it a second time per request.
     */
    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(
        RateLimitingFilter rateLimitingFilter
    ) {
        FilterRegistrationBean<RateLimitingFilter> registration =
            new FilterRegistrationBean<>(rateLimitingFilter);
        registration.setEnabled(false);
        return registration;
    }
}
