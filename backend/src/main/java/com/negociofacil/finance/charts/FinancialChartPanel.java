package com.negociofacil.finance.charts;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity @Table(name="financial_chart_panels")
public class FinancialChartPanel extends BaseEntity {
  public Long companyId;
  public Long ownerId;
  @Version public long version;
}
