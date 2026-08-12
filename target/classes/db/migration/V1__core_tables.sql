-- Exemplar repositories (original GitHub repos containing PRs to benchmark)
CREATE TABLE exemplar_repos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    github_url VARCHAR(500) NOT NULL,
    owner VARCHAR(255) NOT NULL,
    repo_name VARCHAR(255) NOT NULL,
    mirror_org VARCHAR(255),
    mirror_repo_name VARCHAR(255),
    mirror_repo_url VARCHAR(500),
    default_branch VARCHAR(100) DEFAULT 'main',
    last_synced_at TIMESTAMP,
    sync_status VARCHAR(50) DEFAULT 'NEVER',
    config_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(owner, repo_name)
);

-- Benchmark suites (named sets of PRs)
CREATE TABLE benchmark_suites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    exemplar_repo_id BIGINT NOT NULL,
    pr_selection_config_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exemplar_repo_id) REFERENCES exemplar_repos(id)
);

-- Suite PRs (individual PRs selected into a suite)
CREATE TABLE suite_prs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    suite_id BIGINT NOT NULL,
    original_pr_number INT NOT NULL,
    title VARCHAR(500),
    description CLOB,
    base_commit_sha VARCHAR(40),
    head_commit_sha VARCHAR(40),
    base_branch VARCHAR(255),
    head_branch VARCHAR(255),
    author VARCHAR(255),
    merged_at TIMESTAMP,
    files_changed INT DEFAULT 0,
    additions INT DEFAULT 0,
    deletions INT DEFAULT 0,
    pr_labels VARCHAR(2000),
    metadata_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (suite_id) REFERENCES benchmark_suites(id) ON DELETE CASCADE,
    UNIQUE(suite_id, original_pr_number)
);

-- Bots (AI reviewer definitions)
CREATE TABLE bots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(2000),
    workflow_file_name VARCHAR(255),
    workflow_content CLOB,
    wait_strategy VARCHAR(50) DEFAULT 'BOTH',
    timeout_seconds INT DEFAULT 600,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Benchmark runs
CREATE TABLE benchmark_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    suite_id BIGINT NOT NULL,
    name VARCHAR(255),
    status VARCHAR(50) DEFAULT 'PENDING',
    concurrency INT DEFAULT 2,
    golden_dataset_enabled BOOLEAN DEFAULT TRUE,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message VARCHAR(2000),
    config_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (suite_id) REFERENCES benchmark_suites(id)
);

-- Run-Bot join table
CREATE TABLE run_bots (
    run_id BIGINT NOT NULL,
    bot_id BIGINT NOT NULL,
    PRIMARY KEY (run_id, bot_id),
    FOREIGN KEY (run_id) REFERENCES benchmark_runs(id),
    FOREIGN KEY (bot_id) REFERENCES bots(id)
);

-- Replay PRs (synthetic PRs created in mirror)
CREATE TABLE replay_prs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    suite_pr_id BIGINT NOT NULL,
    bot_id BIGINT NOT NULL,
    mirror_pr_number INT,
    mirror_pr_url VARCHAR(500),
    base_branch VARCHAR(255),
    head_branch VARCHAR(255),
    status VARCHAR(50) DEFAULT 'PENDING',
    error_message VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    FOREIGN KEY (run_id) REFERENCES benchmark_runs(id) ON DELETE CASCADE,
    FOREIGN KEY (suite_pr_id) REFERENCES suite_prs(id),
    FOREIGN KEY (bot_id) REFERENCES bots(id)
);

-- Review comments (from replay PRs)
CREATE TABLE review_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    replay_pr_id BIGINT NOT NULL,
    source VARCHAR(50) NOT NULL,
    bot_id BIGINT,
    github_comment_id BIGINT,
    comment_type VARCHAR(50),
    file_path VARCHAR(500),
    line_number INT,
    diff_hunk CLOB,
    body CLOB,
    body_normalized CLOB,
    fingerprint_hash VARCHAR(100),
    github_created_at TIMESTAMP,
    metadata_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (replay_pr_id) REFERENCES replay_prs(id) ON DELETE CASCADE,
    FOREIGN KEY (bot_id) REFERENCES bots(id)
);

-- Original comments (human comments from exemplar PRs)
CREATE TABLE original_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    suite_pr_id BIGINT NOT NULL,
    author VARCHAR(255),
    github_comment_id BIGINT,
    comment_type VARCHAR(50),
    file_path VARCHAR(500),
    line_number INT,
    diff_hunk CLOB,
    body CLOB,
    body_normalized CLOB,
    fingerprint_hash VARCHAR(100),
    github_created_at TIMESTAMP,
    metadata_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (suite_pr_id) REFERENCES suite_prs(id) ON DELETE CASCADE
);

-- Gradings (human/machine verdicts)
CREATE TABLE gradings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    comment_table_type VARCHAR(50) NOT NULL,
    grader_type VARCHAR(50) NOT NULL,
    grader_id VARCHAR(255),
    verdict VARCHAR(50),
    severity VARCHAR(50),
    stars INT DEFAULT 0,
    flagged BOOLEAN DEFAULT FALSE,
    notes VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Golden dataset entries
CREATE TABLE golden_dataset_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    suite_pr_id BIGINT NOT NULL,
    source_comment_id BIGINT,
    source_comment_type VARCHAR(50),
    file_path VARCHAR(500),
    line_number INT,
    issue_type VARCHAR(100),
    description CLOB,
    canonical_body CLOB,
    active BOOLEAN DEFAULT TRUE,
    included_by_default BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (suite_pr_id) REFERENCES suite_prs(id)
);

-- Comment similarities
CREATE TABLE comment_similarities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comment_a_id BIGINT NOT NULL,
    comment_a_type VARCHAR(50) NOT NULL,
    comment_b_id BIGINT NOT NULL,
    comment_b_type VARCHAR(50) NOT NULL,
    strategy VARCHAR(100) NOT NULL,
    score DOUBLE DEFAULT 0,
    is_match BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
