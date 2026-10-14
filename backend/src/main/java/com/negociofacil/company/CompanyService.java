package com.negociofacil.company;

import com.negociofacil.common.ApiException;
import com.negociofacil.security.CurrentUser;
import com.negociofacil.user.UserRepository;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CompanyService {
  public record Input(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Pattern(regexp = "[+0-9() .-]{10,20}") String phone,
      @Size(max = 20) String document,
      @NotBlank @Size(max = 160) String responsibleName) {}

  public record View(Long id, String name, String phone, String document, String responsibleName) {}

  private final CompanyRepository companies;
  private final UserRepository users;

  public CompanyService(CompanyRepository companies, UserRepository users) {
    this.companies = companies;
    this.users = users;
  }

  public View get() {
    var c = companies.findById(CurrentUser.companyId()).orElseThrow(ApiException::missing);
    var u = users.findById(CurrentUser.get().userId()).orElseThrow(ApiException::missing);
    return new View(c.id, c.name, c.phone, c.document, u.name);
  }

  public View update(Input input) {
    var c = companies.lockById(CurrentUser.companyId()).orElseThrow(ApiException::missing);
    c.name = input.name().trim();
    c.phone = input.phone();
    c.document = input.document();
    var u = users.lockById(CurrentUser.get().userId()).orElseThrow(ApiException::missing);
    u.name = input.responsibleName().trim();
    return get();
  }
}
