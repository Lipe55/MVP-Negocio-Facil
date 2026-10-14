package com.negociofacil.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CredentialVersionFilterTest {
  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void allowsCurrentCredentials() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForList(anyString(), eq(Long.class), eq(1L), eq(2L)))
        .thenReturn(List.of(3L));
    authenticate(3);
    var chain = new MockFilterChain();
    var response = new MockHttpServletResponse();
    new CredentialVersionFilter(jdbc).doFilter(
        new MockHttpServletRequest("GET", "/api/auth/me"), response, chain);
    assertThat(chain.getRequest()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void rejectsLateSavedSessionAndInvalidatesIt() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForList(anyString(), eq(Long.class), eq(1L), eq(2L)))
        .thenReturn(List.of(4L));
    authenticate(3);
    var request = new MockHttpServletRequest("GET", "/api/auth/me");
    var session = new MockHttpSession();
    request.setSession(session);
    var response = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    new CredentialVersionFilter(jdbc).doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(session.isInvalid()).isTrue();
    assertThat(chain.getRequest()).isNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void deletedUserCannotUseSession() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForList(anyString(), eq(Long.class), eq(1L), eq(2L)))
        .thenReturn(List.of());
    authenticate(1);
    var response = new MockHttpServletResponse();
    new CredentialVersionFilter(jdbc).doFilter(
        new MockHttpServletRequest("GET", "/api/customers"), response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void anonymousPublicRequestsDoNotQueryCredentials() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    var chain = new MockFilterChain();
    new CredentialVersionFilter(jdbc).doFilter(
        new MockHttpServletRequest("GET", "/api/public/quotations/test"),
        new MockHttpServletResponse(), chain);
    verifyNoInteractions(jdbc);
    assertThat(chain.getRequest()).isNotNull();
  }

  private void authenticate(long version) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            new SessionUser(1L, 2L, "test@example.com", version), null, List.of()));
  }
}
