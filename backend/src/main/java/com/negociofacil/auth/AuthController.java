package com.negociofacil.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService service;
  private final HttpSessionSecurityContextRepository contexts;

  public AuthController(AuthService service, HttpSessionSecurityContextRepository contexts) {
    this.service = service;
    this.contexts = contexts;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken());
  }

  @PostMapping("/register")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public AuthDtos.Me register(@Valid @RequestBody AuthDtos.Register input) {
    return service.register(input);
  }

  @PostMapping("/login")
  public AuthDtos.Me login(
      @Valid @RequestBody AuthDtos.Login input,
      HttpServletRequest request,
      HttpServletResponse response) {
    var user = service.login(input);
    request.getSession();
    request.changeSessionId();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    return service.me();
  }

  @GetMapping("/me")
  public AuthDtos.Me me() {
    return service.me();
  }

  @PostMapping("/password")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void password(@Valid @RequestBody AuthDtos.Password input, HttpServletRequest request) {
    service.changePassword(input);
    var session = request.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
  }
}
