package com.negociofacil.finance;

import com.negociofacil.common.PageResult;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.negociofacil.finance.FinanceTypes.*;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {
  private final FinanceService service;
  public FinanceController(FinanceService service) { this.service=service; }
  @GetMapping("/accounts") public List<FinanceDtos.AccountView> accounts() { return service.accounts(); }
  @PostMapping("/accounts") @ResponseStatus(HttpStatus.CREATED)
  public FinanceDtos.AccountView createAccount(@Valid @RequestBody FinanceDtos.AccountInput input) { return service.createAccount(input); }
  @PutMapping("/accounts/{id}") public FinanceDtos.AccountView updateAccount(@PathVariable Long id,@Valid @RequestBody FinanceDtos.AccountInput input) { return service.updateAccount(id,input); }
  @GetMapping("/categories") public List<FinanceDtos.CategoryView> categories() { return service.categories(); }
  @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
  public FinanceDtos.CategoryView createCategory(@Valid @RequestBody FinanceDtos.CategoryInput input) { return service.createCategory(input); }
  @GetMapping("/entries") public PageResult<FinanceDtos.EntryView> entries(
      @RequestParam(required=false) Kind kind,@RequestParam(required=false) Status status,
      @RequestParam(required=false) Long accountId,@RequestParam(required=false) Long categoryId,
      @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
      @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
    return service.entries(kind,status,accountId,categoryId,from,to,search,page,size);
  }
  @GetMapping("/entries/{id}") public FinanceDtos.EntryView getEntry(@PathVariable Long id) { return service.getEntry(id); }
  @PostMapping("/entries") @ResponseStatus(HttpStatus.CREATED)
  public FinanceDtos.EntryView createEntry(@Valid @RequestBody FinanceDtos.EntryInput input) { return service.createEntry(input); }
  @PutMapping("/entries/{id}") public FinanceDtos.EntryView updateEntry(@PathVariable Long id,@Valid @RequestBody FinanceDtos.EntryInput input) { return service.updateEntry(id,input); }
  @PostMapping("/entries/{id}/settle") public FinanceDtos.EntryView settle(@PathVariable Long id,@Valid @RequestBody FinanceDtos.SettlementInput input) { return service.settleEntry(id,input); }
  @PostMapping("/entries/{id}/cancel") public FinanceDtos.EntryView cancel(@PathVariable Long id,@Valid @RequestBody FinanceDtos.VersionInput input) { return service.cancelEntry(id,input); }
  @PostMapping("/transfers") @ResponseStatus(HttpStatus.CREATED)
  public FinanceDtos.TransferView transfer(@Valid @RequestBody FinanceDtos.TransferInput input) { return service.transfer(input); }
  @PostMapping("/transfers/{id}/cancel") public FinanceDtos.TransferView cancelTransfer(@PathVariable Long id,@Valid @RequestBody FinanceDtos.VersionInput input) { return service.cancelTransfer(id,input); }
  @GetMapping("/accounts/{id}/statement") public FinanceDtos.Statement statement(@PathVariable Long id,
      @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.statement(id,from,to,page,size); }
  @GetMapping("/history/{resource}/{id}") public List<FinanceDtos.AuditView> history(@PathVariable String resource,@PathVariable Long id) { return service.history(resource,id); }
}
