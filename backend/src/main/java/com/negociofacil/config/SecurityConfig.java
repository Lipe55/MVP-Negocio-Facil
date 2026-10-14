package com.negociofacil.config;

import com.negociofacil.security.RateLimitFilter;
import com.negociofacil.security.CredentialVersionFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  HttpSessionSecurityContextRepository contextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      @Value("${app.allowed-origin}") String origin,
      @Value("${server.servlet.session.cookie.secure}") boolean secure,
      HttpSessionSecurityContextRepository repository,
      JdbcTemplate jdbc)
      throws Exception {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(origin));
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
    cors.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrf.setCookieCustomizer(c -> c.sameSite("Lax").path("/").secure(secure));
    http.cors(c -> c.configurationSource(source))
        .csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .securityContext(c -> c.securityContextRepository(repository).requireExplicitSave(true))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/auth/csrf",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/public/**",
                        "/api/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter()
                              .write(
                                  "{\"status\":401,\"message\":\"Entre para"
                                      + " continuar.\",\"fields\":{}}");
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json;charset=UTF-8");
                          if (ex instanceof InvalidCsrfTokenException
                              || ex instanceof MissingCsrfTokenException) {
                            res.getWriter().write(
                                "{\"status\":403,\"code\":\"CSRF_INVALID\","
                                    + "\"message\":\"Atualize a proteção da requisição.\",\"fields\":{}}");
                            return;
                          }
                          res.getWriter()
                              .write(
                                  "{\"status\":403,\"message\":\"Requisição não autorizada."
                                      + " Atualize a página.\",\"fields\":{}}");
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/auth/logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("SESSION", "XSRF-TOKEN")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .addFilterBefore(new RateLimitFilter(), CsrfFilter.class)
        .addFilterBefore(new CredentialVersionFilter(jdbc), AuthorizationFilter.class);
    return http.build();
  }
}
