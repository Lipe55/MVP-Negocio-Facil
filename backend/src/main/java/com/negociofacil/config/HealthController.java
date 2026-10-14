package com.negociofacil.config;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
  private final JdbcTemplate jdbc;

  public HealthController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/api/health")
  public Map<String, String> health() {
    jdbc.queryForObject("select 1", Integer.class);
    return Map.of("status", "UP");
  }
}
