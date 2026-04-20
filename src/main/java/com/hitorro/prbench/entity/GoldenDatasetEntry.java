package com.hitorro.prbench.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "golden_dataset_entries")
public class GoldenDatasetEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_pr_id", nullable = false)
    private SuitePr suitePr;

    @Column(name = "source_comment_id")
    private Long sourceCommentId;

    @Column(name = "source_comment_type")
    private String sourceCommentType;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "issue_type")
    private String issueType;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(name = "canonical_body", columnDefinition = "CLOB")
    private String canonicalBody;

    private boolean active = true;

    @Column(name = "included_by_default")
    private boolean includedByDefault = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public SuitePr getSuitePr() { return suitePr; }
    public void setSuitePr(SuitePr suitePr) { this.suitePr = suitePr; }
    public Long getSourceCommentId() { return sourceCommentId; }
    public void setSourceCommentId(Long sourceCommentId) { this.sourceCommentId = sourceCommentId; }
    public String getSourceCommentType() { return sourceCommentType; }
    public void setSourceCommentType(String sourceCommentType) { this.sourceCommentType = sourceCommentType; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCanonicalBody() { return canonicalBody; }
    public void setCanonicalBody(String canonicalBody) { this.canonicalBody = canonicalBody; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isIncludedByDefault() { return includedByDefault; }
    public void setIncludedByDefault(boolean includedByDefault) { this.includedByDefault = includedByDefault; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
