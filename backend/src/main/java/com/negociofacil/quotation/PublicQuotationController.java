package com.negociofacil.quotation;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/quotations")
public class PublicQuotationController {
  private final QuotationService service;

  public PublicQuotationController(QuotationService service) {
    this.service = service;
  }

  @GetMapping("/{token}")
  public QuotationDtos.PublicView get(@PathVariable String token) {
    return service.publicGet(token);
  }

  @PostMapping("/{token}/accept")
  public QuotationDtos.PublicView accept(@PathVariable String token) {
    return service.respond(token, true);
  }

  @PostMapping("/{token}/reject")
  public QuotationDtos.PublicView reject(@PathVariable String token) {
    return service.respond(token, false);
  }
}
