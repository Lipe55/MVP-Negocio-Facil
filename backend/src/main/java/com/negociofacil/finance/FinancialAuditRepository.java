package com.negociofacil.finance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialAuditRepository extends JpaRepository<FinancialAudit, Long> {
  java.util.List<FinancialAudit> findByCompanyIdAndResourceAndResourceIdOrderById(Long companyId, String resource, Long resourceId);
}
