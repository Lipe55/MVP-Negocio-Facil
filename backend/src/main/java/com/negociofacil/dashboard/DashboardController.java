package com.negociofacil.dashboard;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public DashboardService.Summary summary() {
    return service.summary();
  }
}
