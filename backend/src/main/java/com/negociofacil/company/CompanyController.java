package com.negociofacil.company;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/company")
public class CompanyController {
  private final CompanyService service;

  public CompanyController(CompanyService service) {
    this.service = service;
  }

  @GetMapping
  public CompanyService.View get() {
    return service.get();
  }

  @PutMapping
  public CompanyService.View update(@Valid @RequestBody CompanyService.Input input) {
    return service.update(input);
  }
}
