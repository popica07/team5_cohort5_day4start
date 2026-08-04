package com.dbtraining.reconx.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * ============================================================================
 * SecurityConfig — TICKET-ADV073 + TICKET-ADV074
 * ============================================================================
 * WHAT:    Spring Security filter chain. Production target: stateless JWT
 *          auth + method-level RBAC across ADMIN / TRADER / VIEWER /
 *          RECON_ANALYST roles.
 * HOW:     One SecurityFilterChain @Bean + PasswordEncoder @Bean +
 *          @EnableMethodSecurity. The JwtAuthenticationFilter is registered
 *          before UsernamePasswordAuthenticationFilter.
 * WHY:     Day 6 needs role-based protection on every endpoint, and the
 *          frontend uses bearer tokens issued at /auth/login.
 * OBSERVE: After Day-6 work is wired, GET /api/v1/trades without a token -> 401.
 * ============================================================================
 *
 *  DAY-1 DEFAULT (below): everything is `permitAll`. This lets the frontend
 *  and Swagger UI load on Day 1 without an auth UI. TICKET-ADV073 + ADV074
 *  replace this with proper JWT + role-based auth.
 *
 *  TODO(TICKET-ADV073 + ADV074):
 *    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 *
 *    @Bean
 *    public SecurityFilterChain filterChain(HttpSecurity http,
 *                                           JwtAuthenticationFilter jwtFilter) throws Exception {
 *        http
 *          .csrf(AbstractHttpConfigurer::disable)
 *          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
 *          .authorizeHttpRequests(auth -> auth
 *            .requestMatchers("/auth/login","/actuator/health/**","/actuator/info",
 *                             "/actuator/prometheus","/swagger-ui.html","/swagger-ui/**",
 *                             "/v3/api-docs/**","/h2/**").permitAll()
 *            .requestMatchers(HttpMethod.GET,    "/v1/trades/**").hasAnyRole("VIEWER","TRADER","RECON_ANALYST","ADMIN")
 *            .requestMatchers(HttpMethod.POST,   "/v1/trades").hasAnyRole("TRADER","ADMIN")
 *            .requestMatchers(HttpMethod.PUT,    "/v1/trades/**").hasAnyRole("TRADER","ADMIN")
 *            .requestMatchers(HttpMethod.PATCH,  "/v1/trades/**").hasAnyRole("TRADER","ADMIN")
 *            .requestMatchers(HttpMethod.DELETE, "/v1/trades/**").hasRole("ADMIN")
 *            .requestMatchers("/v1/recon/**").hasAnyRole("RECON_ANALYST","ADMIN")
 *            .requestMatchers("/v1/audit/**").hasAnyRole("RECON_ANALYST","ADMIN")
 *            .anyRequest().authenticated())
 *          .headers(h -> h.frameOptions(f -> f.disable()))
 *          .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
 *        return http.build();
 *    }
 *
 *  HINT: Also add @EnableMethodSecurity on the class so @PreAuthorize on
 *        service methods is honoured.
 * ============================================================================
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/login",
                                "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus",
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/h2/**").permitAll()
                        // EventSource can't set an Authorization header, so the SSE
                        // stream is intentionally unauthenticated (TICKET-ADV104).
                        .requestMatchers(HttpMethod.GET,    "/v1/trades/stream").permitAll()
                        .requestMatchers(HttpMethod.GET,    "/v1/trades/**").hasAnyRole("VIEWER", "TRADER", "RECON_ANALYST", "ADMIN")
                        .requestMatchers(HttpMethod.POST,   "/v1/trades").hasAnyRole("TRADER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/v1/trades/**").hasAnyRole("TRADER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH,  "/v1/trades/**").hasAnyRole("TRADER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/v1/trades/**").hasRole("ADMIN")
                        .requestMatchers("/v1/recon/**").hasAnyRole("RECON_ANALYST", "ADMIN")
                        .requestMatchers("/v1/audit/**").hasAnyRole("RECON_ANALYST", "ADMIN")
                        .anyRequest().authenticated())
                // Without an explicit entry point, a chain that declares no
                // httpBasic/formLogin falls back to Http403ForbiddenEntryPoint,
                // so unauthenticated callers get 403 where the API contract
                // says 401 — clients then cannot tell "log in" from "not
                // allowed". Covered by TICKET-ADV076.
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(h -> h.frameOptions(f -> f.disable()))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Dev-only CORS: the static dashboard is served by `py -m http.server 5500`
     * while this API lives on :8081, so the browser treats every call as
     * cross-origin. Scoped to the local static-server origins on purpose —
     * widen this deliberately, never to "*", and note that allowCredentials
     * stays off (EventSource here sends no cookies).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:5500", "http://127.0.0.1:5500"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(false);
        cfg.setAllowedOrigins(List.of("http://localhost:5500", "http://127.0.0.1:5500", "http://localhost:5173"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
