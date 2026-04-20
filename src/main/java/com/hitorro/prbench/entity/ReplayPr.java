package com.hitorro.prbench.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "replay_prs")
public class ReplayPr {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private BenchmarkRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_pr_id", nullable = false)
    private SuitePr suitePr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_id", nullable = false)
    private Bot bot;

    @Column(name = "mirror_pr_number")
    private Integer mirrorPrNumber;

    @Column(name = "mirror_pr_url")
    private String mirrorPrUrl;

    @Column(name = "base_branch")
    private String baseBranch;

    @Column(name = "head_branch")
    private String headBranch;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BenchmarkRun getRun() { return run; }
    public void setRun(BenchmarkRun run) { this.run = run; }
    public SuitePr getSuitePr() { return suitePr; }
    public void setSuitePr(SuitePr suitePr) { this.suitePr = suitePr; }
    public Bot getBot() { return bot; }
    public void setBot(Bot bot) { this.bot = bot; }
    public Integer getMirrorPrNumber() { return mirrorPrNumber; }
    public void setMirrorPrNumber(Integer mirrorPrNumber) { this.mirrorPrNumber = mirrorPrNumber; }
    public String getMirrorPrUrl() { return mirrorPrUrl; }
    public void setMirrorPrUrl(String mirrorPrUrl) { this.mirrorPrUrl = mirrorPrUrl; }
    public String getBaseBranch() { return baseBranch; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }
    public String getHeadBranch() { return headBranch; }
    public void setHeadBranch(String headBranch) { this.headBranch = headBranch; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
