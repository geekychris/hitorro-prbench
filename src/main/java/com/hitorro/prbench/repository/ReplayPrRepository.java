package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.ReplayPr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Map;

public interface ReplayPrRepository extends JpaRepository<ReplayPr, Long> {
    List<ReplayPr> findByRunId(Long runId);
    List<ReplayPr> findByRunIdAndBotId(Long runId, Long botId);
    List<ReplayPr> findByRunIdAndStatus(Long runId, String status);

    @Query("SELECT r.status AS status, COUNT(r) AS cnt FROM ReplayPr r WHERE r.run.id = :runId GROUP BY r.status")
    List<Object[]> countByRunIdGroupByStatus(Long runId);
}
