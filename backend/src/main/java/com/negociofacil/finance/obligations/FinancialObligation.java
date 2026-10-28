package com.negociofacil.finance.obligations;

import com.negociofacil.common.BaseEntity;
import com.negociofacil.finance.FinanceTypes;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name="financial_obligations")
public class FinancialObligation extends BaseEntity {
  public Long companyId;
  @Enumerated(EnumType.STRING) public FinanceTypes.Kind kind;
  public String description;
  @Column(precision=16,scale=2) public BigDecimal totalAmount;
  public int installmentCount;
  public String source;
  public Long recurrenceId;
  public Integer occurrenceNumber;
  public UUID requestId;
  public String requestHash;
  @Version public long version;
}
