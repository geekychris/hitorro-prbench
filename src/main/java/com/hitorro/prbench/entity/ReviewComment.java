package com.hitorro.prbench.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "review_comments")
public class ReviewComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replay_pr_id", nullable = false)
    private ReplayPr replayPr;

    @Column(nullable = false)
    private String source; // BOT, ORIGINAL_HUMAN

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_id")
    private Bot bot;

    @Column(name = "github_comment_id")
    private Long githubCommentId;

    @Column(name = "comment_type")
    private String commentType; // REVIEW_COMMENT, PR_COMMENT, REVIEW

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "diff_hunk", columnDefinition = "CLOB")
    private String diffHunk;

    @Column(columnDefinition = "CLOB")
    private String body;

    @Column(name = "body_normalized", columnDefinition = "CLOB")
    private String bodyNormalized;

    @Column(name = "fingerprint_hash")
    private String fingerprintHash;

    @Column(name = "github_created_at")
    private Instant githubCreatedAt;

    @Column(name = "metadata_json")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ReplayPr getReplayPr() { return replayPr; }
    public void setReplayPr(ReplayPr replayPr) { this.replayPr = replayPr; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Bot getBot() { return bot; }
    public void setBot(Bot bot) { this.bot = bot; }
    public Long getGithubCommentId() { return githubCommentId; }
    public void setGithubCommentId(Long githubCommentId) { this.githubCommentId = githubCommentId; }
    public String getCommentType() { return commentType; }
    public void setCommentType(String commentType) { this.commentType = commentType; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    public String getDiffHunk() { return diffHunk; }
    public void setDiffHunk(String diffHunk) { this.diffHunk = diffHunk; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getBodyNormalized() { return bodyNormalized; }
    public void setBodyNormalized(String bodyNormalized) { this.bodyNormalized = bodyNormalized; }
    public String getFingerprintHash() { return fingerprintHash; }
    public void setFingerprintHash(String fingerprintHash) { this.fingerprintHash = fingerprintHash; }
    public Instant getGithubCreatedAt() { return githubCreatedAt; }
    public void setGithubCreatedAt(Instant githubCreatedAt) { this.githubCreatedAt = githubCreatedAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
}
