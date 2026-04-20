package com.hitorro.prbench.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "suite_prs")
public class SuitePr {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_id", nullable = false)
    private BenchmarkSuite suite;

    @Column(name = "original_pr_number", nullable = false)
    private int originalPrNumber;

    private String title;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(name = "base_commit_sha")
    private String baseCommitSha;

    @Column(name = "head_commit_sha")
    private String headCommitSha;

    @Column(name = "base_branch")
    private String baseBranch;

    @Column(name = "head_branch")
    private String headBranch;

    private String author;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "files_changed")
    private int filesChanged;

    private int additions;
    private int deletions;

    @Column(name = "pr_labels")
    private String prLabels;

    @Column(name = "metadata_json")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BenchmarkSuite getSuite() { return suite; }
    public void setSuite(BenchmarkSuite suite) { this.suite = suite; }
    public int getOriginalPrNumber() { return originalPrNumber; }
    public void setOriginalPrNumber(int originalPrNumber) { this.originalPrNumber = originalPrNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBaseCommitSha() { return baseCommitSha; }
    public void setBaseCommitSha(String baseCommitSha) { this.baseCommitSha = baseCommitSha; }
    public String getHeadCommitSha() { return headCommitSha; }
    public void setHeadCommitSha(String headCommitSha) { this.headCommitSha = headCommitSha; }
    public String getBaseBranch() { return baseBranch; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }
    public String getHeadBranch() { return headBranch; }
    public void setHeadBranch(String headBranch) { this.headBranch = headBranch; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public Instant getMergedAt() { return mergedAt; }
    public void setMergedAt(Instant mergedAt) { this.mergedAt = mergedAt; }
    public int getFilesChanged() { return filesChanged; }
    public void setFilesChanged(int filesChanged) { this.filesChanged = filesChanged; }
    public int getAdditions() { return additions; }
    public void setAdditions(int additions) { this.additions = additions; }
    public int getDeletions() { return deletions; }
    public void setDeletions(int deletions) { this.deletions = deletions; }
    public String getPrLabels() { return prLabels; }
    public void setPrLabels(String prLabels) { this.prLabels = prLabels; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
}
