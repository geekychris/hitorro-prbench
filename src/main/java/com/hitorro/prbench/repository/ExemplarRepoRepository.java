package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.ExemplarRepo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExemplarRepoRepository extends JpaRepository<ExemplarRepo, Long> {
    Optional<ExemplarRepo> findByOwnerAndRepoName(String owner, String repoName);
}
