package com.hitorro.prbench.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bots")
public class Bot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(name = "workflow_file_name")
    private String workflowFileName;

    @Column(name = "workflow_content", columnDefinition = "CLOB")
    private String workflowContent;

    @Column(name = "wait_strategy")
    private String waitStrategy = "BOTH";

    @Column(name = "timeout_seconds")
    private int timeoutSeconds = 600;

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
    public String getWorkflowFileName() { return workflowFileName; }
    public void setWorkflowFileName(String workflowFileName) { this.workflowFileName = workflowFileName; }
    public String getWorkflowContent() { return workflowContent; }
    public void setWorkflowContent(String workflowContent) { this.workflowContent = workflowContent; }
    public String getWaitStrategy() { return waitStrategy; }
    public void setWaitStrategy(String waitStrategy) { this.waitStrategy = waitStrategy; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
