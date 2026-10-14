package com.negociofacil.customer;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class CustomerDtos {
  public record Input(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Pattern(regexp = "[+0-9() .-]{10,20}") String phone,
      @Email @Size(max = 254) String email,
      @Size(max = 20) String document,
      @Size(max = 3000) String notes) {}

  public record View(
      Long id,
      String name,
      String phone,
      String email,
      String document,
      String notes,
      Instant archivedAt,
      Instant createdAt,
      Instant updatedAt) {}

  public static View view(Customer c) {
    return new View(
        c.id,
        c.name,
        c.phone,
        c.email,
        c.document,
        c.notes,
        c.archivedAt,
        c.createdAt,
        c.updatedAt);
  }
}
