package com.negociofacil.finance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, Long> {
  java.util.List<FinancialAccount> findByCompanyIdOrderById(Long companyId);
  java.util.Optional<FinancialAccount> findByIdAndCompanyId(Long id, Long companyId);
  java.util.Optional<FinancialAccount> findByCompanyIdAndPrimaryAccountTrue(Long companyId);
}
