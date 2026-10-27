package com.negociofacil.finance;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "financial_entries")
public class FinancialEntry extends BaseEntity {
  public Long companyId;
  public Long accountId;
  public Long categoryId;
  @Enumerated(EnumType.STRING) public FinanceTypes.Kind kind;
  @Enumerated(EnumType.STRING) public FinanceTypes.Status status;
  public String description;
  public String notes;
  @Column(precision = 16, scale = 2) public java.math.BigDecimal amount;
  public java.time.LocalDate entryDate;
  public java.util.UUID requestId;
  public String requestHash;
  @Version public long version;
}
