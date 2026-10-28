package com.negociofacil.finance.charts;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity @Table(name="financial_charts")
public class FinancialChart extends BaseEntity {
  public Long companyId;
  public Long ownerId;
  public Long panelId;
  @Column(columnDefinition="text") public String configData;
  public int position;
  @Version public long version;
}
