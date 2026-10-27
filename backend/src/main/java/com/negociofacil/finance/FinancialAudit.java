package com.negociofacil.finance;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "financial_audit")
public class FinancialAudit extends BaseEntity {
  public Long companyId;
  public Long actorId;
  public String resource;
  public Long resourceId;
  public String action;
  @Column(columnDefinition = "text") public String beforeData;
  @Column(columnDefinition = "text") public String afterData;
}
