package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.GoldenDatasetEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GoldenDatasetEntryRepository extends JpaRepository<GoldenDatasetEntry, Long> {
    List<GoldenDatasetEntry> findByActiveTrue();
    List<GoldenDatasetEntry> findBySuitePrId(Long suitePrId);
    List<GoldenDatasetEntry> findBySuitePrSuiteId(Long suiteId);
}
