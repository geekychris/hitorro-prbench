package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.BotSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BotSnapshotRepository extends JpaRepository<BotSnapshot, Long> {
    List<BotSnapshot> findByRunId(Long runId);
}
