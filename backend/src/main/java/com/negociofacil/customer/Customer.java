package com.negociofacil.customer;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {
  public Long companyId;
  public String name;
  public String phone;
  public String email;
  public String document;
  public String notes;
  public java.time.Instant archivedAt;
}
