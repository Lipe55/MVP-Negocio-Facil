package com.negociofacil.fiscal;

import com.negociofacil.common.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class DanfseClient {
  private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
  private static final int MAX_PDF_BYTES = 8 * 1024 * 1024;
  private final HttpClient http;
  private final URI base;
  private final Duration timeout;

  DanfseClient(@Value("${fiscal.danfse.url}") String url,
               @Value("${fiscal.danfse.timeout}") Duration timeout) {
    this.base = URI.create(url);
    this.timeout = timeout;
    this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  boolean ready() {
    try {
      return send(HttpRequest.newBuilder(base.resolve("/health/ready")).GET()).statusCode() == 200;
    } catch (ApiException e) {
      return false;
    }
  }

  byte[] generate(FiscalRepository.Document document, boolean receipt) {
    Integer status = document.status().generatorCode;
    var uri = base.resolve("/api/danfse?canhoto=" + receipt + (status == null ? "" : "&stc=" + status));
    var result = send(HttpRequest.newBuilder(uri).header("Content-Type", "application/xml")
        .header("Accept", "application/pdf").POST(HttpRequest.BodyPublishers.ofByteArray(document.xml())));
    if (result.statusCode() == 400) throw new ApiException(422, "O gerador não conseguiu renderizar este XML.");
    if (result.statusCode() >= 500) throw unavailable();
    String type = result.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip();
    byte[] body = result.body();
    if (result.statusCode() != 200 || !"application/pdf".equalsIgnoreCase(type)
        || !Arrays.equals(PDF_MAGIC, Arrays.copyOf(body, PDF_MAGIC.length))) {
      throw new ApiException(502, "O gerador retornou um PDF inválido.");
    }
    return body;
  }

  private HttpResponse<byte[]> send(HttpRequest.Builder builder) {
    var pending = http.sendAsync(builder.timeout(timeout).build(), response -> new LimitedBody());
    try {
      return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      pending.cancel(true);
      throw new ApiException(504, "O gerador de PDF demorou demais. Tente novamente.");
    } catch (InterruptedException e) {
      pending.cancel(true);
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof java.net.http.HttpTimeoutException) {
        throw new ApiException(504, "O gerador de PDF demorou demais. Tente novamente.");
      }
      if (e.getCause() instanceof OversizedPdf) {
        throw new ApiException(502, "O arquivo retornado pelo gerador excede o limite.");
      }
      throw unavailable();
    }
  }

  private static ApiException unavailable() {
    return new ApiException(503, "O gerador de PDF está indisponível. Tente novamente mais tarde.");
  }

  private static final class OversizedPdf extends IOException {}

  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    private Flow.Subscription subscription;
    private long size;

    @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }

    @Override public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      delegate.onSubscribe(subscription);
    }

    @Override public void onNext(List<ByteBuffer> buffers) {
      for (var buffer : buffers) size += buffer.remaining();
      if (size > MAX_PDF_BYTES) {
        subscription.cancel();
        delegate.onError(new OversizedPdf());
        return;
      }
      delegate.onNext(buffers);
    }

    @Override public void onError(Throwable error) { delegate.onError(error); }

    @Override public void onComplete() { delegate.onComplete(); }
  }
}
