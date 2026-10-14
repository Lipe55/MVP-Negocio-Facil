package com.negociofacil.auth;

import com.negociofacil.common.ApiException;
import com.negociofacil.company.*;
import com.negociofacil.security.*;
import com.negociofacil.user.*;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserRepository users;
  private final CompanyRepository companies;
  private final PasswordEncoder passwords;
  private final String dummyHash;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public AuthService(
      UserRepository users,
      CompanyRepository companies,
      PasswordEncoder passwords,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.users = users;
    this.companies = companies;
    this.passwords = passwords;
    dummyHash = passwords.encode("invalid-login-placeholder");
  }

  private String email(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private void validatePassword(String value) {
    if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ApiException(400, "Senha excede o limite de 72 bytes.");
  }

  @Transactional
  public AuthDtos.Me register(AuthDtos.Register input) {
    validatePassword(input.password());
    String email = email(input.email());
    if (users.existsByEmail(email)) throw new ApiException(409, "E-mail já cadastrado.");
    var company = new Company();
    company.name = input.companyName().trim();
    company.phone = input.companyPhone();
    company.document = input.companyDocument();
    companies.save(company);
    var user = new UserAccount();
    user.companyId = company.id;
    user.name = input.name().trim();
    user.email = email;
    user.passwordHash = passwords.encode(input.password());
    users.save(user);
    return view(user, company);
  }

  @Transactional(readOnly = true)
  public SessionUser login(AuthDtos.Login input) {
    var user = users.findByEmail(email(input.email())).orElse(null);
    boolean valid =
        passwords.matches(input.password(), user == null ? dummyHash : user.passwordHash);
    if (!valid || user == null) throw new ApiException(401, "E-mail ou senha incorretos.");
    return new SessionUser(user.id, user.companyId, user.email, user.credentialVersion);
  }

  @Transactional(readOnly = true)
  public AuthDtos.Me me() {
    var user = users.findById(CurrentUser.get().userId()).orElseThrow(ApiException::missing);
    return view(user, companies.findById(user.companyId).orElseThrow(ApiException::missing));
  }

  @Transactional
  public void changePassword(AuthDtos.Password input) {
    validatePassword(input.newPassword());
    var user = users.lockById(CurrentUser.get().userId()).orElseThrow(ApiException::missing);
    if (!passwords.matches(input.currentPassword(), user.passwordHash))
      throw new ApiException(400, "Senha atual incorreta.");
    if (user.credentialVersion != CurrentUser.get().credentialVersion())
      throw new ApiException(401, "Entre novamente para continuar.");
    user.passwordHash = passwords.encode(input.newPassword());
    user.credentialVersion = Math.incrementExact(user.credentialVersion);
    jdbc.update("delete from spring_session where principal_name=?", user.email);
  }

  private AuthDtos.Me view(UserAccount u, Company c) {
    return new AuthDtos.Me(u.id, u.name, u.email, c.id, c.name);
  }
}
