package com.negociofacil.finance.obligations;

import com.negociofacil.common.BaseEntity;
import com.negociofacil.finance.FinanceTypes;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name="financial_recurrences")
public class FinancialRecurrence extends BaseEntity {
  public Long companyId;
  @Enumerated(EnumType.STRING) public FinanceTypes.Kind kind=FinanceTypes.Kind.DESPESA;
  public String description;
  @Column(precision=16,scale=2) public BigDecimal amount;
  public Long accountId;
  public Long categoryId;
  public LocalDate firstDueDate;
  public int occurrences;
  public boolean active=true;
  public String notes;
  public UUID requestId;
  public String requestHash;
  @Version public long version;
}
