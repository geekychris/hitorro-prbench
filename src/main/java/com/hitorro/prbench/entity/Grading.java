package com.hitorro.prbench.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "gradings")
public class Grading {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "comment_table_type", nullable = false)
    private String commentTableType; // REVIEW, ORIGINAL

    @Column(name = "grader_type", nullable = false)
    private String graderType; // HUMAN, MACHINE

    @Column(name = "grader_id")
    private String graderId;

    private String verdict; // VALID, INVALID, DUPLICATE, NEEDS_REVIEW

    private String severity; // CRITICAL, MAJOR, MINOR, NITPICK

    private int stars;

    private boolean flagged;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public String getCommentTableType() { return commentTableType; }
    public void setCommentTableType(String commentTableType) { this.commentTableType = commentTableType; }
    public String getGraderType() { return graderType; }
    public void setGraderType(String graderType) { this.graderType = graderType; }
    public String getGraderId() { return graderId; }
    public void setGraderId(String graderId) { this.graderId = graderId; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
}
