package com.negociofacil.finance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceBootstrap {
  private final JdbcTemplate jdbc;
  public FinanceBootstrap(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public void initializeCompany(Long companyId) {
    jdbc.update("""
        insert into financial_accounts(company_id,name,type,primary_account,opening_date,created_at,updated_at)
        values (?,'Conta principal','BANCO',true,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
        on conflict do nothing
        """, companyId, FinanceRules.today());
    for (String[] category : new String[][] {
        {"Prestação de serviços", "RECEITA"}, {"Outras receitas", "RECEITA"},
        {"Materiais", "DESPESA"}, {"Transporte", "DESPESA"}, {"Despesas gerais", "DESPESA"}}) {
      jdbc.update("""
          insert into financial_categories(company_id,name,kind,created_at,updated_at)
          values (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) on conflict do nothing
          """, companyId, category[0], category[1]);
    }
  }
}
