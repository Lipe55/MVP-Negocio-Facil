package com.negociofacil.common;

import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class Errors {
  public record ErrorBody(
      int status, String message, Map<String, String> fields, Instant timestamp) {}

  private ResponseEntity<ErrorBody> response(
      int status, String message, Map<String, String> fields) {
    return ResponseEntity.status(status)
        .body(new ErrorBody(status, message, fields, Instant.now()));
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorBody> api(ApiException e) {
    return response(e.status, e.getMessage(), Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
    return response(400, "Confira os campos informados.", fields);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    jakarta.validation.ConstraintViolationException.class
  })
  ResponseEntity<ErrorBody> malformed(Exception e) {
    return response(400, "Requisição inválida.", Map.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorBody> conflict(Exception e) {
    return response(409, "Dados em conflito. Atualize a página e tente novamente.", Map.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorBody> denied(Exception e) {
    return response(403, "Acesso negado.", Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorBody> unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(Errors.class)
        .error("Unexpected application error: {}", e.getClass().getSimpleName());
    return response(500, "Não foi possível concluir a operação.", Map.of());
  }
}
