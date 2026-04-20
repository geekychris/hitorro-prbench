package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.IssueType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface IssueTypeRepository extends JpaRepository<IssueType, Long> {
    Optional<IssueType> findByCode(String code);
    List<IssueType> findByActiveTrue();

    @Query("SELECT DISTINCT it.category FROM IssueType it WHERE it.category IS NOT NULL ORDER BY it.category")
    List<String> findDistinctCategories();
}
