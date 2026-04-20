package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.ReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {
    List<ReviewComment> findByReplayPrId(Long replayPrId);

    @Query("SELECT rc FROM ReviewComment rc WHERE rc.replayPr.run.id = :runId")
    List<ReviewComment> findByRunId(Long runId);

    @Query("SELECT rc FROM ReviewComment rc WHERE rc.replayPr.run.id = :runId AND rc.bot.id = :botId")
    List<ReviewComment> findByRunIdAndBotId(Long runId, Long botId);
}
