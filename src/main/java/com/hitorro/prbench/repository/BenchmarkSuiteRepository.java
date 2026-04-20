package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.BenchmarkSuite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BenchmarkSuiteRepository extends JpaRepository<BenchmarkSuite, Long> {
    List<BenchmarkSuite> findByExemplarRepoId(Long repoId);
}
