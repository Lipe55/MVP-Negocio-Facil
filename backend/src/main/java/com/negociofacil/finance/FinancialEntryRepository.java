package com.negociofacil.finance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialEntryRepository extends JpaRepository<FinancialEntry, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<FinancialEntry> {
  java.util.Optional<FinancialEntry> findByIdAndCompanyId(Long id, Long companyId);
  java.util.Optional<FinancialEntry> findByCompanyIdAndRequestId(Long companyId, java.util.UUID requestId);
  boolean existsByCompanyIdAndAccountIdAndStatus(Long companyId, Long accountId, FinanceTypes.Status status);
  boolean existsByCompanyIdAndAccountIdAndStatusIn(Long companyId,Long accountId,java.util.Collection<FinanceTypes.Status> status);
  java.util.List<FinancialEntry> findByCompanyIdAndObligationIdOrderByInstallmentNumber(Long companyId,Long obligationId);
}
