package com.negociofacil.quotation;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "quotation_items")
public class QuotationItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quotation_id", nullable = false)
  public Quotation quotation;

  public String description;

  @Column(precision = 12, scale = 3)
  public BigDecimal quantity;

  @Column(precision = 16, scale = 2)
  public BigDecimal unitPrice;

  @Column(precision = 16, scale = 2)
  public BigDecimal total;
}
