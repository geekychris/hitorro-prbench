-- Webhook events for idempotency and audit
CREATE TABLE webhook_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    delivery_id VARCHAR(255) UNIQUE,
    event_type VARCHAR(100),
    action VARCHAR(100),
    repo_full_name VARCHAR(500),
    pr_number INT,
    payload CLOB,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bot snapshots (frozen bot config at run start)
CREATE TABLE bot_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    bot_id BIGINT NOT NULL,
    name VARCHAR(255),
    workflow_file_name VARCHAR(255),
    workflow_content CLOB,
    wait_strategy VARCHAR(50),
    timeout_seconds INT,
    snapshotted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (run_id) REFERENCES benchmark_runs(id) ON DELETE CASCADE,
    FOREIGN KEY (bot_id) REFERENCES bots(id),
    UNIQUE(run_id, bot_id)
);

-- Issue types taxonomy
CREATE TABLE issue_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    category VARCHAR(100),
    severity_hint VARCHAR(50),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed common issue types
INSERT INTO issue_types (code, name, category, severity_hint, description) VALUES
('NULL_DEREF', 'Null Dereference', 'BUG', 'CRITICAL', 'Potential null pointer dereference'),
('SQL_INJECTION', 'SQL Injection', 'SECURITY', 'CRITICAL', 'SQL injection vulnerability'),
('XSS', 'Cross-Site Scripting', 'SECURITY', 'CRITICAL', 'XSS vulnerability'),
('RESOURCE_LEAK', 'Resource Leak', 'BUG', 'MAJOR', 'Unclosed resource (file, connection, etc.)'),
('RACE_CONDITION', 'Race Condition', 'CONCURRENCY', 'MAJOR', 'Potential race condition or thread safety issue'),
('ERROR_HANDLING', 'Error Handling', 'BUG', 'MAJOR', 'Missing or incorrect error handling'),
('PERFORMANCE', 'Performance Issue', 'PERFORMANCE', 'MINOR', 'Performance concern or inefficiency'),
('CODE_STYLE', 'Code Style', 'STYLE', 'NITPICK', 'Code style or formatting issue'),
('NAMING', 'Naming Convention', 'STYLE', 'NITPICK', 'Variable/method/class naming issue'),
('DEAD_CODE', 'Dead Code', 'MAINTENANCE', 'MINOR', 'Unreachable or unused code'),
('COMPLEXITY', 'Complexity', 'MAINTENANCE', 'MINOR', 'Overly complex code that should be simplified'),
('DOCUMENTATION', 'Documentation', 'MAINTENANCE', 'NITPICK', 'Missing or incorrect documentation');

-- Scheduled runs
CREATE TABLE scheduled_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    suite_id BIGINT NOT NULL,
    bot_ids VARCHAR(1000),
    cron_expression VARCHAR(100) NOT NULL,
    concurrency INT DEFAULT 2,
    golden_dataset_enabled BOOLEAN DEFAULT TRUE,
    enabled BOOLEAN DEFAULT TRUE,
    last_run_at TIMESTAMP,
    last_run_id BIGINT,
    last_run_status VARCHAR(50),
    next_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (suite_id) REFERENCES benchmark_suites(id)
);
