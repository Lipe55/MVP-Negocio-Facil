package com.negociofacil.quotation;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "quotations")
public class Quotation extends BaseEntity {
  public Long companyId;
  public Long customerId;
  public long number;

  @Enumerated(EnumType.STRING)
  public QuotationStatus status = QuotationStatus.RASCUNHO;

  public java.time.LocalDate validUntil;
  public String notes;
  public String terms;
  public String companyNameSnapshot;
  public String customerNameSnapshot;
  public java.time.Instant publicRevokedAt;

  @Column(precision = 16, scale = 2)
  public java.math.BigDecimal subtotal;

  @Column(precision = 16, scale = 2)
  public java.math.BigDecimal discount;

  @Column(precision = 16, scale = 2)
  public java.math.BigDecimal total;

  public String publicToken;
  public java.time.Instant sentAt;
  public java.time.Instant respondedAt;

  @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id ASC")
  public java.util.List<QuotationItem> items = new java.util.ArrayList<>();
}
