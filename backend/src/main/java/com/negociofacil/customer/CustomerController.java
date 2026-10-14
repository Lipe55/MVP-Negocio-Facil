package com.negociofacil.customer;

import com.negociofacil.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
  private final CustomerService service;

  public CustomerController(CustomerService service) {
    this.service = service;
  }

  @GetMapping
  public PageResult<CustomerDtos.View> list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(search, archived, page, size);
  }

  @GetMapping("/{id}")
  public CustomerDtos.View get(@PathVariable Long id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CustomerDtos.View create(@Valid @RequestBody CustomerDtos.Input input) {
    return service.create(input);
  }

  @PutMapping("/{id}")
  public CustomerDtos.View update(
      @PathVariable Long id, @Valid @RequestBody CustomerDtos.Input input) {
    return service.update(id, input);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }

  @PostMapping("/{id}/archive")
  public CustomerDtos.View archive(@PathVariable Long id) {
    return service.archive(id);
  }
}
