package com.negociofacil.security;

import com.negociofacil.common.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
  private CurrentUser() {}

  public static SessionUser get() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof SessionUser user))
      throw new ApiException(401, "Entre para continuar.");
    return user;
  }

  public static Long companyId() {
    return get().companyId();
  }
}
