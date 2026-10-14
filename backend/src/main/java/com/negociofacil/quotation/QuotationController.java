package com.negociofacil.quotation;

import com.negociofacil.common.PageResult;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotations")
public class QuotationController {
  private final QuotationService service;

  public QuotationController(QuotationService service) {
    this.service = service;
  }

  @GetMapping
  public PageResult<QuotationDtos.View> list(
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) QuotationStatus status,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(customerId, status, from, to, page, size);
  }

  @GetMapping("/{id}")
  public QuotationDtos.View get(@PathVariable Long id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public QuotationDtos.View create(@Valid @RequestBody QuotationDtos.Input input) {
    return service.create(input);
  }

  @PutMapping("/{id}")
  public QuotationDtos.View update(
      @PathVariable Long id, @Valid @RequestBody QuotationDtos.Input input) {
    return service.update(id, input);
  }

  @PostMapping("/{id}/revoke-public")
  public QuotationDtos.View revokePublic(@PathVariable Long id) {
    return service.revokePublic(id);
  }

  @PostMapping("/{id}/send")
  public QuotationDtos.View send(@PathVariable Long id) {
    return service.send(id);
  }
}
