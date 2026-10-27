package com.negociofacil;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.negociofacil.auth.*;
import com.negociofacil.common.ApiException;
import com.negociofacil.company.*;
import com.negociofacil.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
  @Test
  void duplicateNormalizedEmailDoesNotCreateCompany() {
    var users = mock(UserRepository.class);
    var companies = mock(CompanyRepository.class);
    var encoder = mock(PasswordEncoder.class);
    when(users.existsByEmail("user@example.com")).thenReturn(true);
    var service =
        new AuthService(
            users, companies, encoder, mock(org.springframework.jdbc.core.JdbcTemplate.class),
            mock(com.negociofacil.finance.FinanceBootstrap.class));
    assertThatThrownBy(
            () ->
                service.register(
                    new AuthDtos.Register(
                        "User",
                        "USER@example.com",
                        "Password123!",
                        "Company",
                        "11999999999",
                        null)))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(companies);
  }
}
