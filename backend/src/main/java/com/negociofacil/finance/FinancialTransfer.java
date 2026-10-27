package com.negociofacil.finance;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "financial_transfers")
public class FinancialTransfer extends BaseEntity {
  public Long companyId;
  public Long sourceAccountId;
  public Long targetAccountId;
  @Column(precision = 16, scale = 2) public java.math.BigDecimal amount;
  public java.time.LocalDate transferDate;
  public String description;
  public java.util.UUID requestId;
  public String requestHash;
  public java.time.Instant canceledAt;
  @Version public long version;
}
