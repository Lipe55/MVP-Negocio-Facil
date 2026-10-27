package com.negociofacil.finance;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "financial_movements")
public class BalanceMovement extends BaseEntity {
  public Long companyId;
  public Long accountId;
  public Long settlementId;
  public Long transferId;
  public Long reversalOfId;
  @Enumerated(EnumType.STRING) public FinanceTypes.MovementKind kind;
  @Column(precision = 16, scale = 2) public java.math.BigDecimal amount;
  public java.time.LocalDate effectiveDate;
  public String description;
}
