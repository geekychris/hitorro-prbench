package com.hitorro.prbench.repository;

import com.hitorro.prbench.entity.OriginalComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OriginalCommentRepository extends JpaRepository<OriginalComment, Long> {
    List<OriginalComment> findBySuitePrId(Long suitePrId);
}
