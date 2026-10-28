package com.negociofacil.finance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialSettlementRepository extends JpaRepository<FinancialSettlement, Long> {
  java.util.Optional<FinancialSettlement> findByCompanyIdAndRequestId(Long companyId, java.util.UUID requestId);
  java.util.List<FinancialSettlement> findByCompanyIdAndEntryIdAndReversedAtIsNullOrderById(Long companyId, Long entryId);
  java.util.List<FinancialSettlement> findByCompanyIdAndEntryIdOrderById(Long companyId,Long entryId);
  java.util.Optional<FinancialSettlement> findByIdAndCompanyIdAndEntryId(Long id,Long companyId,Long entryId);
}
