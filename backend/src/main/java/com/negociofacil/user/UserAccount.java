package com.negociofacil.user;

import com.negociofacil.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserAccount extends BaseEntity {
  public Long companyId;
  public String name;
  public String email;
  public String passwordHash;
  public long credentialVersion = 1;
  public String role = "ADMIN";
}
