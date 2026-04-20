package com.hitorro.prbench.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "benchmark_runs")
public class BenchmarkRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suite_id", nullable = false)
    private BenchmarkSuite suite;

    private String name;

    @Column(nullable = false)
    private String status = "PENDING";

    private int concurrency = 2;

    @Column(name = "golden_dataset_enabled")
    private boolean goldenDatasetEnabled = true;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "config_json")
    private String configJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToMany
    @JoinTable(name = "run_bots",
            joinColumns = @JoinColumn(name = "run_id"),
            inverseJoinColumns = @JoinColumn(name = "bot_id"))
    private List<Bot> bots = new ArrayList<>();

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BenchmarkSuite getSuite() { return suite; }
    public void setSuite(BenchmarkSuite suite) { this.suite = suite; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
    public boolean isGoldenDatasetEnabled() { return goldenDatasetEnabled; }
    public void setGoldenDatasetEnabled(boolean goldenDatasetEnabled) { this.goldenDatasetEnabled = goldenDatasetEnabled; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Bot> getBots() { return bots; }
    public void setBots(List<Bot> bots) { this.bots = bots; }
}
