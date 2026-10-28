package com.negociofacil.finance.obligations;

import com.negociofacil.common.PageResult;
import com.negociofacil.finance.FinanceTypes.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/finance")
public class ObligationController {
  private final ObligationService service;
  public ObligationController(ObligationService service){this.service=service;}
  @PostMapping("/obligations") @ResponseStatus(HttpStatus.CREATED)
  public ObligationDtos.Detail create(@Valid @RequestBody ObligationDtos.CreateInput input){return service.create(input);}
  @GetMapping("/obligations/{id}") public ObligationDtos.Detail get(@PathVariable Long id){return service.get(id);}
  @GetMapping("/installments") public PageResult<ObligationDtos.InstallmentView> list(@RequestParam Kind kind,
      @RequestParam(required=false) PaymentStatus status,@RequestParam(required=false) Long accountId,@RequestParam(required=false) Long categoryId,
      @RequestParam(defaultValue="") String search,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(kind,status,accountId,categoryId,search,from,to,page,size);}
  @GetMapping("/installments/{id}") public ObligationDtos.InstallmentView installment(@PathVariable Long id){return service.getInstallment(id);}
  @PutMapping("/installments/{id}") public ObligationDtos.InstallmentView edit(@PathVariable Long id,@Valid @RequestBody ObligationDtos.EditInput input){return service.edit(id,input);}
  @PostMapping("/recurrences") @ResponseStatus(HttpStatus.CREATED)
  public ObligationDtos.RecurrenceView createRecurrence(@Valid @RequestBody ObligationDtos.RecurrenceInput input){return service.createRecurrence(input);}
  @GetMapping("/recurrences/{id}") public ObligationDtos.RecurrenceView recurrence(@PathVariable Long id){return service.getRecurrence(id);}
  @PutMapping("/recurrences/{id}/future") public ObligationDtos.FutureResult future(@PathVariable Long id,@Valid @RequestBody ObligationDtos.FutureInput input){return service.updateFuture(id,input);}
  @PostMapping("/recurrences/{id}/cancel-future") public ObligationDtos.FutureResult cancelFuture(@PathVariable Long id,@Valid @RequestBody ObligationDtos.CancelFutureInput input){return service.cancelFuture(id,input);}
}
