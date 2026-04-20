package com.hitorro.prbench.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bot_snapshots")
public class BotSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private BenchmarkRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bot_id", nullable = false)
    private Bot bot;

    private String name;

    @Column(name = "workflow_file_name")
    private String workflowFileName;

    @Column(name = "workflow_content", columnDefinition = "CLOB")
    private String workflowContent;

    @Column(name = "wait_strategy")
    private String waitStrategy;

    @Column(name = "timeout_seconds")
    private int timeoutSeconds;

    @Column(name = "snapshotted_at", nullable = false)
    private Instant snapshottedAt;

    @PrePersist
    void prePersist() { if (snapshottedAt == null) snapshottedAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BenchmarkRun getRun() { return run; }
    public void setRun(BenchmarkRun run) { this.run = run; }
    public Bot getBot() { return bot; }
    public void setBot(Bot bot) { this.bot = bot; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getWorkflowFileName() { return workflowFileName; }
    public void setWorkflowFileName(String workflowFileName) { this.workflowFileName = workflowFileName; }
    public String getWorkflowContent() { return workflowContent; }
    public void setWorkflowContent(String workflowContent) { this.workflowContent = workflowContent; }
    public String getWaitStrategy() { return waitStrategy; }
    public void setWaitStrategy(String waitStrategy) { this.waitStrategy = waitStrategy; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Instant getSnapshottedAt() { return snapshottedAt; }
}
