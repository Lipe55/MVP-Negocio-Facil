package com.negociofacil.finance;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "financial_settlements")
public class FinancialSettlement extends BaseEntity {
  public Long companyId;
  public Long entryId;
  public Long accountId;
  @Column(precision = 16, scale = 2) public java.math.BigDecimal amount;
  public java.time.LocalDate settlementDate;
  public java.time.Instant reversedAt;
  public java.util.UUID requestId;
  public String requestHash;
}
