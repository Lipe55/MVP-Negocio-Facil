package com.negociofacil.company;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "companies")
public class Company extends BaseEntity {
  public String name;
  public String document;
  public String phone;
  public long nextQuoteNumber = 1;
}
