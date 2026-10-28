package com.negociofacil.finance.charts;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialChartPanelRepository extends JpaRepository<FinancialChartPanel,Long> {
  Optional<FinancialChartPanel> findByCompanyIdAndOwnerId(Long companyId,Long ownerId);
}
