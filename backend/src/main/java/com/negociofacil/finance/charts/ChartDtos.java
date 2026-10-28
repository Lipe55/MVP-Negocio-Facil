package com.negociofacil.finance.charts;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.negociofacil.common.ApiException;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static com.negociofacil.finance.charts.ChartTypes.*;

public final class ChartDtos {
  private ChartDtos() {}
  public static class Input {
    @NotBlank @jakarta.validation.constraints.Size(max=120) public String title;
    @NotNull public Type type;
    @NotNull @jakarta.validation.constraints.Size(min=1,max=5) public List<@NotNull Metric> metrics;
    @NotNull public Grouping grouping;
    @NotNull public Period period;
    public LocalDate from;
    public LocalDate to;
    @jakarta.validation.constraints.Size(max=50) public List<@NotNull @Positive Long> categoryIds=List.of();
    @jakarta.validation.constraints.Size(max=50) public List<@NotNull @Positive Long> accountIds=List.of();
    public ChartTypes.Size size=ChartTypes.Size.MEDIO;
    public Palette palette=Palette.PADRAO;
    public Boolean showValues=false;
    public Long version;
    @JsonAnySetter public void unsupported(String key,Object ignored) {
      throw new ApiException(400,"Campo ou filtro indisponível no financeiro atual: " + key + ".");
    }
  }
  public record Config(String title,Type type,List<Metric> metrics,Grouping grouping,Period period,
      LocalDate from,LocalDate to,List<Long> categoryIds,List<Long> accountIds,
      ChartTypes.Size size,Palette palette,boolean showValues) {}
  public record View(Long id,String title,Type type,List<Metric> metrics,Grouping grouping,Period period,
      LocalDate from,LocalDate to,List<Long> categoryIds,List<Long> accountIds,
      ChartTypes.Size size,Palette palette,boolean showValues,int position,long version,
      Instant createdAt,Instant updatedAt) {}
  public record Panel(List<View> charts,long version) {}
  public record VersionInput(@NotNull @PositiveOrZero Long version) {}
  public record OrderInput(@NotNull @jakarta.validation.constraints.Size(max=24) List<@NotNull @Positive Long> ids,
      @NotNull @PositiveOrZero Long version) {}
  public record MetricOption(String id,String label,String description,boolean available,String reason,boolean stock) {}
  public record GroupOption(String id,String label) {}
  public record UnavailableFilter(String id,String label,String reason) {}
  public record Catalog(List<MetricOption> metrics,List<GroupOption> groupings,List<UnavailableFilter> unavailableFilters,
      int maxCharts,int maxPoints) {}
  public record Series(Metric metric,String label) {}
  public record Point(String key,String label,Map<Metric,String> values) {}
  public record Data(LocalDate from,LocalDate to,Grouping grouping,List<Series> series,List<Point> points,
      String openingBalance,boolean hasData,List<String> warnings) {}
  public record Range(LocalDate from,LocalDate to) {}
}
