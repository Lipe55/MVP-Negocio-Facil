package com.negociofacil.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-instance limiter. Proxy must preserve remote addresses; scale deployments at the edge. */
public class RateLimitFilter extends OncePerRequestFilter {
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  private static final class Bucket {
    long start = System.currentTimeMillis();
    int count;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    boolean auth = path.equals("/api/auth/login") || path.equals("/api/auth/register");
    boolean publicRoute = path.startsWith("/api/public/");
    if (!auth && !publicRoute) {
      chain.doFilter(request, response);
      return;
    }
    long now = System.currentTimeMillis();
    if (buckets.size() > 10000) buckets.entrySet().removeIf(e -> now - e.getValue().start > 60000);
    String key = request.getRemoteAddr() + ":" + (auth ? "auth" : "public");
    if (buckets.size() > 10000 && !buckets.containsKey(key)) {
      reject(response);
      return;
    }
    Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
    synchronized (bucket) {
      if (now - bucket.start >= 60000) {
        bucket.start = now;
        bucket.count = 0;
      }
      if (++bucket.count > (auth ? 15 : 90)) {
        reject(response);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private void reject(HttpServletResponse r) throws IOException {
    r.setStatus(429);
    r.setHeader("Retry-After", "60");
    r.setContentType("application/json;charset=UTF-8");
    r.getWriter()
        .write(
            "{\"status\":429,\"message\":\"Muitas requisições. Aguarde um"
                + " minuto.\",\"fields\":{}}");
  }
}
