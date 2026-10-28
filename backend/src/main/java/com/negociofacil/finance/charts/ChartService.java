package com.negociofacil.finance.charts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociofacil.common.ApiException;
import com.negociofacil.company.CompanyRepository;
import com.negociofacil.finance.FinanceRules;
import com.negociofacil.security.CurrentUser;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.negociofacil.finance.charts.ChartTypes.*;

@Service @Transactional
public class ChartService {
  private final CompanyRepository companies;
  private final FinancialChartPanelRepository panels;
  private final FinancialChartRepository charts;
  private final ChartAggregation aggregation;
  private final ObjectMapper json;
  public ChartService(CompanyRepository companies,FinancialChartPanelRepository panels,FinancialChartRepository charts,
      ChartAggregation aggregation,ObjectMapper json) {
    this.companies=companies;this.panels=panels;this.charts=charts;this.aggregation=aggregation;this.json=json;
  }
  private Long company(){return CurrentUser.companyId();}
  private Long owner(){return CurrentUser.get().userId();}
  private void lock(){companies.lockById(company()).orElseThrow(ApiException::missing);}
  private List<FinancialChart> owned(){return charts.findByCompanyIdAndOwnerIdOrderByPositionAscIdAsc(company(),owner());}
  private FinancialChart chart(Long id){return charts.findByIdAndCompanyIdAndOwnerId(id,company(),owner()).orElseThrow(ApiException::missing);}
  private FinancialChartPanel panel() {
    var found=panels.findByCompanyIdAndOwnerId(company(),owner());
    if(found.isPresent())return found.get();
    var p=new FinancialChartPanel();p.companyId=company();p.ownerId=owner();panels.saveAndFlush(p);
    defaults(p);return p;
  }
  private String encode(ChartDtos.Config config) {
    try{return json.writeValueAsString(config);}catch(JsonProcessingException e){throw new IllegalStateException("Falha ao salvar configuração.",e);}
  }
  private ChartDtos.Config decode(FinancialChart c) {
    try{return json.readValue(c.configData,ChartDtos.Config.class);}catch(JsonProcessingException e){throw new IllegalStateException("Falha ao ler configuração.",e);}
  }
  private ChartDtos.View view(FinancialChart chart) {
    var c=decode(chart);
    return new ChartDtos.View(chart.id,c.title(),c.type(),c.metrics(),c.grouping(),c.period(),c.from(),c.to(),c.categoryIds(),c.accountIds(),
        c.size(),c.palette(),c.showValues(),chart.position,chart.version,chart.createdAt,chart.updatedAt);
  }
  private void changed(FinancialChartPanel p) {p.updatedAt=Instant.now();panels.flush();}
  private ChartDtos.Panel panelView(FinancialChartPanel p) {return new ChartDtos.Panel(owned().stream().map(this::view).toList(),p.version);}
  private FinancialChart add(FinancialChartPanel panel,ChartDtos.Config config,int position) {
    var c=new FinancialChart();c.companyId=company();c.ownerId=owner();c.panelId=panel.id;c.position=position;c.configData=encode(config);
    return charts.saveAndFlush(c);
  }
  private ChartDtos.Config configuration(ChartDtos.Input input) {
    var c=ChartRules.normalize(input);aggregation.validateOwnership(c);return c;
  }
  private void defaults(FinancialChartPanel panel) {
    List<ChartDtos.Config> configs=List.of(
      standard("Receitas versus despesas",Type.BARRAS,List.of(Metric.RECEITAS_RECEBIDAS,Metric.DESPESAS_PAGAS),Grouping.MES),
      standard("Evolução do saldo",Type.LINHAS,List.of(Metric.SALDO_DISPONIVEL),Grouping.MES),
      standard("Despesas por categoria",Type.ROSCA,List.of(Metric.DESPESAS_PAGAS),Grouping.CATEGORIA),
      standard("Resultado operacional de caixa",Type.LINHAS,List.of(Metric.RESULTADO_CAIXA),Grouping.MES),
      standard("Retiradas pessoais",Type.BARRAS,List.of(Metric.RETIRADAS_PESSOAIS),Grouping.MES));
    for(int i=0;i<configs.size();i++)add(panel,configs.get(i),i);
  }
  private ChartDtos.Config standard(String title,Type type,List<Metric> metrics,Grouping grouping) {
    return new ChartDtos.Config(title,type,metrics,grouping,Period.ULTIMOS_6_MESES,null,null,List.of(),List.of(),Size.MEDIO,Palette.PADRAO,false);
  }
  public ChartDtos.Panel list(){lock();return panelView(panel());}
  @Transactional(readOnly=true)
  public ChartDtos.View get(Long id){return view(chart(id));}
  public ChartDtos.View create(ChartDtos.Input input) {
    lock();var config=configuration(input);var p=panel();var current=owned();
    if(current.size()>=ChartRules.MAX_CHARTS)throw new ApiException(409,"Seu painel permite até 24 gráficos. Exclua um gráfico para adicionar outro.");
    var c=add(p,config,current.size());changed(p);return view(c);
  }
  public ChartDtos.View update(Long id,ChartDtos.Input input) {
    lock();var c=chart(id);FinanceRules.version(input.version,c.version);var config=configuration(input);
    c.configData=encode(config);c.updatedAt=Instant.now();charts.flush();changed(panel());return view(c);
  }
  public ChartDtos.View duplicate(Long id,ChartDtos.VersionInput input) {
    lock();var original=chart(id);FinanceRules.version(input.version(),original.version);var p=panel();var current=owned();
    if(current.size()>=ChartRules.MAX_CHARTS)throw new ApiException(409,"Seu painel permite até 24 gráficos.");
    var c=decode(original);String title=c.title();if(title.length()>112)title=title.substring(0,112);
    var copy=new ChartDtos.Config(title+" (cópia)",c.type(),c.metrics(),c.grouping(),c.period(),c.from(),c.to(),
        c.categoryIds(),c.accountIds(),c.size(),c.palette(),c.showValues());
    var saved=add(p,copy,current.size());changed(p);return view(saved);
  }
  public void delete(Long id,Long version) {
    lock();var c=chart(id);FinanceRules.version(version,c.version);charts.delete(c);charts.flush();
    var remaining=owned();for(int i=0;i<remaining.size();i++)remaining.get(i).position=i;
    charts.flush();changed(panel());
  }
  public ChartDtos.Panel order(ChartDtos.OrderInput input) {
    lock();var p=panel();FinanceRules.version(input.version(),p.version);var current=owned();
    var byId=new HashMap<Long,FinancialChart>();for(var c:current)byId.put(c.id,c);
    if(input.ids().size()!=current.size() || new HashSet<>(input.ids()).size()!=current.size() || !byId.keySet().equals(new HashSet<>(input.ids())))
      throw ChartRules.invalid("A ordem deve incluir todos os seus gráficos uma única vez.");
    for(int i=0;i<input.ids().size();i++)byId.get(input.ids().get(i)).position=i;
    charts.flush();changed(p);return panelView(p);
  }
  public ChartDtos.Panel restore(ChartDtos.VersionInput input) {
    lock();var p=panel();FinanceRules.version(input.version(),p.version);charts.deleteAll(owned());charts.flush();
    defaults(p);changed(p);return panelView(p);
  }
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public ChartDtos.Data data(Long id){return aggregation.data(decode(chart(id)));}
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public ChartDtos.Data preview(ChartDtos.Input input){return aggregation.data(configuration(input));}
  @Transactional(readOnly=true)
  public ChartDtos.Catalog catalog() {
    var available=new ArrayList<ChartDtos.MetricOption>();
    available.add(new ChartDtos.MetricOption("RECEITAS_RECEBIDAS",label(Metric.RECEITAS_RECEBIDAS),"Entradas operacionais efetivamente recebidas, por data de liquidação.",true,null,false));
    available.add(new ChartDtos.MetricOption("DESPESAS_PAGAS",label(Metric.DESPESAS_PAGAS),"Pagamentos operacionais efetivos, sem retiradas ou transferências.",true,null,false));
    available.add(new ChartDtos.MetricOption("RESULTADO_CAIXA",label(Metric.RESULTADO_CAIXA),"Receitas recebidas menos despesas pagas; não representa lucro contábil.",true,null,false));
    available.add(new ChartDtos.MetricOption("SALDO_DISPONIVEL",label(Metric.SALDO_DISPONIVEL),"Posição de saldo no fim de cada intervalo, incluindo abertura, retiradas e transferências.",true,null,true));
    available.add(new ChartDtos.MetricOption("RETIRADAS_PESSOAIS",label(Metric.RETIRADAS_PESSOAIS),"Dinheiro retirado pelo proprietário, separado das despesas operacionais.",true,null,false));
    var unavailable=List.of(
        new String[]{"CONTAS_A_RECEBER","Contas a receber","Comparação por vencimento depende do módulo de obrigações e vencimentos."},
        new String[]{"CONTAS_A_PAGAR","Contas a pagar","Comparação por vencimento depende do módulo de obrigações e vencimentos."},
        new String[]{"CONTAS_VENCIDAS","Contas vencidas","Os lançamentos atuais não possuem data de vencimento."},
        new String[]{"CUSTOS_DIRETOS","Custos diretos dos serviços","Lançamentos ainda não têm vínculo com serviços."},
        new String[]{"MARGEM_CONTRIBUICAO","Margem de contribuição por serviço","Depende da integração financeira com serviços."},
        new String[]{"TAXAS_CARTAO","Taxas de cartão","O módulo de cartões ainda não foi implementado."},
        new String[]{"REPASSES_CARTAO","Repasses de cartão","O módulo de cartões ainda não foi implementado."});
    for(var u:unavailable)available.add(new ChartDtos.MetricOption(u[0],u[1],u[2],false,u[2],u[0].startsWith("CONTAS_")));
    return new ChartDtos.Catalog(available,List.of(new ChartDtos.GroupOption("DIA","Dia"),new ChartDtos.GroupOption("SEMANA","Semana"),
        new ChartDtos.GroupOption("MES","Mês"),new ChartDtos.GroupOption("ANO","Ano"),new ChartDtos.GroupOption("CATEGORIA","Categoria"),new ChartDtos.GroupOption("CONTA","Conta financeira")),
        List.of(new ChartDtos.UnavailableFilter("customerIds","Clientes","Os lançamentos financeiros ainda não estão vinculados aos clientes."),
          new ChartDtos.UnavailableFilter("serviceIds","Serviços","Não há vínculo financeiro com serviços nesta versão."),
          new ChartDtos.UnavailableFilter("subcategoryIds","Subcategorias","O cadastro atual possui apenas categorias."),
          new ChartDtos.UnavailableFilter("status","Situação","As métricas disponíveis consideram movimentos efetivos; pendências não alteram o caixa.")),
        ChartRules.MAX_CHARTS,ChartRules.MAX_POINTS);
  }
}
