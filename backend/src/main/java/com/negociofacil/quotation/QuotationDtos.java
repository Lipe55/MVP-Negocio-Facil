package com.negociofacil.quotation;

import com.negociofacil.customer.CustomerDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class QuotationDtos {
  public record ItemInput(
      @NotBlank @Size(max = 500) String description,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3)
          BigDecimal quantity,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice) {}

  public record Input(
      @NotNull Long customerId,
      @NotNull LocalDate validUntil,
      @Size(max = 3000) String notes,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal discount,
      @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid ItemInput> items,
      @Size(max = 3000) String terms) {
    public Input(Long customerId, LocalDate validUntil, String notes,
        BigDecimal discount, List<ItemInput> items) {
      this(customerId, validUntil, notes, discount, items, null);
    }
  }

  public record ItemView(
      Long id, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {}

  public record View(
      Long id,
      long number,
      QuotationStatus status,
      CustomerDtos.View customer,
      LocalDate validUntil,
      String notes,
      BigDecimal subtotal,
      BigDecimal discount,
      BigDecimal total,
      List<ItemView> items,
      String publicToken,
      Instant sentAt,
      Instant respondedAt,
      Instant createdAt,
      Instant updatedAt,
      String companyName,
      String customerName,
      String terms,
      Instant publicRevokedAt) {}

  public record PublicItemView(
      String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {}

  public record PublicView(
      String companyName,
      long number,
      String customerName,
      QuotationStatus status,
      LocalDate validUntil,
      BigDecimal subtotal,
      BigDecimal discount,
      BigDecimal total,
      List<PublicItemView> items,
      Instant respondedAt,
      String terms) {}
}
