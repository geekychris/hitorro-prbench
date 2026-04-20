package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.BenchmarkRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {
    List<BenchmarkRun> findBySuiteId(Long suiteId);
    List<BenchmarkRun> findBySuiteIdOrderByCreatedAtDesc(Long suiteId);
    List<BenchmarkRun> findByStatus(String status);
}
