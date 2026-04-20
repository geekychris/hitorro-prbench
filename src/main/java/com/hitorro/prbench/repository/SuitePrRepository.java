package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.SuitePr;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SuitePrRepository extends JpaRepository<SuitePr, Long> {
    List<SuitePr> findBySuiteId(Long suiteId);
    Optional<SuitePr> findBySuiteIdAndOriginalPrNumber(Long suiteId, int prNumber);
}
