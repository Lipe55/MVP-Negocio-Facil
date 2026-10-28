package com.negociofacil.finance.obligations;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.negociofacil.finance.FinanceRules;
import com.negociofacil.security.SessionUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties={
    "spring.datasource.url=${FINANCE_TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/negocio_finance_test}",
    "spring.datasource.username=${FINANCE_TEST_DATABASE_USER:postgres}",
    "spring.datasource.password=${FINANCE_TEST_DATABASE_PASSWORD:unused}"})
@ContextConfiguration(initializers=ObligationIntegrationTest.DatabaseGuard.class)
@AutoConfigureMockMvc
class ObligationIntegrationTest {
  public static class DatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public void initialize(ConfigurableApplicationContext context) {
      String url=context.getEnvironment().getProperty("spring.datasource.url","");
      if(!url.matches("jdbc:postgresql://[^/]+/negocio_finance_test(?:\\?.*)?"))
        throw new IllegalStateException("Obligation tests require isolated negocio_finance_test before Flyway.");
    }
  }
  private static final String ROOT="/api/finance";
  private static final AtomicInteger counter=new AtomicInteger();
  @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired javax.sql.DataSource dataSource;
  SessionUser owner,other;
  @BeforeEach void prepare()throws Exception {
    assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("negocio_finance_test");
    jdbc.execute("TRUNCATE companies CASCADE");owner=register("obligation-owner@example.com");other=register("obligation-other@example.com");
    var main=main(owner);call(body(put(ROOT+"/accounts/"+main.get("id").asLong()),Map.of("name","Conta principal","type","BANCO","openingBalance","0",
        "openingDate","2020-01-01","active",true,"version",main.get("version").asLong())),owner).andExpect(status().isOk());
  }
  private LocalDate today(){return FinanceRules.today();}
  private SessionUser register(String email)throws Exception {
    var result=mvc.perform(body(post("/api/auth/register").with(csrf()),Map.of("name","Responsável","email",email,"password","SyntheticObligation123!",
        "companyName","Empresa teste","companyPhone","11999999999"))).andExpect(status().isCreated());
    var n=data(result);return new SessionUser(n.get("id").asLong(),n.get("companyId").asLong(),email);
  }
  private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder r,Object v)throws Exception {
    return r.contentType("application/json").content(json.writeValueAsString(v)).with(req->{req.setRemoteAddr("192.0.2."+counter.incrementAndGet());return req;});
  }
  private ResultActions call(MockHttpServletRequestBuilder r,SessionUser u)throws Exception {
    return mvc.perform(r.with(authentication(new UsernamePasswordAuthenticationToken(u,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))).with(csrf()));
  }
  private JsonNode data(ResultActions r)throws Exception{return json.readTree(r.andReturn().getResponse().getContentAsString());}
  private JsonNode main(SessionUser u)throws Exception {
    for(var n:data(call(get(ROOT+"/accounts"),u)))if(n.get("primaryAccount").asBoolean())return n;throw new AssertionError();
  }
  private long category(SessionUser u,String kind)throws Exception {
    for(var c:data(call(get(ROOT+"/categories"),u)))if(c.get("kind").asText().equals(kind))return c.get("id").asLong();throw new AssertionError();
  }
  private BigDecimal balance()throws Exception{return new BigDecimal(main(owner).get("balance").asText());}
  private Map<String,Object> input(String kind,String amount,int count,LocalDate due)throws Exception {
    var v=new HashMap<String,Object>();v.put("requestId",UUID.randomUUID().toString());v.put("kind",kind);v.put("description","Obrigação de teste");v.put("amount",amount);
    v.put("categoryId",category(owner,kind));v.put("accountId",main(owner).get("id").asLong());v.put("entryDate",today().toString());
    v.put("dueDate",due.toString());v.put("installments",count);return v;
  }
  private JsonNode create(Map<String,Object> input)throws Exception{return data(call(body(post(ROOT+"/obligations"),input),owner).andExpect(status().isCreated()));}
  private JsonNode getInstallment(long id)throws Exception{return data(call(get(ROOT+"/installments/"+id),owner).andExpect(status().isOk()));}
  private JsonNode getParent(long id)throws Exception{return data(call(get(ROOT+"/obligations/"+id),owner).andExpect(status().isOk()));}
  private Map<String,Object> payment(JsonNode entry,String amount)throws Exception {
    var p=new HashMap<String,Object>();p.put("requestId",UUID.randomUUID().toString());p.put("version",entry.get("version").asLong());p.put("settlementDate",today().toString());
    if(amount!=null)p.put("amount",amount);return p;
  }
  private JsonNode pay(JsonNode e,String amount)throws Exception{return data(call(body(post(ROOT+"/entries/"+e.get("id").asLong()+"/settle"),payment(e,amount)),owner).andExpect(status().isOk()));}
  private Map<String,Object> edit(JsonNode e) {
    var p=new HashMap<String,Object>();p.put("version",e.get("version").asLong());p.put("description",e.get("description").asText());p.put("amount",e.get("amount").asText());
    p.put("accountId",e.get("accountId").asLong());p.put("categoryId",e.get("categoryId").asLong());p.put("entryDate",e.get("entryDate").asText());
    if(!e.get("dueDate").isNull())p.put("dueDate",e.get("dueDate").asText());return p;
  }
  private JsonNode recurrence(LocalDate first,int count)throws Exception {
    return data(call(body(post(ROOT+"/recurrences"),recurrenceInput(first,count)),owner).andExpect(status().isCreated()));
  }
  private Map<String,Object> recurrenceInput(LocalDate first,int count)throws Exception {
    return new HashMap<>(Map.of("requestId",UUID.randomUUID().toString(),"description","Aluguel mensal","amount","100.00","categoryId",category(owner,"DESPESA"),
        "accountId",main(owner).get("id").asLong(),"firstDueDate",first.toString(),"occurrences",count));
  }
  private JsonNode occurrences(long recurrenceId)throws Exception {
    return data(call(get(ROOT+"/installments").param("kind","DESPESA").param("size","100"),owner)).get("content");
  }
  @Test void receivable1500Received500Then1000TracksRealCashAndRemaining()throws Exception {
    var parent=create(input("RECEITA","1500",1,today().plusDays(10)));var e=parent.get("installments").get(0);
    assertThat(balance()).isEqualByComparingTo("0");assertThat(e.get("remainingAmount").asText()).isEqualTo("1500.00");
    var first=pay(e,"500");assertThat(first.get("status").asText()).isEqualTo("PARCIAL");assertThat(first.get("paymentStatus").asText()).isEqualTo("PARCIAL");
    assertThat(first.get("paidAmount").asText()).isEqualTo("500.00");assertThat(first.get("remainingAmount").asText()).isEqualTo("1000.00");assertThat(balance()).isEqualByComparingTo("500");
    var last=pay(first,"1000");assertThat(last.get("status").asText()).isEqualTo("LIQUIDADO");assertThat(last.get("paymentStatus").asText()).isEqualTo("QUITADO");
    assertThat(last.get("remainingAmount").asText()).isEqualTo("0.00");assertThat(balance()).isEqualByComparingTo("1500");
    assertThat(data(call(get(ROOT+"/entries/"+e.get("id").asLong()+"/settlements"),owner)).size()).isEqualTo(2);
    assertThat(getParent(parent.get("id").asLong()).get("version").asLong()).isGreaterThan(parent.get("version").asLong());
  }
  @Test void installmentsPreserveExactTotalsAndOnlyPaidInstallmentAffectsCash()throws Exception {
    var six=create(input("DESPESA","1200",6,LocalDate.of(2024,1,31)));assertThat(six.get("installments").size()).isEqualTo(6);
    for(var e:six.get("installments"))assertThat(e.get("amount").asText()).isEqualTo("200.00");
    assertThat(six.get("installments").get(1).get("dueDate").asText()).isEqualTo("2024-02-29");
    assertThat(six.get("installments").get(2).get("dueDate").asText()).isEqualTo("2024-03-31");
    assertThat(balance()).isEqualByComparingTo("0");pay(six.get("installments").get(2),null);assertThat(balance()).isEqualByComparingTo("-200");
    var current=getParent(six.get("id").asLong());assertThat(current.get("installments").get(1).get("paidAmount").asText()).isEqualTo("0.00");
    var cents=create(input("DESPESA","100",3,today()));assertThat(cents.get("installments").get(0).get("amount").asText()).isEqualTo("33.34");
    assertThat(cents.get("installments").get(1).get("amount").asText()).isEqualTo("33.33");assertThat(cents.get("totalAmount").asText()).isEqualTo("100.00");
  }
  @Test void repeatedCreationAndPaymentAreIdempotentAndConflictingReuseIsRejected()throws Exception {
    var input=input("RECEITA","100",1,today());var a=create(input);var b=create(input);assertThat(a.get("id")).isEqualTo(b.get("id"));
    assertThat(jdbc.queryForObject("select count(*) from financial_obligations where company_id=?",Integer.class,owner.companyId())).isEqualTo(1);
    var e=a.get("installments").get(0);var p=payment(e,"25");
    for(int i=0;i<2;i++)call(body(post(ROOT+"/entries/"+e.get("id").asLong()+"/settle"),p),owner).andExpect(status().isOk());
    assertThat(balance()).isEqualByComparingTo("25");p.put("amount","26");call(body(post(ROOT+"/entries/"+e.get("id").asLong()+"/settle"),p),owner).andExpect(status().isConflict());
    input.put("amount","101");call(body(post(ROOT+"/obligations"),input),owner).andExpect(status().isConflict());
  }
  @Test void rejectsOverpaymentAndAdvancesVersionForEveryPartial()throws Exception {
    var e=create(input("DESPESA","100",1,today())).get("installments").get(0);var p=pay(e,"20");var second=pay(p,"20");
    assertThat(second.get("version").asLong()).isGreaterThan(p.get("version").asLong());assertThat(second.get("status").asText()).isEqualTo("PARCIAL");
    call(body(post(ROOT+"/entries/"+e.get("id").asLong()+"/settle"),payment(second,"60.01")),owner).andExpect(status().isBadRequest());
    call(body(post(ROOT+"/entries/"+e.get("id").asLong()+"/settle"),payment(second,"0")),owner).andExpect(status().isBadRequest());
    assertThat(balance()).isEqualByComparingTo("-40");
  }
  @Test void cancellationReversesAllPartialsAndLeavesNoOutstandingDebt()throws Exception {
    var e=create(input("RECEITA","100",1,today().minusDays(2))).get("installments").get(0);var p=pay(e,"20");var last=pay(p,"30");
    var canceled=data(call(body(post(ROOT+"/entries/"+e.get("id").asLong()+"/cancel"),Map.of("version",last.get("version").asLong())),owner).andExpect(status().isOk()));
    assertThat(balance()).isEqualByComparingTo("0");assertThat(canceled.get("remainingAmount").asText()).isEqualTo("0.00");
    assertThat(canceled.get("paymentStatus").asText()).isEqualTo("CANCELADO");assertThat(canceled.get("overdue").asBoolean()).isFalse();
    for(var s:data(call(get(ROOT+"/entries/"+e.get("id").asLong()+"/settlements"),owner)))assertThat(s.get("reversedAt").isNull()).isFalse();
    assertThat(data(call(get(ROOT+"/installments").param("kind","RECEITA").param("status","VENCIDO"),owner)).get("totalElements").asInt()).isZero();
  }
  @Test void individualReversalPreservesOtherPaymentsAndAdvancesPartialVersionAndAuditReason()throws Exception {
    var e=create(input("RECEITA","100",1,today())).get("installments").get(0);var p=pay(e,"20");var last=pay(p,"30");
    var history=data(call(get(ROOT+"/entries/"+e.get("id").asLong()+"/settlements"),owner));long settlement=history.get(0).get("id").asLong();
    var reversed=data(call(body(post(ROOT+"/entries/"+e.get("id").asLong()+"/settlements/"+settlement+"/reverse"),Map.of("version",last.get("version").asLong(),"reason","Duplicidade identificada")),owner).andExpect(status().isOk()));
    assertThat(reversed.get("paidAmount").asText()).isEqualTo("30.00");assertThat(reversed.get("status").asText()).isEqualTo("PARCIAL");
    assertThat(reversed.get("version").asLong()).isGreaterThan(last.get("version").asLong());assertThat(balance()).isEqualByComparingTo("30");
    var audit=data(call(get(ROOT+"/history/LANCAMENTO/"+e.get("id").asLong()),owner));assertThat(audit.toString()).contains("Duplicidade identificada");
    var edit=edit(getInstallment(e.get("id").asLong()));edit.put("amount","90");call(body(put(ROOT+"/installments/"+e.get("id").asLong()),edit),owner).andExpect(status().isConflict());
  }
  @Test void fullyReversedPaymentsKeepFinancialFieldsLockedButMetadataEditable()throws Exception {
    var e=create(input("DESPESA","100",1,today())).get("installments").get(0);var p=pay(e,"30");
    var s=data(call(get(ROOT+"/entries/"+e.get("id").asLong()+"/settlements"),owner)).get(0);
    call(body(post(ROOT+"/entries/"+e.get("id").asLong()+"/settlements/"+s.get("id").asLong()+"/reverse"),Map.of("version",p.get("version").asLong(),"reason","Correção")),owner).andExpect(status().isOk());
    var current=getInstallment(e.get("id").asLong());assertThat(current.get("hasSettlementHistory").asBoolean()).isTrue();assertThat(current.get("paidAmount").asText()).isEqualTo("0.00");
    var edit=edit(current);edit.put("amount","99");call(body(put(ROOT+"/installments/"+e.get("id").asLong()),edit),owner).andExpect(status().isConflict());
    edit.put("amount","100.00");edit.put("description","Descrição corrigida");edit.put("dueDate",today().plusDays(20).toString());
    call(body(put(ROOT+"/installments/"+e.get("id").asLong()),edit),owner).andExpect(status().isOk());
  }
  @Test void editsOneUnpaidInstallmentUpdatesParentTotalWithoutTouchingOthersAndBlocksLegacyBypass()throws Exception {
    var parent=create(input("DESPESA","1200",6,today().plusDays(5)));var e=parent.get("installments").get(2);var edit=edit(e);
    edit.put("amount","201.01");edit.put("description","Parcela ajustada");
    call(body(put(ROOT+"/installments/"+e.get("id").asLong()),edit),owner).andExpect(status().isOk());
    var updated=getParent(parent.get("id").asLong());assertThat(updated.get("totalAmount").asText()).isEqualTo("1201.01");
    assertThat(updated.get("installments").get(1).get("amount").asText()).isEqualTo("200.00");assertThat(updated.get("version").asLong()).isGreaterThan(parent.get("version").asLong());
    var bypass=new HashMap<>(edit);bypass.put("requestId",UUID.randomUUID().toString());bypass.put("kind","DESPESA");bypass.put("settled",false);
    bypass.put("version",updated.get("installments").get(2).get("version").asLong());call(body(put(ROOT+"/entries/"+e.get("id").asLong()),bypass),owner).andExpect(status().isConflict());
    assertThat(balance()).isEqualByComparingTo("0");
  }
  @Test void overdueAndPartialFiltersUseRealOutstandingBalanceAndLegacyNullDueDatesAreNotOverdue()throws Exception {
    var old=create(input("RECEITA","100",1,today().minusDays(1))).get("installments").get(0);pay(old,"20");
    create(input("RECEITA","50",1,today().plusDays(1)));
    var overdue=data(call(get(ROOT+"/installments").param("kind","RECEITA").param("status","VENCIDO"),owner));
    assertThat(overdue.get("totalElements").asInt()).isEqualTo(1);assertThat(overdue.get("content").get(0).get("paymentStatus").asText()).isEqualTo("VENCIDO");
    assertThat(data(call(get(ROOT+"/installments").param("kind","RECEITA").param("status","PARCIAL"),owner)).get("totalElements").asInt()).isEqualTo(1);
    var payload=new HashMap<String,Object>();payload.put("requestId",UUID.randomUUID().toString());payload.put("kind","RECEITA");payload.put("accountId",main(owner).get("id").asLong());
    payload.put("categoryId",category(owner,"RECEITA"));payload.put("description","Lançamento sem vencimento");payload.put("amount","10");payload.put("settled",false);
    var quick=data(call(body(post(ROOT+"/entries"),payload),owner).andExpect(status().isCreated()));assertThat(quick.get("dueDate").isNull()).isTrue();assertThat(quick.get("overdue").asBoolean()).isFalse();
    assertThat(quick.get("obligationId").isNull()).isFalse();assertThat(quick.get("installmentCount").asInt()).isEqualTo(1);
    assertThat(data(call(get(ROOT+"/installments").param("kind","RECEITA").param("status","VENCIDO"),owner)).get("totalElements").asInt()).isEqualTo(1);
  }
  @Test void recurrenceGenerationIsFiniteIdempotentAndMonthEndDoesNotDrift()throws Exception {
    var payload=recurrenceInput(LocalDate.of(2024,1,31),3);var r=data(call(body(post(ROOT+"/recurrences"),payload),owner).andExpect(status().isCreated()));
    var again=data(call(body(post(ROOT+"/recurrences"),payload),owner).andExpect(status().isCreated()));assertThat(again.get("id")).isEqualTo(r.get("id"));
    var rows=occurrences(r.get("id").asLong());assertThat(rows.size()).isEqualTo(3);
    assertThat(rows.get(0).get("dueDate").asText()).isEqualTo("2024-01-31");assertThat(rows.get(1).get("dueDate").asText()).isEqualTo("2024-02-29");
    assertThat(rows.get(2).get("dueDate").asText()).isEqualTo("2024-03-31");assertThat(balance()).isEqualByComparingTo("0");
    payload.put("amount","101");call(body(post(ROOT+"/recurrences"),payload),owner).andExpect(status().isConflict());
  }
  @Test void recurrenceFutureEditsPreservePastAndPartiallyPaidOccurrencesAndCanCancelUnpaidFuture()throws Exception {
    var r=recurrence(today().minusMonths(1),4);var rows=occurrences(r.get("id").asLong());
    var past=rows.get(0);var selected=rows.get(1);var partial=pay(rows.get(2),"25");long recId=r.get("id").asLong();
    r=data(call(get(ROOT+"/recurrences/"+recId),owner));
    var future=new HashMap<String,Object>();future.put("version",r.get("version").asLong());future.put("fromObligationId",past.get("obligationId").asLong());
    future.put("description","Aluguel atualizado");future.put("amount","120");future.put("categoryId",category(owner,"DESPESA"));future.put("accountId",main(owner).get("id").asLong());future.put("dueDay",today().getDayOfMonth());
    var result=data(call(body(put(ROOT+"/recurrences/"+recId+"/future"),future),owner).andExpect(status().isOk()));
    assertThat(result.get("updatedCount").asInt()).isEqualTo(2);assertThat(result.get("skippedCount").asInt()).isEqualTo(2);
    assertThat(getInstallment(past.get("id").asLong()).get("amount").asText()).isEqualTo("100.00");
    assertThat(getInstallment(partial.get("id").asLong()).get("amount").asText()).isEqualTo("100.00");assertThat(getInstallment(selected.get("id").asLong()).get("amount").asText()).isEqualTo("120.00");
    r=data(call(get(ROOT+"/recurrences/"+recId),owner));var cancel=data(call(body(post(ROOT+"/recurrences/"+recId+"/cancel-future"),Map.of("version",r.get("version").asLong(),"fromObligationId",past.get("obligationId").asLong())),owner).andExpect(status().isOk()));
    assertThat(cancel.get("updatedCount").asInt()).isEqualTo(2);assertThat(balance()).isEqualByComparingTo("-25");
    assertThat(getInstallment(partial.get("id").asLong()).get("status").asText()).isEqualTo("PARCIAL");
  }
  @Test void concurrentPaymentsAndDuplicateKeysNeverOverpayOrDoublePost()throws Exception {
    var e=create(input("RECEITA","100",1,today())).get("installments").get(0);long id=e.get("id").asLong();
    var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(2)) {
      var futures=new ArrayList<Future<Integer>>();
      for(int i=0;i<2;i++)futures.add(pool.submit(()->{var request=body(post(ROOT+"/entries/"+id+"/settle"),payment(e,"70"));ready.countDown();assertThat(start.await(5,TimeUnit.SECONDS)).isTrue();return call(request,owner).andReturn().getResponse().getStatus();}));
      assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
      assertThat(List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
    }
    assertThat(balance()).isEqualByComparingTo("70");assertThat(getInstallment(id).get("remainingAmount").asText()).isEqualTo("30.00");
    var sameKey=payment(getInstallment(id),"10");
    try(var pool=Executors.newFixedThreadPool(2)) {
      var first=pool.submit(()->call(body(post(ROOT+"/entries/"+id+"/settle"),sameKey),owner).andReturn().getResponse().getStatus());
      var second=pool.submit(()->call(body(post(ROOT+"/entries/"+id+"/settle"),sameKey),owner).andReturn().getResponse().getStatus());
      assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactly(200,200);
    }
    assertThat(balance()).isEqualByComparingTo("80");
    assertThat(jdbc.queryForObject("select count(*) from financial_settlements where company_id=? and entry_id=?",Integer.class,owner.companyId(),id)).isEqualTo(2);
  }
  @Test void tenantCannotReadModifyPayReverseCancelOrLinkForeignRecords()throws Exception {
    var parent=create(input("RECEITA","100",2,today()));var e=parent.get("installments").get(0);long id=e.get("id").asLong(),pid=parent.get("id").asLong();
    call(get(ROOT+"/obligations/"+pid),other).andExpect(status().isNotFound());call(get(ROOT+"/installments/"+id),other).andExpect(status().isNotFound());
    call(body(put(ROOT+"/installments/"+id),edit(e)),other).andExpect(status().isNotFound());call(body(post(ROOT+"/entries/"+id+"/settle"),payment(e,"20")),other).andExpect(status().isNotFound());
    var paid=pay(e,"20");var s=data(call(get(ROOT+"/entries/"+id+"/settlements"),owner)).get(0);
    call(get(ROOT+"/entries/"+id+"/settlements"),other).andExpect(status().isNotFound());
    call(body(post(ROOT+"/entries/"+id+"/settlements/"+s.get("id").asLong()+"/reverse"),Map.of("version",paid.get("version").asLong(),"reason","Tentativa")),other).andExpect(status().isNotFound());
    call(body(post(ROOT+"/entries/"+id+"/cancel"),Map.of("version",paid.get("version").asLong())),other).andExpect(status().isNotFound());
    call(get(ROOT+"/history/OBRIGACAO/"+pid),other).andExpect(status().isNotFound());
    var foreign=input("DESPESA","10",1,today());foreign.put("accountId",main(other).get("id").asLong());call(body(post(ROOT+"/obligations"),foreign),owner).andExpect(status().isNotFound());
    foreign.put("accountId",main(owner).get("id").asLong());foreign.put("categoryId",category(other,"DESPESA"));call(body(post(ROOT+"/obligations"),foreign),owner).andExpect(status().isNotFound());
    call(get(ROOT+"/installments").param("kind","RECEITA").param("accountId",main(other).get("id").asText()),owner).andExpect(status().isNotFound());
  }
  @Test void recurrenceAndHistoryEndpointsRejectOtherCompaniesAndForeignScopeParents()throws Exception {
    var r=recurrence(today().plusMonths(1),2);long id=r.get("id").asLong();var p=occurrences(id).get(0).get("obligationId").asLong();
    call(get(ROOT+"/recurrences/"+id),other).andExpect(status().isNotFound());call(get(ROOT+"/history/RECORRENCIA/"+id),other).andExpect(status().isNotFound());
    var future=Map.of("version",r.get("version").asLong(),"fromObligationId",p,"description","Mudança","amount","90","categoryId",category(owner,"DESPESA"),"accountId",main(owner).get("id").asLong(),"dueDay",15);
    call(body(put(ROOT+"/recurrences/"+id+"/future"),future),other).andExpect(status().isNotFound());
    call(body(post(ROOT+"/recurrences/"+id+"/cancel-future"),Map.of("version",r.get("version").asLong(),"fromObligationId",p)),other).andExpect(status().isNotFound());
    var unrelated=create(input("DESPESA","50",1,today()));var wrong=new HashMap<>(future);wrong.put("fromObligationId",unrelated.get("id").asLong());
    call(body(put(ROOT+"/recurrences/"+id+"/future"),wrong),owner).andExpect(status().isNotFound());
  }
  @Test void paidPartialsFeedChartsOnlyWithCashAlreadyReceived()throws Exception {
    var e=create(input("RECEITA","1500",1,today().plusDays(10))).get("installments").get(0);pay(e,"500");
    var c=Map.of("title","Receitas reais","type","BARRAS","metrics",List.of("RECEITAS_RECEBIDAS"),"grouping","MES","period","ULTIMOS_12_MESES");
    var d=data(call(body(post(ROOT+"/charts/preview"),c),owner).andExpect(status().isOk()));BigDecimal total=BigDecimal.ZERO;
    for(var p:d.get("points"))total=total.add(new BigDecimal(p.get("values").get("RECEITAS_RECEBIDAS").asText()));
    assertThat(total).isEqualByComparingTo("500");assertThat(getInstallment(e.get("id").asLong()).get("remainingAmount").asText()).isEqualTo("1000.00");
  }
  @Test void aZeroBalanceAccountWithPartialDebtCannotBeDeactivated()throws Exception {
    var account=data(call(body(post(ROOT+"/accounts"),Map.of("name","Secundária","type","DINHEIRO","openingBalance","0","openingDate",today().toString(),"active",true)),owner).andExpect(status().isCreated()));
    var v=input("DESPESA","100",1,today());v.put("accountId",account.get("id").asLong());var e=create(v).get("installments").get(0);pay(e,"20");
    var received=new HashMap<String,Object>();received.put("requestId",UUID.randomUUID().toString());received.put("kind","RECEITA");received.put("description","Entrada");received.put("amount","20");
    received.put("accountId",account.get("id").asLong());received.put("categoryId",category(owner,"RECEITA"));received.put("settled",true);
    call(body(post(ROOT+"/entries"),received),owner).andExpect(status().isCreated());
    call(body(put(ROOT+"/accounts/"+account.get("id").asLong()),Map.of("name","Secundária","type","DINHEIRO","openingBalance","0","openingDate",today().toString(),"active",false,"version",account.get("version").asLong())),owner).andExpect(status().isConflict());
  }
  @Test void validatesInstallmentCountsKindsFiltersAndAuthentication()throws Exception {
    var v=input("DESPESA","0.01",2,today());call(body(post(ROOT+"/obligations"),v),owner).andExpect(status().isBadRequest());
    v=input("DESPESA","100",121,today());call(body(post(ROOT+"/obligations"),v),owner).andExpect(status().isBadRequest());
    v=input("DESPESA","100",2,today());v.put("settled",true);call(body(post(ROOT+"/obligations"),v),owner).andExpect(status().isBadRequest());
    v=input("DESPESA","100",1,today());v.put("kind","RETIRADA");call(body(post(ROOT+"/obligations"),v),owner).andExpect(status().isBadRequest());
    call(get(ROOT+"/installments").param("kind","RETIRADA"),owner).andExpect(status().isBadRequest());
    call(get(ROOT+"/installments").param("kind","RECEITA").param("size","101"),owner).andExpect(status().isBadRequest());
    call(get(ROOT+"/installments").param("kind","RECEITA").param("from",today().plusDays(1).toString()).param("to",today().toString()),owner).andExpect(status().isBadRequest());
    mvc.perform(get(ROOT+"/installments").param("kind","RECEITA")).andExpect(status().isUnauthorized());
    mvc.perform(body(post(ROOT+"/obligations"),input("RECEITA","50",1,today())).with(authentication(new UsernamePasswordAuthenticationToken(owner,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))).andExpect(status().isForbidden());
  }
  @Test void settledCreationAuditsCreationBeforePayment()throws Exception {
    var v=input("DESPESA","50",1,today());v.put("settled",true);var p=create(v);
    var history=data(call(get(ROOT+"/history/OBRIGACAO/"+p.get("id").asLong()),owner));
    assertThat(history.get(0).get("action").asText()).isEqualTo("CRIACAO");assertThat(history.get(1).get("action").asText()).isEqualTo("LIQUIDACAO");
    assertThat(balance()).isEqualByComparingTo("-50");
  }
  @Test void pendingWithdrawalMustBeSettledInFullAndNeverBecomesAnUnmanagedPartial()throws Exception {
    var payload=Map.of("requestId",UUID.randomUUID().toString(),"kind","RETIRADA","description","Retirada pessoal","amount","100","settled",false);
    var e=data(call(body(post(ROOT+"/entries"),payload),owner).andExpect(status().isCreated()));
    call(body(post(ROOT+"/entries/"+e.get("id").asLong()+"/settle"),payment(e,"40")),owner).andExpect(status().isBadRequest());
    assertThat(balance()).isEqualByComparingTo("0");var paid=pay(e,null);assertThat(paid.get("status").asText()).isEqualTo("LIQUIDADO");assertThat(balance()).isEqualByComparingTo("-100");
  }
  @Test void editingOneRecurringOccurrenceDoesNotRewriteItsNeighborsAndFutureSelectionUsesIdentity()throws Exception {
    var first=today().withDayOfMonth(1).plusMonths(1);var r=recurrence(first,3);long recurrenceId=r.get("id").asLong();
    var rows=occurrences(recurrenceId);var selected=rows.get(1);var change=edit(selected);change.put("description","Ocorrência específica");change.put("amount","85.01");
    change.put("dueDate",first.plusMonths(8).toString());
    call(body(put(ROOT+"/installments/"+selected.get("id").asLong()),change),owner).andExpect(status().isOk());
    assertThat(getInstallment(rows.get(0).get("id").asLong()).get("amount").asText()).isEqualTo("100.00");assertThat(getInstallment(rows.get(2).get("id").asLong()).get("amount").asText()).isEqualTo("100.00");
    r=data(call(get(ROOT+"/recurrences/"+recurrenceId),owner));
    var future=Map.of("version",r.get("version").asLong(),"fromObligationId",selected.get("obligationId").asLong(),"description","Futuras","amount","130",
        "categoryId",category(owner,"DESPESA"),"accountId",main(owner).get("id").asLong(),"dueDay",31);
    var result=data(call(body(put(ROOT+"/recurrences/"+recurrenceId+"/future"),future),owner).andExpect(status().isOk()));
    assertThat(result.get("updatedCount").asInt()).isEqualTo(2);assertThat(getInstallment(rows.get(0).get("id").asLong()).get("amount").asText()).isEqualTo("100.00");
    assertThat(getInstallment(selected.get("id").asLong()).get("dueDate").asText()).isEqualTo(java.time.YearMonth.from(first.plusMonths(1)).atEndOfMonth().toString());
  }
  @Test void databaseConstraintsRejectMissingInstallmentAndOccurrenceNumbers()throws Exception {
    var e=create(input("DESPESA","10",1,today())).get("installments").get(0);
    assertThatThrownBy(()->jdbc.update("update financial_entries set installment_number=null where id=? and company_id=?",e.get("id").asLong(),owner.companyId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    var r=recurrence(today().plusMonths(1),1);
    assertThatThrownBy(()->jdbc.update("update financial_obligations set occurrence_number=null where recurrence_id=? and company_id=?",r.get("id").asLong(),owner.companyId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
  // Exact pre-V5 record shapes/order: no dueDate and no settlement amount.
  record LegacyEntryInput(UUID requestId,com.negociofacil.finance.FinanceTypes.Kind kind,Long accountId,Long categoryId,
      String description,BigDecimal amount,Boolean settled,LocalDate entryDate,LocalDate settlementDate,String notes,Long version) {}
  record LegacySettlementInput(UUID requestId,Long version,LocalDate settlementDate) {}
  private String legacyHash(Object oldRecord)throws Exception {
    return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(oldRecord)));
  }
  @Test void v4CreationAndSettlementHashesRemainReplayableAfterOptionalFieldsAreAdded()throws Exception {
    var oldEntry=new LegacyEntryInput(UUID.randomUUID(),com.negociofacil.finance.FinanceTypes.Kind.RECEITA,main(owner).get("id").asLong(),
        category(owner,"RECEITA"),"Criação com DTO V4",new BigDecimal("125.00"),false,today(),null,null,null);
    var created=data(call(body(post(ROOT+"/entries"),oldEntry),owner).andExpect(status().isCreated()));long id=created.get("id").asLong();
    String entryHash=legacyHash(oldEntry);
    assertThat(jdbc.queryForObject("select request_hash from financial_entries where company_id=? and id=?",String.class,owner.companyId(),id)).isEqualTo(entryHash);
    // Seed the persisted pre-upgrade hash explicitly, as it would survive V5.
    jdbc.update("update financial_entries set request_hash=? where company_id=? and id=?",entryHash,owner.companyId(),id);
    jdbc.update("update financial_obligations set request_hash=? where company_id=? and id=?",entryHash,owner.companyId(),created.get("obligationId").asLong());
    var replay=data(call(body(post(ROOT+"/entries"),oldEntry),owner).andExpect(status().isCreated()));assertThat(replay.get("id").asLong()).isEqualTo(id);
    var oldSettlement=new LegacySettlementInput(UUID.randomUUID(),created.get("version").asLong(),today());
    call(body(post(ROOT+"/entries/"+id+"/settle"),oldSettlement),owner).andExpect(status().isOk());String settlementHash=legacyHash(oldSettlement);
    assertThat(jdbc.queryForObject("select request_hash from financial_settlements where company_id=? and request_id=?",String.class,owner.companyId(),oldSettlement.requestId())).isEqualTo(settlementHash);
    jdbc.update("update financial_settlements set request_hash=? where company_id=? and request_id=?",settlementHash,owner.companyId(),oldSettlement.requestId());
    call(body(post(ROOT+"/entries/"+id+"/settle"),oldSettlement),owner).andExpect(status().isOk());
    assertThat(balance()).isEqualByComparingTo("125");
    assertThat(jdbc.queryForObject("select count(*) from financial_entries where company_id=?",Integer.class,owner.companyId())).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from financial_obligations where company_id=?",Integer.class,owner.companyId())).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from financial_settlements where company_id=?",Integer.class,owner.companyId())).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from financial_movements where company_id=?",Integer.class,owner.companyId())).isEqualTo(1);
  }
  @Test void migrationV4ToV5BackfillsParentsWithoutInventingDueDatesOrChangingCash() {
    String schema="obligations_upgrade_"+UUID.randomUUID().toString().replace("-","");
    try {
      org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("4").load().migrate();
      Long company=jdbc.queryForObject("insert into "+schema+".companies(name,phone,created_at,updated_at) values('Anterior','11999999999',current_timestamp,current_timestamp) returning id",Long.class);
      Long account=jdbc.queryForObject("insert into "+schema+".financial_accounts(company_id,name,type,primary_account,opening_balance,opening_date,created_at,updated_at) values(?,'Principal','BANCO',true,10,current_date,current_timestamp,current_timestamp) returning id",Long.class,company);
      Long category=jdbc.queryForObject("insert into "+schema+".financial_categories(company_id,name,kind,created_at,updated_at) values(?,'Materiais','DESPESA',current_timestamp,current_timestamp) returning id",Long.class,company);
      var entryIds=new ArrayList<Long>();
      for(String status:List.of("PENDENTE","LIQUIDADO"))entryIds.add(jdbc.queryForObject("insert into "+schema+".financial_entries(company_id,account_id,category_id,kind,status,description,amount,entry_date,request_id,request_hash,created_at,updated_at) values(?,?,?,'DESPESA',?,'Legado',20,current_date,?,repeat('a',64),current_timestamp,current_timestamp) returning id",Long.class,company,account,category,status,UUID.randomUUID()));
      Long settlement=jdbc.queryForObject("insert into "+schema+".financial_settlements(company_id,entry_id,account_id,amount,settlement_date,request_id,request_hash,created_at,updated_at) values(?,?,?,20,current_date,?,repeat('b',64),current_timestamp,current_timestamp) returning id",Long.class,company,entryIds.get(1),account,UUID.randomUUID());
      jdbc.update("insert into "+schema+".financial_movements(company_id,account_id,settlement_id,kind,amount,effective_date,description,created_at,updated_at) values(?,?,?,'PAGAMENTO',-20,current_date,'Legado',current_timestamp,current_timestamp)",company,account,settlement);
      org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
      assertThat(jdbc.queryForObject("select count(*) from "+schema+".financial_obligations where source='LEGACY' and installment_count=1",Integer.class)).isEqualTo(2);
      assertThat(jdbc.queryForObject("select count(*) from "+schema+".financial_entries where obligation_id is not null and installment_number=1 and due_date is null",Integer.class)).isEqualTo(2);
      assertThat(jdbc.queryForObject("select sum(amount) from "+schema+".financial_movements",BigDecimal.class)).isEqualByComparingTo("-20");
      assertThat(jdbc.queryForObject("select sum(amount) from "+schema+".financial_settlements where reversed_at is null",BigDecimal.class)).isEqualByComparingTo("20");
    }finally{jdbc.execute("drop schema if exists "+schema+" cascade");}
  }
}
