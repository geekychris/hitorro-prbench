package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.Grading;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GradingRepository extends JpaRepository<Grading, Long> {
    List<Grading> findByCommentIdAndCommentTableType(Long commentId, String type);
    List<Grading> findByCommentIdInAndCommentTableType(List<Long> commentIds, String type);
}
