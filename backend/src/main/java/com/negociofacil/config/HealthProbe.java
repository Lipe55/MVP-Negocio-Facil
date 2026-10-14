package com.negociofacil.config;

/** Container health check using only the JRE, without extra OS packages. */
public final class HealthProbe {
  public static void main(String[] args) throws Exception {
    var request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:8080/api/health"))
            .timeout(java.time.Duration.ofSeconds(2))
            .GET()
            .build();
    var result =
        java.net.http.HttpClient.newHttpClient()
            .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    System.exit(result.statusCode() == 200 && result.body().contains("UP") ? 0 : 1);
  }
}
