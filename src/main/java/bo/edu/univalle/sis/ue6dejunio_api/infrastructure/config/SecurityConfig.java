package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.config;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security.JwtAuthConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
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
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/health"
    };

    @Bean
    public SecurityFilterChain filterChain(
        HttpSecurity http,
        JwtAuthConverter jwtAuthConverter,
        @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource
    ) throws Exception {
        http
            .cors(c -> c.configurationSource(corsSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers("/api/users/**").hasRole("Director")
                .requestMatchers("/api/courses/**").hasRole("Director")
                .requestMatchers("/api/levels/**").hasRole("Director")
                .requestMatchers("/api/grades/**").hasRole("Director")
                .requestMatchers("/api/subjects/**").hasRole("Director")
                .requestMatchers("/api/parallels/**").hasRole("Director")
                .requestMatchers("/api/catalog/**").authenticated()
                .requestMatchers("/api/notifications/**").authenticated()
                .requestMatchers("/api/course-enrollments/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/students/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/criteria/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/scores/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/assessment-events/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/assessment-scores/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/adaptations/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/attendance/**").hasAnyRole("Director", "Teacher")
                .requestMatchers("/api/teachers/**").hasAnyRole("Director", "Teacher")
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
}
