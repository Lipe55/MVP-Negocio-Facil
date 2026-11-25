package com.negociofacil.fiscal;

import static com.negociofacil.fiscal.FiscalFixture.*;
import static org.assertj.core.api.Assertions.*;

import com.negociofacil.common.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class DanfseClientTest {
  private HttpServer server;
  private String url;
  private static final byte[] PDF = bytes("%PDF-1.4\n%%EOF");
  private final FiscalRepository.Document note = new FiscalRepository.Document(complete(1), FiscalXml.Status.CANCELADA);

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    url = "http://127.0.0.1:" + server.getAddress().getPort();
    server.start();
  }

  @AfterEach void stop() { server.stop(0); }

  @Test
  void sendsOriginalXmlAndGeneratorOptions() {
    var input = new AtomicReference<byte[]>();
    var request = new AtomicReference<String>();
    server.createContext("/api/danfse", exchange -> {
      try (exchange) {
        input.set(exchange.getRequestBody().readAllBytes());
        request.set(exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst("Content-Type")
            + " " + exchange.getRequestURI().getQuery());
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.sendResponseHeaders(200, PDF.length);
        exchange.getResponseBody().write(PDF);
      }
    });
    assertThat(client().generate(note, false)).isEqualTo(PDF);
    assertThat(input.get()).isEqualTo(note.xml());
    assertThat(request.get()).isEqualTo("POST application/xml canhoto=false&stc=2");
  }

  @Test void handlesUnavailableGeneratorWithoutLeakingResponse() {
    response(503, "text/plain", bytes("private internal info"));
    expect(503);
    assertThat(client().ready()).isFalse();
  }

  @Test void rejectsWrongMediaType() {
    response(200, "text/html", PDF);
    expect(502);
  }

  @Test void rejectsNonPdfBody() {
    response(200, "application/pdf", bytes("unexpected"));
    expect(502);
  }

  @Test void capsResponseBytes() {
    response(200, "application/pdf", new byte[8 * 1024 * 1024 + 1]);
    expect(502);
  }

  @Test void timeoutIncludesBodyDownload() {
    server.createContext("/api/danfse", exchange -> {
      try (exchange) {
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.sendResponseHeaders(200, PDF.length);
        exchange.getResponseBody().write(PDF[0]);
        exchange.getResponseBody().flush();
        try { Thread.sleep(700); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      }
    });
    assertThatExceptionOfType(ApiException.class)
        .isThrownBy(() -> new DanfseClient(url, Duration.ofMillis(250)).generate(note, true))
        .satisfies(error -> assertThat(error.status).isEqualTo(504));
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "TEST_DANFSE_URL", matches = ".+")
  void rendersSyntheticInvoiceWithOfficialGenerator() {
    var official = new DanfseClient(System.getenv("TEST_DANFSE_URL"), Duration.ofSeconds(8));
    assertThat(official.ready()).isTrue();
    assertThat(official.generate(note, true).length).isGreaterThan(1000);
  }

  private void response(int status, String contentType, byte[] body) {
    server.createContext("/api/danfse", exchange -> {
      try (exchange) {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
      }
    });
  }

  private DanfseClient client() { return new DanfseClient(url, Duration.ofSeconds(5)); }

  private void expect(int status) {
    assertThatExceptionOfType(ApiException.class).isThrownBy(() -> client().generate(note, true))
        .satisfies(error -> assertThat(error.status).isEqualTo(status));
  }
}
