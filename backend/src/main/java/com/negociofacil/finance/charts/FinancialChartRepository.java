package com.negociofacil.finance.charts;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialChartRepository extends JpaRepository<FinancialChart,Long> {
  List<FinancialChart> findByCompanyIdAndOwnerIdOrderByPositionAscIdAsc(Long companyId,Long ownerId);
  Optional<FinancialChart> findByIdAndCompanyIdAndOwnerId(Long id,Long companyId,Long ownerId);
}
