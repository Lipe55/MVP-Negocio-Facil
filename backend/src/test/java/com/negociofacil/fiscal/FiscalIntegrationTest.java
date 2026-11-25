package com.negociofacil.fiscal;

import static com.negociofacil.fiscal.FiscalFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociofacil.common.ApiException;
import com.negociofacil.security.SessionUser;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/negocio_facil_test}",
    "spring.datasource.username=${DATABASE_USER:negocio_facil}",
    "spring.datasource.password=${DATABASE_PASSWORD}",
    "app.allowed-origin=http://localhost:5173"
})
@AutoConfigureMockMvc
class FiscalIntegrationTest {
  private static final String NOTES = "/api/fiscal/notas";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper mapper;
  @MockitoBean DanfseClient generator;
  SessionUser owner;
  SessionUser other;

  @BeforeEach void setup() {
    assertThat(db.queryForObject("select current_database()", String.class)).endsWith("_test");
    db.execute("TRUNCATE companies,users RESTART IDENTITY CASCADE");
    owner = company("one");
    other = company("two");
  }

  @Test
  void importsAndDownloadsWithoutChangingTheXmlAndRejectsDuplicate() throws Exception {
    byte[] file = complete(1);
    long id = upload(file);
    assertThat(db.queryForObject("SELECT count(*) FROM fiscal_audit WHERE operation='IMPORTACAO'", Long.class)).isEqualTo(1);
    mvc.perform(get(NOTES + "/{id}", id).with(user(owner))).andExpect(status().isOk())
        .andExpect(jsonPath("$.note.gross").value("115.30"))
        .andExpect(jsonPath("$.amounts.vISSQN").value("2.25"))
        .andExpect(jsonPath("$.note.status").value("CANCELADA"));
    mvc.perform(get(NOTES + "/{id}/xml", id).with(user(owner))).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().bytes(file));
    mvc.perform(multipart(NOTES + "/importar").file(part(file)).with(user(owner)).with(csrf()))
        .andExpect(status().isConflict());
    byte[] differentBytes = bytes(new String(file, java.nio.charset.StandardCharsets.UTF_8).replace("115.30", "117.00"));
    mvc.perform(multipart(NOTES + "/importar").file(part(differentBytes)).with(user(owner)).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(multipart(NOTES + "/importar").file(part(file)).with(user(other)).with(csrf()))
        .andExpect(status().isCreated());
  }

  @Test
  void concurrentImportsCreateOneInvoice() throws Exception {
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<Integer> importNote = () -> {
        start.await();
        return mvc.perform(multipart(NOTES + "/importar").file(part(complete(1))).with(user(owner)).with(csrf()))
            .andReturn().getResponse().getStatus();
      };
      var first = workers.submit(importNote);
      var second = workers.submit(importNote);
      start.countDown();
      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
      assertThat(db.queryForObject("SELECT count(*) FROM fiscal_invoices", Long.class)).isEqualTo(1);
    }
  }

  @Test
  void rejectsOversizedAndMismatchedIssuer() throws Exception {
    mvc.perform(multipart(NOTES + "/importar").file(part(new byte[FiscalXml.MAX_BYTES + 1]))
        .with(user(owner)).with(csrf())).andExpect(status().isPayloadTooLarge());
    db.update("UPDATE companies SET document='99999999000199' WHERE id=?", owner.companyId());
    mvc.perform(multipart(NOTES + "/importar").file(part(complete(1))).with(user(owner)).with(csrf()))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void rejectsMissingMultipartContentAndWrongMediaType() throws Exception {
    mvc.perform(multipart(NOTES + "/importar").with(user(owner)).with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(post(NOTES + "/importar").with(user(owner)).with(csrf())
        .contentType("application/xml").content(complete(1))).andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void isolatesReadsAndWritesAndRequiresCsrf() throws Exception {
    long id = upload(complete(1));
    mvc.perform(get(NOTES)).andExpect(status().isUnauthorized());
    mvc.perform(get(NOTES + "/{id}", id).with(user(other))).andExpect(status().isNotFound());
    mvc.perform(get(NOTES + "/{id}/xml", id).with(user(other))).andExpect(status().isNotFound());
    mvc.perform(get(NOTES + "/{id}/danfse", id).with(user(other))).andExpect(status().isNotFound());
    mvc.perform(patch(NOTES + "/{id}/vinculos", id).with(user(other)).with(csrf())
        .contentType("application/json").content("{}")).andExpect(status().isNotFound());
    mvc.perform(patch(NOTES + "/{id}/vinculos", id).with(user(owner))
        .contentType("application/json").content("{}")).andExpect(status().isForbidden());
    mvc.perform(get(NOTES).with(user(other)).param("companyId", owner.companyId().toString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    verifyNoInteractions(generator);
  }

  @Test
  void permitsSameConfiguredOriginToPreflightLinkUpdates() throws Exception {
    mvc.perform(options(NOTES + "/1/vinculos")
        .header("Origin", "http://localhost:5173")
        .header("Access-Control-Request-Method", "PATCH")
        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
        .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  @Test
  void servesPdfAndKeepsOtherOperationsAvailableDuringFailure() throws Exception {
    long id = upload(complete(1));
    byte[] pdf = bytes("%PDF-1.4\n%%EOF");
    when(generator.generate(any(), eq(true))).thenReturn(pdf).thenThrow(new ApiException(503, "Indisponível."));
    mvc.perform(get(NOTES + "/{id}/danfse", id).with(user(owner)).param("download", "true"))
        .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
        .andExpect(header().string("Content-Disposition", "attachment; filename=\"danfse-" + id + ".pdf\""))
        .andExpect(content().bytes(pdf));
    mvc.perform(get(NOTES + "/{id}/danfse", id).with(user(owner))).andExpect(status().isServiceUnavailable());
    mvc.perform(get(NOTES + "/{id}/xml", id).with(user(owner))).andExpect(status().isOk());
    mvc.perform(get(NOTES).with(user(owner))).andExpect(status().isOk());
  }

  @Test
  void filtersAndPaginatesWithStableOrdering() throws Exception {
    upload(complete(1));
    long latest = upload(complete(2));
    upload(xml(3, "<DPS><infDPS/></DPS>"));
    mvc.perform(get(NOTES).with(user(owner)).param("from", "2026-09-01").param("competenceTo", "2026-08-31")
        .param("status", "CANCELADA").param("origin", "IMPORTACAO_MANUAL").param("search", "sintético").param("size", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(latest))
        .andExpect(jsonPath("$.totalPages").value(2));
    mvc.perform(get("/api/fiscal/resumo").with(user(owner))).andExpect(status().isOk())
        .andExpect(jsonPath("$.gross").value("230.60")).andExpect(jsonPath("$.unverified").value(1));
    mvc.perform(get(NOTES).with(user(owner)).param("from", "bad")).andExpect(status().isBadRequest());
  }

  @Test
  void linksOnlyOwnedRecordsAndDoesNotMutateFinancialData() throws Exception {
    long id = upload(complete(1));
    long customer = customer(owner);
    long foreign = customer(other);
    long receivable = receivable(owner);
    long foreignReceivable = receivable(other);
    long quotation = db.queryForObject("""
        INSERT INTO quotations(company_id,customer_id,number,status,valid_until,subtotal,discount,total,created_at,updated_at)
        VALUES (?, ?, 1, 'RASCUNHO', current_date, 10, 0, 10, now(), now()) RETURNING id
        """, Long.class, owner.companyId(), customer);
    long service = db.queryForObject("""
        INSERT INTO quotation_items(quotation_id,description,quantity,unit_price,total)
        VALUES (?, 'Teste', 1, 10, 10) RETURNING id
        """, Long.class, quotation);
    saveLinks(id, new FiscalDtos.Links(foreign, null, null, null)).andExpect(status().isNotFound());
    saveLinks(id, new FiscalDtos.Links(customer, null, null, foreignReceivable)).andExpect(status().isNotFound());
    for (int i = 0; i < 2; i++) {
      saveLinks(id, new FiscalDtos.Links(customer, quotation, service, receivable)).andExpect(status().isOk())
          .andExpect(jsonPath("$.note.customerName").value("Cliente sintético"))
          .andExpect(jsonPath("$.links.obligationId").value(receivable))
          .andExpect(jsonPath("$.links.quotationItemId").value(service));
    }
    mvc.perform(get(NOTES).with(user(owner)).param("customerId", Long.toString(customer)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/fiscal/vinculos/receber").with(user(owner)).param("search", "sintética"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(receivable));
    db.update("DELETE FROM quotation_items WHERE id=?", service);
    mvc.perform(get(NOTES + "/{id}", id).with(user(owner))).andExpect(status().isOk())
        .andExpect(jsonPath("$.links.quotationId").value(quotation))
        .andExpect(jsonPath("$.links.quotationItemId").doesNotExist());
    assertThat(db.queryForObject("SELECT count(*) FROM financial_obligations", Long.class)).isEqualTo(2);
    for (String table : List.of("financial_entries", "financial_settlements", "financial_movements")) {
      assertThat(db.queryForObject("SELECT count(*) FROM " + table, Long.class)).isZero();
    }
  }

  private org.springframework.test.web.servlet.ResultActions saveLinks(long id, FiscalDtos.Links links) throws Exception {
    return mvc.perform(patch(NOTES + "/{id}/vinculos", id).with(user(owner)).with(csrf())
        .contentType("application/json").content(mapper.writeValueAsBytes(links)));
  }

  private long upload(byte[] xml) throws Exception {
    var result = mvc.perform(multipart(NOTES + "/importar").file(part(xml)).with(user(owner)).with(csrf()))
        .andExpect(status().isCreated()).andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray()).path("note").path("id").asLong();
  }

  private static MockMultipartFile part(byte[] bytes) {
    return new MockMultipartFile("file", "synthetic.xml", "application/xml", bytes);
  }

  private SessionUser company(String name) {
    long id = db.queryForObject("""
        INSERT INTO companies(name,document,phone,created_at,updated_at) VALUES (?,?,'0',now(),now()) RETURNING id
        """, Long.class, name, PROVIDER);
    String email = name + "@example.test";
    long user = db.queryForObject("""
        INSERT INTO users(company_id,name,email,password_hash,role,created_at,updated_at)
        VALUES (?,?,?,'unused','ADMIN',now(),now()) RETURNING id
        """, Long.class, id, name, email);
    return new SessionUser(user, id, email);
  }

  private long customer(SessionUser user) {
    return db.queryForObject("""
        INSERT INTO customers(company_id,name,phone,created_at,updated_at)
        VALUES (?,'Cliente sintético','0',now(),now()) RETURNING id
        """, Long.class, user.companyId());
  }

  private long receivable(SessionUser user) {
    return db.queryForObject("""
        INSERT INTO financial_obligations(company_id,kind,description,total_amount,installment_count,source,
          request_id,request_hash,created_at,updated_at)
        VALUES (?,'RECEITA','Conta sintética',115.30,1,'OBLIGATION',gen_random_uuid(),'test',now(),now()) RETURNING id
        """, Long.class, user.companyId());
  }

  private static RequestPostProcessor user(SessionUser user) {
    return authentication(new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }
}
