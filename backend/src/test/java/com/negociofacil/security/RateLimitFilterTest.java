package com.negociofacil.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class RateLimitFilterTest {
  @Test
  void rejectsSensitiveRequestsAfterLimitAndSetsRetryAfter() throws Exception {
    var filter = new RateLimitFilter();
    for (int i = 0; i < 15; i++) {
      var response = new MockHttpServletResponse();
      filter.doFilter(
          new MockHttpServletRequest("POST", "/api/auth/login"), response, new MockFilterChain());
      assertThat(response.getStatus()).isEqualTo(200);
    }
    var response = new MockHttpServletResponse();
    filter.doFilter(
        new MockHttpServletRequest("POST", "/api/auth/login"), response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    var unrelated = new MockHttpServletResponse();
    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/customers"), unrelated, new MockFilterChain());
    assertThat(unrelated.getStatus()).isEqualTo(200);
  }
}
