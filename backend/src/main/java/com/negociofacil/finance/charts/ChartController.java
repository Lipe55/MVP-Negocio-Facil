package com.negociofacil.finance.charts;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/finance/charts")
public class ChartController {
  private final ChartService service;
  public ChartController(ChartService service){this.service=service;}
  @GetMapping public ChartDtos.Panel list(){return service.list();}
  @GetMapping("/catalog") public ChartDtos.Catalog catalog(){return service.catalog();}
  @GetMapping("/{id}") public ChartDtos.View get(@PathVariable Long id){return service.get(id);}
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public ChartDtos.View create(@Valid @RequestBody ChartDtos.Input input){return service.create(input);}
  @PutMapping("/{id}") public ChartDtos.View update(@PathVariable Long id,@Valid @RequestBody ChartDtos.Input input){return service.update(id,input);}
  @PostMapping("/{id}/duplicate") @ResponseStatus(HttpStatus.CREATED)
  public ChartDtos.View duplicate(@PathVariable Long id,@Valid @RequestBody ChartDtos.VersionInput input){return service.duplicate(id,input);}
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id,@RequestParam Long version){service.delete(id,version);}
  @PutMapping("/order") public ChartDtos.Panel order(@Valid @RequestBody ChartDtos.OrderInput input){return service.order(input);}
  @PostMapping("/restore") public ChartDtos.Panel restore(@Valid @RequestBody ChartDtos.VersionInput input){return service.restore(input);}
  @GetMapping("/{id}/data") public ChartDtos.Data data(@PathVariable Long id){return service.data(id);}
  @PostMapping("/preview") public ChartDtos.Data preview(@Valid @RequestBody ChartDtos.Input input){return service.preview(input);}
}
