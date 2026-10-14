package com.negociofacil.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects stale credentials even when a concurrent login saves its session after revocation. */
public class CredentialVersionFilter extends OncePerRequestFilter {
  private final JdbcTemplate jdbc;

  public CredentialVersionFilter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof SessionUser user) {
      var versions = jdbc.queryForList(
          "select credential_version from users where id=? and company_id=?", Long.class,
          user.userId(), user.companyId());
      if (versions.isEmpty() || versions.get(0) != user.credentialVersion()) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            "{\"status\":401,\"message\":\"Entre novamente para continuar.\",\"fields\":{}}");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
