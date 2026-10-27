package com.negociofacil.finance;

import com.negociofacil.common.PageResult;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.negociofacil.finance.FinanceTypes.*;

public final class FinanceDtos {
  public record AccountInput(@NotBlank @Size(max=160) String name, @NotNull AccountType type,
      @NotNull @Digits(integer=14, fraction=2) BigDecimal openingBalance,
      @NotNull LocalDate openingDate, @NotNull Boolean active, Long version) {}
  public record AccountView(Long id, String name, AccountType type, boolean primaryAccount,
      boolean active, BigDecimal openingBalance, LocalDate openingDate, BigDecimal balance, long version) {}
  public record CategoryInput(@NotBlank @Size(max=100) String name, @NotNull Kind kind) {}
  public record CategoryView(Long id, String name, Kind kind) {}
  public record EntryInput(@NotNull UUID requestId, @NotNull Kind kind, Long accountId, Long categoryId,
      @NotBlank @Size(max=500) String description,
      @NotNull @DecimalMin(value="0", inclusive=false) @Digits(integer=14, fraction=2) BigDecimal amount,
      @NotNull Boolean settled, LocalDate entryDate, LocalDate settlementDate,
      @Size(max=3000) String notes, Long version) {}
  public record EntryView(Long id, Kind kind, Status status, Long accountId, String accountName,
      Long categoryId, String categoryName, String description, BigDecimal amount,
      LocalDate entryDate, LocalDate settlementDate, String notes, long version, Instant createdAt) {}
  public record SettlementInput(@NotNull UUID requestId, @NotNull @PositiveOrZero Long version,
      @NotNull LocalDate settlementDate) {}
  public record VersionInput(@NotNull @PositiveOrZero Long version) {}
  public record TransferInput(@NotNull UUID requestId, @NotNull Long sourceAccountId,
      @NotNull Long targetAccountId,
      @NotNull @DecimalMin(value="0", inclusive=false) @Digits(integer=14, fraction=2) BigDecimal amount,
      @NotNull LocalDate transferDate, @Size(max=500) String description) {}
  public record TransferView(Long id, Long sourceAccountId, Long targetAccountId, BigDecimal amount,
      LocalDate transferDate, String description, Instant canceledAt, long version) {}
  public record MovementView(Long id, LocalDate effectiveDate, MovementKind kind,
      String description, BigDecimal amount, BigDecimal runningBalance, Long entryId,
      Long transferId, Long transferVersion, Long reversalOfId, boolean reversed, Instant createdAt) {}
  public record Statement(AccountView account, BigDecimal balanceBeforePeriod,
      BigDecimal periodChange, BigDecimal balanceAtEnd, PageResult<MovementView> movements) {}
  public record AuditView(Long id, Long actorId, String action, String beforeData,
      String afterData, Instant createdAt) {}
}
