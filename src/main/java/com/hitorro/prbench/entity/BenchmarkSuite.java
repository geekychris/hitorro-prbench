package com.hitorro.prbench.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "benchmark_suites")
public class BenchmarkSuite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exemplar_repo_id", nullable = false)
    private ExemplarRepo exemplarRepo;

    @Column(name = "pr_selection_config_json", columnDefinition = "CLOB")
    private String prSelectionConfigJson;

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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ExemplarRepo getExemplarRepo() { return exemplarRepo; }
    public void setExemplarRepo(ExemplarRepo exemplarRepo) { this.exemplarRepo = exemplarRepo; }
    public String getPrSelectionConfigJson() { return prSelectionConfigJson; }
    public void setPrSelectionConfigJson(String prSelectionConfigJson) { this.prSelectionConfigJson = prSelectionConfigJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
