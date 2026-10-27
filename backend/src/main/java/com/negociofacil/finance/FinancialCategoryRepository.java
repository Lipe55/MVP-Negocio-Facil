package com.negociofacil.finance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialCategoryRepository extends JpaRepository<FinancialCategory, Long> {
  java.util.List<FinancialCategory> findByCompanyIdOrderByKindAscNameAsc(Long companyId);
  java.util.Optional<FinancialCategory> findByIdAndCompanyId(Long id, Long companyId);
}
