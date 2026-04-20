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

    @Column(name = "github_url", nullable = false, length = 500)
    private String githubUrl;

    @Column(nullable = false)
    private String owner;

    @Column(name = "repo_name", nullable = false)
    private String repoName;

    @Column(name = "mirror_org")
    private String mirrorOrg;

    @Column(name = "mirror_repo_name")
    private String mirrorRepoName;

    @Column(name = "mirror_repo_url", length = 500)
    private String mirrorRepoUrl;

    @Column(name = "default_branch")
    private String defaultBranch = "main";

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "sync_status")
    private String syncStatus = "NEVER";

    @Column(name = "config_json", columnDefinition = "CLOB")
    private String configJson;

    @Column(name = "tags", length = 2000)
    private String tags;

    @Column(name = "notes", columnDefinition = "CLOB")
    private String notes;

    private String language;

    @Column(name = "is_fork")
    private boolean fork;

    @Column(name = "is_private")
    private boolean isPrivate;

    private int stars;

    @Column(name = "github_description", length = 2000)
    private String githubDescription;

    @Column(name = "docs_json", columnDefinition = "CLOB")
    private String docsJson;

    @Column(name = "docs_scanned_at")
    private Instant docsScannedAt;

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
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public boolean isFork() { return fork; }
    public void setFork(boolean fork) { this.fork = fork; }
    public boolean isIsPrivate() { return isPrivate; }
    public void setIsPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
    public String getGithubDescription() { return githubDescription; }
    public void setGithubDescription(String githubDescription) { this.githubDescription = githubDescription; }
    public String getDocsJson() { return docsJson; }
    public void setDocsJson(String docsJson) { this.docsJson = docsJson; }
    public Instant getDocsScannedAt() { return docsScannedAt; }
    public void setDocsScannedAt(Instant docsScannedAt) { this.docsScannedAt = docsScannedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Helper: get tags as list. */
    public java.util.List<String> getTagList() {
        if (tags == null || tags.isBlank()) return java.util.List.of();
        return java.util.Arrays.asList(tags.split(","));
    }

    /** Helper: set tags from list. */
    public void setTagList(java.util.List<String> tagList) {
        this.tags = tagList == null || tagList.isEmpty() ? null : String.join(",", tagList);
    }

    public void addTag(String tag) {
        var list = new java.util.ArrayList<>(getTagList());
        if (!list.contains(tag.trim())) list.add(tag.trim());
        setTagList(list);
    }

    public void removeTag(String tag) {
        var list = new java.util.ArrayList<>(getTagList());
        list.remove(tag.trim());
        setTagList(list);
    }
}
