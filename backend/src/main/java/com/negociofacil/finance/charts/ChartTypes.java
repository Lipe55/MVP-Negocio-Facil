package com.negociofacil.finance.charts;

public final class ChartTypes {
  private ChartTypes() {}
  public enum Metric { RECEITAS_RECEBIDAS, DESPESAS_PAGAS, RESULTADO_CAIXA, SALDO_DISPONIVEL, RETIRADAS_PESSOAIS }
  public enum Type { BARRAS, LINHAS, AREA, PIZZA, ROSCA }
  public enum Grouping { DIA, SEMANA, MES, ANO, CATEGORIA, CONTA }
  public enum Period { ULTIMOS_7_DIAS, ULTIMOS_30_DIAS, MES_ATUAL, ULTIMOS_3_MESES, ULTIMOS_6_MESES, ULTIMOS_12_MESES, ANO_ATUAL, PERSONALIZADO }
  public enum Size { PEQUENO, MEDIO, GRANDE }
  public enum Palette { PADRAO, ACESSIVEL }
  public static String label(Metric metric) {
    return switch(metric) {
      case RECEITAS_RECEBIDAS -> "Receitas recebidas";
      case DESPESAS_PAGAS -> "Despesas pagas";
      case RESULTADO_CAIXA -> "Resultado operacional de caixa";
      case SALDO_DISPONIVEL -> "Saldo disponível";
      case RETIRADAS_PESSOAIS -> "Retiradas pessoais";
    };
  }
  public static boolean temporal(Grouping grouping) { return grouping != Grouping.CATEGORIA && grouping != Grouping.CONTA; }
}
