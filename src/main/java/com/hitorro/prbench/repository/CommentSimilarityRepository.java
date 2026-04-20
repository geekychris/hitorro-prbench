package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.CommentSimilarity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommentSimilarityRepository extends JpaRepository<CommentSimilarity, Long> {
    List<CommentSimilarity> findByCommentAIdAndCommentAType(Long commentId, String type);
}
