package com.negociofacil.fiscal;

import static com.negociofacil.fiscal.FiscalDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociofacil.common.ApiException;
import com.negociofacil.common.PageResult;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class FiscalRepository {
  private static final String ROW = """
      n.id,n.number,n.access_key,n.issued_on,n.competence,c.name AS customer_name,
      n.recipient_name,n.description,n.gross,n.net,n.status
      """;
  private static final String FROM = """
      FROM fiscal_invoices n LEFT JOIN customers c ON c.id=n.customer_id AND c.company_id=n.company_id
      WHERE n.company_id=:company
      """;

  record Document(byte[] xml, FiscalXml.Status status) {}

  private final JdbcClient jdbc;
  private final ObjectMapper json;

  FiscalRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  String companyDocument(long company) {
    return jdbc.sql("SELECT coalesce(document,'') FROM companies WHERE id=:company")
        .param("company", company).query(String.class).optional().orElseThrow(ApiException::missing);
  }

  long insert(long company, long user, FiscalXml.Note note, byte[] xml, String hash) {
    return jdbc.sql("""
        INSERT INTO fiscal_invoices(company_id,imported_by,number,access_key,status,issued_on,competence,
          provider_document,provider_name,recipient_document,recipient_name,municipality,description,
          service_code,gross,net,amounts,xml_hash,xml)
        VALUES (:company,:user,:number,:key,:status,:issued,:competence,:providerDocument,:providerName,
          :recipientDocument,:recipientName,:municipality,:description,:serviceCode,:gross,:net,
          CAST(:amounts AS jsonb),:hash,:xml)
        ON CONFLICT DO NOTHING RETURNING id
        """)
        .param("company", company).param("user", user).param("number", note.number())
        .param("key", note.accessKey()).param("status", note.status().name()).param("issued", note.issuedOn())
        .param("competence", note.competence()).param("providerDocument", note.providerDocument())
        .param("providerName", note.providerName()).param("recipientDocument", note.recipientDocument())
        .param("recipientName", note.recipientName()).param("municipality", note.municipality())
        .param("description", note.description()).param("serviceCode", note.serviceCode())
        .param("gross", note.gross()).param("net", note.net()).param("amounts", writeAmounts(note.amounts()))
        .param("hash", hash).param("xml", xml).query(Long.class).optional()
        .orElseThrow(() -> new ApiException(409, "Esta nota fiscal já foi importada."));
  }

  Detail detail(long company, long id) {
    return scoped("SELECT " + ROW + """
        ,n.customer_id,n.quotation_id,n.quotation_item_id,n.obligation_id,n.provider_document,n.provider_name,
        n.recipient_document,n.municipality,n.service_code,n.amounts,n.created_at,n.updated_at,n.imported_by,n.xml_hash
        """ + FROM + " AND n.id=:id", company, id)
        .query((rs, index) -> new Detail(row(rs), new Links(rs.getObject("customer_id", Long.class),
            rs.getObject("quotation_id", Long.class), rs.getObject("quotation_item_id", Long.class),
            rs.getObject("obligation_id", Long.class)), rs.getString("provider_document"),
            rs.getString("provider_name"), rs.getString("recipient_document"), rs.getString("municipality"),
            rs.getString("service_code"), readAmounts(rs.getString("amounts")),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("imported_by"), rs.getString("xml_hash"), "NFSE", "IMPORTADA"))
        .optional().orElseThrow(ApiException::missing);
  }

  PageResult<Row> list(long company, Filter filter, Order order, int page, int size) {
    String sorting = switch (order) {
      case EMISSAO_RECENTE -> "n.issued_on DESC,n.id DESC";
      case EMISSAO_ANTIGA -> "n.issued_on,n.id";
      case MAIOR_VALOR -> "n.gross DESC NULLS LAST,n.id DESC";
    };
    var criteria = new Criteria(company, filter);
    long total = criteria.query("SELECT count(*)").query(Long.class).single();
    var rows = criteria.query("SELECT " + ROW, " ORDER BY " + sorting + " LIMIT :size OFFSET :offset")
        .param("size", size).param("offset", (long) page * size).query((rs, index) -> row(rs)).list();
    return new PageResult<>(rows, total, (int) ((total + size - 1) / size), page, size);
  }

  Summary summary(long company, Filter filter) {
    return new Criteria(company, filter).query("""
        SELECT count(*),coalesce(sum(n.gross),0),
        count(*) FILTER (WHERE n.status='VALIDA'),count(*) FILTER (WHERE n.status='CANCELADA'),
        count(*) FILTER (WHERE n.status='NAO_VERIFICADA'),count(*) FILTER (WHERE n.status='SUBSTITUIDA')
        """).query((rs, index) -> new Summary(rs.getLong(1), rs.getBigDecimal(2), rs.getLong(3),
            rs.getLong(4), rs.getLong(5), rs.getLong(6))).single();
  }

  Document document(long company, long id) {
    return scoped("SELECT xml,status FROM fiscal_invoices WHERE company_id=:company AND id=:id", company, id)
        .query((rs, index) -> new Document(rs.getBytes(1), FiscalXml.Status.valueOf(rs.getString(2))))
        .optional().orElseThrow(ApiException::missing);
  }

  void updateLinks(long company, long id, Links links) {
    int updated = scoped("""
        UPDATE fiscal_invoices SET customer_id=:customer,quotation_id=:quotation,quotation_item_id=:item,
          obligation_id=:obligation,updated_at=now()
        WHERE company_id=:company AND id=:id
        """, company, id).param("customer", links.customerId()).param("quotation", links.quotationId())
        .param("item", links.quotationItemId()).param("obligation", links.obligationId()).update();
    if (updated == 0) throw ApiException.missing();
  }

  List<Option> options(long company, String kind, String search, Long quotation, Long selected) {
    String sql = switch (kind) {
      case "clientes" -> "SELECT id,name AS label FROM customers WHERE company_id=:company";
      case "orcamentos" -> "SELECT id,'Orçamento #' || number AS label FROM quotations WHERE company_id=:company";
      case "servicos" -> """
          SELECT i.id,i.description AS label FROM quotation_items i JOIN quotations q ON q.id=i.quotation_id
          WHERE q.company_id=:company AND q.id=:quotation
          """;
      case "receber" -> """
          SELECT id,description || ' (#' || id || ')' AS label FROM financial_obligations
          WHERE company_id=:company AND kind='RECEITA'
          """;
      default -> throw ApiException.missing();
    };
    var query = jdbc.sql("""
        SELECT id,label FROM (%s) options
        WHERE id=:selected OR strpos(lower(label),lower(:search))>0
        ORDER BY (id IS NOT DISTINCT FROM :selected) DESC,label,id LIMIT 50
        """.formatted(sql)).param("company", company).param("search", search)
        .param("selected", selected, java.sql.Types.BIGINT);
    if ("servicos".equals(kind)) query.param("quotation", quotation);
    return query.query((rs, index) -> new Option(rs.getLong(1), rs.getString(2))).list();
  }

  void audit(long id, long user, String operation) {
    jdbc.sql("INSERT INTO fiscal_audit(invoice_id,actor_id,operation) VALUES (:id,:user,:operation)")
        .param("id", id).param("user", user).param("operation", operation).update();
  }

  private JdbcClient.StatementSpec scoped(String sql, long company, long id) {
    return jdbc.sql(sql).param("company", company).param("id", id);
  }

  private static Row row(ResultSet rs) throws SQLException {
    return new Row(rs.getLong("id"), rs.getString("number"), rs.getString("access_key"),
        rs.getObject("issued_on", LocalDate.class), rs.getObject("competence", LocalDate.class),
        rs.getString("customer_name"), rs.getString("recipient_name"), rs.getString("description"),
        rs.getBigDecimal("gross"), rs.getBigDecimal("net"), FiscalXml.Status.valueOf(rs.getString("status")),
        Origin.IMPORTACAO_MANUAL);
  }

  private String writeAmounts(Map<String, BigDecimal> amounts) {
    try {
      return json.writeValueAsString(amounts);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private Map<String, BigDecimal> readAmounts(String amounts) {
    try {
      return json.readValue(amounts, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private class Criteria {
    private final StringBuilder sql = new StringBuilder(FROM);
    private final Map<String, Object> values = new java.util.LinkedHashMap<>();

    Criteria(long company, Filter filter) {
      values.put("company", company);
      add("from", filter.from(), "n.issued_on>=:from");
      add("to", filter.to(), "n.issued_on<=:to");
      add("cf", filter.competenceFrom(), "n.competence>=:cf");
      add("ct", filter.competenceTo(), "n.competence<=:ct");
      add("customer", filter.customerId(), "n.customer_id=:customer");
      if (filter.status() != null) add("status", filter.status().name(), "n.status=:status");
      if (filter.search() != null && !filter.search().isBlank()) {
        add("search", filter.search().strip(), """
            strpos(lower(concat_ws(' ',n.number,n.access_key,c.name,n.recipient_name,n.description)),lower(:search))>0
            """);
      }
      // All V1 invoices have the only supported origin, IMPORTACAO_MANUAL.
    }

    private void add(String key, Object value, String condition) {
      if (value == null) return;
      sql.append(" AND ").append(condition);
      values.put(key, value);
    }

    JdbcClient.StatementSpec query(String projection) { return query(projection, ""); }

    JdbcClient.StatementSpec query(String projection, String suffix) {
      return jdbc.sql(projection + sql + suffix).params(values);
    }
  }
}
