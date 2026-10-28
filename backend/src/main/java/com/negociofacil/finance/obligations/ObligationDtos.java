package com.negociofacil.finance.obligations;

import com.negociofacil.finance.FinanceTypes.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class ObligationDtos {
  private ObligationDtos() {}
  public record CreateInput(@NotNull UUID requestId,@NotNull Kind kind,
      @NotBlank @Size(max=500) String description,
      @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=2) BigDecimal amount,
      @NotNull Long categoryId,Long accountId,LocalDate entryDate,@NotNull LocalDate dueDate,
      @NotNull @Min(1) @Max(120) Integer installments,@Size(max=3000) String notes,
      Boolean settled,LocalDate settlementDate) {}
  public record InstallmentView(Long id,Long obligationId,Kind kind,String description,BigDecimal amount,
      BigDecimal paidAmount,BigDecimal remainingAmount,LocalDate dueDate,LocalDate entryDate,Long accountId,String accountName,
      Long categoryId,String categoryName,PaymentStatus paymentStatus,boolean overdue,Status status,long version,
      Integer installmentNumber,Integer installmentCount,Long recurrenceId,Integer occurrenceNumber,String notes,boolean hasSettlementHistory) {}
  public record Detail(Long id,Kind kind,String description,BigDecimal totalAmount,int installmentCount,
      Long recurrenceId,Integer occurrenceNumber,long version,List<InstallmentView> installments) {}
  public record EditInput(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=500) String description,
      @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=2) BigDecimal amount,
      @NotNull Long accountId,@NotNull Long categoryId,@NotNull LocalDate entryDate,LocalDate dueDate,
      @Size(max=3000) String notes) {}
  public record RecurrenceInput(@NotNull UUID requestId,@NotBlank @Size(max=500) String description,
      @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=2) BigDecimal amount,
      @NotNull Long categoryId,Long accountId,@NotNull LocalDate firstDueDate,
      @NotNull @Min(1) @Max(60) Integer occurrences,@Size(max=3000) String notes) {}
  public record RecurrenceView(Long id,String description,BigDecimal amount,Long categoryId,Long accountId,
      LocalDate firstDueDate,int occurrences,boolean active,long version) {}
  public record FutureInput(@NotNull @PositiveOrZero Long version,@NotNull Long fromObligationId,
      @NotBlank @Size(max=500) String description,
      @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=14,fraction=2) BigDecimal amount,
      @NotNull Long categoryId,@NotNull Long accountId,@NotNull @Min(1) @Max(31) Integer dueDay,
      @Size(max=3000) String notes) {}
  public record CancelFutureInput(@NotNull @PositiveOrZero Long version,@NotNull Long fromObligationId) {}
  public record FutureResult(int updatedCount,int skippedCount) {}
}
