package com.negociofacil.fiscal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

public final class FiscalDtos {
  private FiscalDtos() {}

  public enum Origin { IMPORTACAO_MANUAL }
  public enum Order { EMISSAO_RECENTE, EMISSAO_ANTIGA, MAIOR_VALOR }

  public record Filter(String search, @DateTimeFormat(iso = DATE) LocalDate from,
                       @DateTimeFormat(iso = DATE) LocalDate to, @DateTimeFormat(iso = DATE) LocalDate competenceFrom,
                       @DateTimeFormat(iso = DATE) LocalDate competenceTo, Long customerId, FiscalXml.Status status, Origin origin) {}

  public record Row(long id, String number, String accessKey, LocalDate issuedOn, LocalDate competence,
                    String customerName, String recipientName, String description, BigDecimal gross,
                    BigDecimal net, FiscalXml.Status status, Origin origin) {}

  public record Links(Long customerId, Long quotationId, Long quotationItemId, Long obligationId) {}

  public record Detail(Row note, Links links, String providerDocument, String providerName,
                       String recipientDocument, String municipality, String serviceCode,
                       Map<String, BigDecimal> amounts, Instant importedAt, Instant updatedAt,
                       long importedBy, String xmlHash, String type, String processing) {}

  public record Summary(long count, BigDecimal gross, long valid, long canceled, long unverified, long replaced) {}

  public record Option(long id, String label) {}
}
