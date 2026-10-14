package com.negociofacil.security;

import java.io.Serializable;

public record SessionUser(Long userId, Long companyId, String email, long credentialVersion)
    implements Serializable, java.security.Principal {
  public SessionUser(Long userId, Long companyId, String email) {
    this(userId, companyId, email, 1);
  }

  @Override
  public String getName() {
    return email;
  }
}
