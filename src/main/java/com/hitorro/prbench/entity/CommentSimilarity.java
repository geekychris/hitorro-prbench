package com.hitorro.prbench.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "comment_similarities")
public class CommentSimilarity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_a_id", nullable = false)
    private Long commentAId;

    @Column(name = "comment_a_type", nullable = false)
    private String commentAType;

    @Column(name = "comment_b_id", nullable = false)
    private Long commentBId;

    @Column(name = "comment_b_type", nullable = false)
    private String commentBType;

    @Column(nullable = false)
    private String strategy;

    private double score;

    @Column(name = "is_match")
    private boolean match;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCommentAId() { return commentAId; }
    public void setCommentAId(Long commentAId) { this.commentAId = commentAId; }
    public String getCommentAType() { return commentAType; }
    public void setCommentAType(String commentAType) { this.commentAType = commentAType; }
    public Long getCommentBId() { return commentBId; }
    public void setCommentBId(Long commentBId) { this.commentBId = commentBId; }
    public String getCommentBType() { return commentBType; }
    public void setCommentBType(String commentBType) { this.commentBType = commentBType; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public boolean isMatch() { return match; }
    public void setMatch(boolean match) { this.match = match; }
    public Instant getCreatedAt() { return createdAt; }
}
