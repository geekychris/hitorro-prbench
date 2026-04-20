package com.hitorro.prbench.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "exemplar_repos")
public class ExemplarRepo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "github_url", nullable = false)
    private String githubUrl;

    @Column(nullable = false)
    private String owner;

    @Column(name = "repo_name", nullable = false)
    private String repoName;

    @Column(name = "mirror_org")
    private String mirrorOrg;

    @Column(name = "mirror_repo_name")
    private String mirrorRepoName;

    @Column(name = "mirror_repo_url")
    private String mirrorRepoUrl;

    @Column(name = "default_branch")
    private String defaultBranch = "main";

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "sync_status")
    private String syncStatus = "NEVER";

    @Column(name = "config_json")
    private String configJson;

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

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }
    public String getMirrorOrg() { return mirrorOrg; }
    public void setMirrorOrg(String mirrorOrg) { this.mirrorOrg = mirrorOrg; }
    public String getMirrorRepoName() { return mirrorRepoName; }
    public void setMirrorRepoName(String mirrorRepoName) { this.mirrorRepoName = mirrorRepoName; }
    public String getMirrorRepoUrl() { return mirrorRepoUrl; }
    public void setMirrorRepoUrl(String mirrorRepoUrl) { this.mirrorRepoUrl = mirrorRepoUrl; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
