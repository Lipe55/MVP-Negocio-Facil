package com.negociofacil.finance;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "financial_categories")
public class FinancialCategory extends BaseEntity {
  public Long companyId;
  public String name;
  @Enumerated(EnumType.STRING) public FinanceTypes.Kind kind;
}
