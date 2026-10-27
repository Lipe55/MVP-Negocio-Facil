package com.negociofacil.finance;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "financial_accounts")
public class FinancialAccount extends BaseEntity {
  public Long companyId;
  public String name;
  @Enumerated(EnumType.STRING) public FinanceTypes.AccountType type;
  public boolean primaryAccount;
  public boolean active = true;
  @Column(precision = 16, scale = 2) public java.math.BigDecimal openingBalance = java.math.BigDecimal.ZERO;
  public java.time.LocalDate openingDate;
  @Version public long version;
}
