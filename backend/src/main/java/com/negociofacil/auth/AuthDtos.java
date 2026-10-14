package com.negociofacil.auth;

import jakarta.validation.constraints.*;

public final class AuthDtos {
  public record Register(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 10, max = 64) String password,
      @NotBlank @Size(max = 160) String companyName,
      @NotBlank @Pattern(regexp = "[+0-9() .-]{10,20}") String companyPhone,
      @Size(max = 20) String companyDocument) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 64) String password) {}

  public record Me(Long id, String name, String email, Long companyId, String companyName) {}

  public record Password(
      @NotBlank @Size(max = 64) String currentPassword,
      @NotBlank @Size(min = 10, max = 64) String newPassword) {}
}
