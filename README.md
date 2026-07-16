# HiTorro PR Bench

A standalone benchmarking platform for evaluating AI code review bots. PR Bench replays real pull requests as synthetic PRs in mirror repositories, triggers AI reviewer workflows, collects their comments, and measures quality against a human-curated golden dataset using precision, recall, and F1 metrics.

Built with Java 21, Spring Boot 3.2, React 18, and H2 (file-based).

## Feature Highlights which are important

**PR Benchmarking Pipeline**
- Define benchmark suites from real GitHub PRs with known review comments
- Register AI review bots with their GitHub Actions workflow definitions
- Replay PRs as synthetic PRs in mirror repos, injecting bot workflows automatically
- Collect all bot-generated comments (inline reviews, PR reviews, issue comments)
- Grade comments as VALID, INVALID, DUPLICATE, or NEEDS_REVIEW -- individually or in bulk
- Build a golden dataset by promoting exemplary comments
- Measure precision/recall/F1 per bot, compare runs with McNemar's significance test, track trends over time

**Repository Management**
- Browse and bulk-import repos from GitHub (personal, org, or by visibility)
- Tag repos locally with automatic sync to GitHub Topics
- AI-generated descriptions via Ollama (scans README, file tree, commits, package manifests)
- Docs scanning across all markdown/rst/adoc files in a repo
- Faceted search and filtering by owner, language, tag, fork status, description
- Markdown report generation with summary tables, doc links, and statistics

## Architecture

### System Overview

```mermaid
graph TB
    subgraph Frontend
        React[React 18 SPA<br/>Vite + TypeScript]
    end

    subgraph Backend["Spring Boot 3.2 (port 8090)"]
        Controllers[REST Controllers]
        Orchestrator[RunOrchestrator]
        Replay[ReplayEngine]
        Collector[CommentCollector]
        Similarity[SimilarityService]
        Reporting[ReportingService]
        Ollama[OllamaService]
        GitTools[hitorro-gittools]
    end

    subgraph Storage
        H2[(H2 File DB<br/>./data/prbench)]
    end

    subgraph External
        GitHub[GitHub API]
        OllamaServer[Ollama LLM<br/>localhost:11434]
        Mirror[Mirror Repos<br/>on GitHub]
    end

    React -->|REST API| Controllers
    Controllers --> Orchestrator
    Orchestrator --> Replay
    Orchestrator --> Collector
    Controllers --> Similarity
    Controllers --> Reporting
    Controllers --> Ollama
    Replay --> GitTools
    Replay --> GitHub
    Collector --> GitHub
    Ollama --> OllamaServer
    GitTools -->|clone/push/branch| Mirror
    Controllers --> H2
```

### Benchmark Run Flow

```mermaid
sequenceDiagram
    participant UI as React UI
    participant API as RunController
    participant Orch as RunOrchestrator
    participant RE as ReplayEngine
    participant GH as GitHub API
    participant CC as CommentCollector

    UI->>API: POST /api/runs (suiteId, botIds, concurrency)
    API->>Orch: executeRun(run) [async]
    API-->>UI: 200 OK (run created)

    Note over Orch: Snapshot bot configs

    loop For each SuitePr x Bot (semaphore-controlled)
        Orch->>RE: replay(suitePr, bot, repo)
        RE->>RE: Clone/fetch mirror repo
        RE->>RE: Create base branch at base SHA
        RE->>RE: Create head branch at head SHA
        RE->>RE: Inject bot workflow file + commit
        RE->>GH: Push branches, create PR
        GH-->>RE: PR number + URL
        RE-->>Orch: ReplayResult

        Note over Orch: Poll for bot completion

        loop Until checks/reviews complete or timeout
            Orch->>GH: Check run status / list reviews
        end

        Orch->>CC: collectReplayPrComments(replayPr)
        CC->>GH: List review comments, reviews, issue comments
        CC->>CC: Normalize text, compute Winnowing hashes
        CC-->>Orch: Comments saved
    end

    Orch->>Orch: Mark run COMPLETED

    UI->>API: GET /api/runs/{id}/progress
    API-->>UI: Status counts per replay PR
```

### Repository Management Components

```mermaid
graph LR
    subgraph "Repo Management"
        Import[Import from GitHub]
        Tags[Tag Management]
        Desc[Description Management]
        Docs[Docs Scanner]
        Report[Report Generator]
    end

    subgraph "External"
        GH[GitHub API]
        LLM[Ollama LLM]
    end

    Import -->|Browse user/org repos| GH
    Tags -->|Sync as Topics| GH
    Desc -->|Push description max 350 chars| GH
    Desc -->|Generate via scan| LLM
    Docs -->|Git tree API recursive| GH
    Report -->|Markdown with tables| Output[Markdown Output]

    LLM -.->|README + file tree + commits + manifest| GH
```

### Database Schema (Key Tables)

```mermaid
erDiagram
    exemplar_repos ||--o{ benchmark_suites : has
    benchmark_suites ||--o{ suite_prs : contains
    benchmark_suites ||--o{ benchmark_runs : has
    benchmark_runs }o--o{ bots : uses
    benchmark_runs ||--o{ replay_prs : creates
    benchmark_runs ||--o{ bot_snapshots : freezes
    replay_prs }o--|| suite_prs : replays
    replay_prs }o--|| bots : uses
    replay_prs ||--o{ review_comments : has
    suite_prs ||--o{ original_comments : has
    suite_prs ||--o{ golden_dataset_entries : has
    review_comments ||--o{ gradings : graded_by
    review_comments ||--o{ comment_similarities : compared_in
    original_comments ||--o{ comment_similarities : compared_in

    exemplar_repos {
        bigint id PK
        varchar name
        varchar github_url
        varchar owner
        varchar repo_name
        varchar mirror_org
        varchar mirror_repo_name
        varchar default_branch
    }

    benchmark_suites {
        bigint id PK
        varchar name
        bigint exemplar_repo_id FK
    }

    suite_prs {
        bigint id PK
        bigint suite_id FK
        int original_pr_number
        varchar base_commit_sha
        varchar head_commit_sha
        int files_changed
    }

    bots {
        bigint id PK
        varchar name
        varchar workflow_file_name
        clob workflow_content
        varchar wait_strategy
        int timeout_seconds
    }

    benchmark_runs {
        bigint id PK
        bigint suite_id FK
        varchar status
        int concurrency
        boolean golden_dataset_enabled
    }

    replay_prs {
        bigint id PK
        bigint run_id FK
        bigint suite_pr_id FK
        bigint bot_id FK
        int mirror_pr_number
        varchar status
    }

    review_comments {
        bigint id PK
        bigint replay_pr_id FK
        varchar source
        varchar comment_type
        varchar file_path
        int line_number
        clob body_normalized
        varchar fingerprint_hash
    }

    golden_dataset_entries {
        bigint id PK
        bigint suite_pr_id FK
        varchar file_path
        int line_number
        varchar issue_type
        clob canonical_body
        boolean active
    }

    gradings {
        bigint id PK
        bigint comment_id
        varchar verdict
        varchar severity
        int stars
    }

    comment_similarities {
        bigint id PK
        varchar strategy
        double score
        boolean is_match
    }
```

## Getting Started

### Prerequisites

- **Java 21** (JDK)
- **Maven 3.8+**
- **Node.js 18+** and npm (for the React frontend)
- **GitHub Personal Access Token** with `repo` scope
- **Ollama** (optional, for AI-generated descriptions) -- install from [ollama.com](https://ollama.com)
- **hitorro-gittools 3.0.0** in your local Maven repository

### Build

```bash
# Build the backend
mvn clean package -DskipTests

# Install frontend dependencies
cd react-app && npm install
```

### Run

The included `run.sh` starts both the backend and frontend dev server:

```bash
# Set your GitHub token
export GITHUB_TOKEN=ghp_your_token_here

# Start both servers
./run.sh
```

This starts:
- **Backend API** at `http://localhost:8090`
- **React dev server** at `http://localhost:3001`
- **Swagger UI** at `http://localhost:8090/swagger-ui.html`
- **H2 Console** at `http://localhost:8090/h2-console`

Alternatively, run each component separately:

```bash
# Backend only
java -jar target/hitorro-pr-bench-1.0.0.jar

# Frontend only (in react-app/)
npm run dev
```

## Configuration

### application.yml

| Property | Default | Description |
|:---------|:--------|:------------|
| `server.port` | `8090` | Backend HTTP port |
| `spring.datasource.url` | `jdbc:h2:file:./data/prbench` | H2 database file path |
| `app.github.token` | `${GITHUB_TOKEN:}` | GitHub PAT (env var or direct) |
| `app.github.api-url` | `https://api.github.com` | GitHub API base URL |
| `app.github.poll-interval-seconds` | `30` | Interval for polling bot completion |
| `app.github.default-bot-timeout-seconds` | `600` | Default timeout waiting for a bot |
| `app.workspace.base-path` | `~/.pr-bench/workspaces` | Local directory for git clones |
| `app.run.default-concurrency` | `2` | Default parallel replay PRs per run |
| `app.run.max-concurrency` | `10` | Maximum allowed concurrency |
| `app.similarity.text-similarity-threshold` | `0.8` | Jaro-Winkler threshold for a match |
| `app.similarity.winnowing-k` | `5` | Winnowing k-gram size |
| `app.similarity.winnowing-w` | `4` | Winnowing window size |
| `app.ollama.url` | `http://localhost:11434` | Ollama server URL |
| `app.ollama.model` | `llama3.2` | Ollama model for description generation |

### GitHub Token

Set via environment variable (recommended) or directly in `application.yml`:

```bash
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

The token needs `repo` scope for reading/writing repositories, creating PRs, and managing topics.

### Ollama (Optional)

For AI-generated repository descriptions:

```bash
# Install Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Pull the default model
ollama pull llama3.2

# Ollama runs on localhost:11434 by default -- no further config needed
```

## Feature Walkthrough

### Repository Import and Management

1. **Browse GitHub** -- navigate to Repositories, click "Browse GitHub" to see all repos accessible via your token (personal and org)
2. **Import** -- import individual repos or use "Import All" for bulk import
3. **Tag** -- add tags to repos; tags auto-sync to GitHub as Topics (visible on the repo page under "About")
4. **Describe** -- write descriptions manually or click "Generate Description" to have Ollama scan the repo (README, file tree, commits, package manifest) and produce a summary
5. **Scan Docs** -- discovers all markdown, rst, and adoc files in the repo tree
6. **Report** -- generate a markdown report with summary table, per-repo details, doc links, and statistics

### Benchmark Suite Setup

1. **Create Exemplar Repo** -- register the GitHub repo containing the PRs you want to benchmark
2. **Create Suite** -- name a benchmark suite and associate it with the exemplar repo
3. **Add PRs** -- select merged PRs to include; the system records base/head SHAs, changed files, and metadata
4. **Collect Original Comments** -- fetch human review comments from the original PRs for comparison

### Bot Configuration

1. **Create Bot** -- provide a name, GitHub Actions workflow YAML, and a wait strategy
2. **Wait Strategies** -- `CHECKS` (wait for check runs to complete), `REVIEWS` (wait for a review to appear), or `BOTH` (wait for both)
3. **Timeout** -- how long to wait before giving up (default 600 seconds)

### Running a Benchmark

1. **Create Run** -- select a suite, pick bots, set concurrency (max 10 parallel)
2. **Execution** -- the orchestrator creates synthetic PRs in the mirror repo, injects each bot's workflow, pushes, opens PRs, then polls for completion
3. **Monitor** -- the progress endpoint shows status counts (PENDING, CREATING_BRANCHES, WAITING_FOR_BOTS, COLLECTING_COMMENTS, COMPLETED, FAILED)
4. **Cleanup** -- after analysis, clean up mirror branches and close synthetic PRs

### Grading and Golden Dataset

1. **Grading Queue** -- review ungraded bot comments one by one or in bulk
2. **Verdicts** -- VALID (real issue found), INVALID (false positive), DUPLICATE, NEEDS_REVIEW
3. **Severity and Stars** -- rate comment quality with severity levels and star ratings
4. **Promote to Golden** -- promote validated comments to the golden dataset as ground truth
5. **Export/Import** -- export golden dataset entries for sharing or backup

### Similarity Analysis

Four strategies compare bot comments against original human comments:

| Strategy | Description |
|:---------|:------------|
| EXACT_MATCH | Normalized text is identical |
| FILE_LINE | Same file path and line number |
| NORMALIZED_TEXT | Jaro-Winkler similarity above threshold (default 0.8) |
| WINNOWING | Jaccard similarity of Winnowing fingerprint hash sets |

Text normalization strips markdown, URLs, punctuation, and lowercases before comparison.

### Reporting

- **Run Report** -- per-bot totals, verdict breakdowns, grading stats
- **Golden Comparison** -- precision, recall, F1 per bot against the golden dataset
- **Significance Test** -- McNemar's chi-squared test (with continuity correction) between two runs
- **Trend Charts** -- F1/precision/recall over time for a bot (rendered with Recharts)

## API Reference

### Setup (`/api/setup`)

| Method | Path | Description |
|:-------|:-----|:------------|
| GET | `/status` | GitHub token status and connectivity |
| POST | `/token` | Set GitHub token at runtime |
| GET | `/rate-limit` | Current GitHub API rate limit |

### Repositories (`/api/repos`)

| Method | Path | Description |
|:-------|:-----|:------------|
| GET | `/` | List repos (filter: tag, search, language, owner, hasNotes, isFork) |
| POST | `/` | Create repo by GitHub URL |
| POST | `/import` | Import single repo from GitHub |
| POST | `/import-all` | Bulk import repos |
| GET | `/{id}` | Get repo by ID |
| PUT | `/{id}` | Update repo |
| DELETE | `/{id}` | Delete repo |
| GET | `/{id}/github-status` | Compare local vs live GitHub state |
| POST | `/{id}/sync-to-github` | Push description + tags to GitHub |
| POST | `/{id}/tags` | Add tag (syncs to GitHub Topics) |
| DELETE | `/{id}/tags/{tag}` | Remove tag |
| POST | `/bulk-tag` | Add tag to multiple repos |
| GET | `/meta/tags` | List all tags |
| GET | `/meta/stats` | Faceted stats (by owner, language, tag, fork) |
| POST | `/{id}/notes` | Set description (pushes to GitHub, max 350 chars) |
| POST | `/{id}/generate-description` | AI-generate description via Ollama |
| POST | `/meta/generate-descriptions` | Bulk AI-generate for all repos without descriptions |
| GET | `/github/browse` | Browse GitHub repos accessible via token |
| GET | `/github/orgs` | List user's GitHub organizations |
| GET | `/github/orgs/{org}/repos` | List repos in an organization |
| GET | `/{id}/prs` | List PRs from GitHub for a repo |
| POST | `/{id}/scan-docs` | Scan repo for documentation files |
| POST | `/meta/scan-docs` | Bulk scan repos for docs |
| GET | `/{id}/docs` | Get scanned docs for a repo |
| POST | `/meta/report` | Generate markdown report for selected repos |

### Benchmark Suites (`/api/suites`)

| Method | Path | Description |
|:-------|:-----|:------------|
| GET | `/` | List suites (filter: repoId) |
| POST | `/` | Create suite |
| GET | `/{id}` | Get suite |
| DELETE | `/{id}` | Delete suite |
| GET | `/{id}/prs` | List PRs in suite |
| POST | `/{id}/prs` | Add PR to suite |
| DELETE | `/{suiteId}/prs/{prId}` | Remove PR from suite |
| POST | `/suite-prs/{id}/collect-original-comments` | Collect human comments from original PR |

### Bots (`/api/bots`)

| Method | Path | Description |
|:-------|:-----|:------------|
| GET | `/` | List all bots |
| POST | `/` | Create bot (name, workflow YAML, wait strategy, timeout) |
| GET | `/{id}` | Get bot |
| PUT | `/{id}` | Update bot |
| DELETE | `/{id}` | Delete bot |

### Runs (`/api/runs`)

| Method | Path | Description |
|:-------|:-----|:------------|
| GET | `/` | List runs (filter: suiteId) |
| POST | `/` | Create and start run (suiteId, botIds, concurrency) |
| GET | `/{id}` | Get run details |
| GET | `/{id}/progress` | Status counts per replay PR |
| POST | `/{id}/cancel` | Cancel a running benchmark |
| GET | `/{id}/replay-prs` | List replay PRs for run |
| GET | `/{id}/bot-snapshots` | Bot config snapshots taken at run start |
| POST | `/{id}/cleanup` | Close mirror PRs and delete branches |
| GET | `/replay-prs/{id}/comments` | Comments collected for a replay PR |
| GET | `/{runId}/similarities` | Compute pairwise similarity analysis |

### Grading (`/api`)

| Method | Path | Description |
|:-------|:-----|:------------|
| POST | `/gradings` | Create grading (verdict, severity, stars, notes) |
| PUT | `/gradings/{id}` | Update grading |
| DELETE | `/gradings/{id}` | Delete grading |
| GET | `/comments/{id}/gradings` | Get gradings for a comment |
| POST | `/gradings/bulk` | Bulk grade multiple comments |
| GET | `/grading-queue` | Ungraded comments queue |
| GET | `/grading-progress` | Grading completion stats |

### Golden Dataset (`/api/golden-dataset`)

| Method | Path | Description |
|:-------|:-----|:------------|
| GET | `/` | List entries (filter: suiteId) |
| POST | `/promote` | Promote a comment to golden dataset |
| PUT | `/{id}` | Update entry |
| DELETE | `/{id}` | Remove entry |
| GET | `/export` | Export golden dataset as JSON |

### Reports (`/api/reports`)

| Method | Path | Description |
|:-------|:-----|:------------|
| GET | `/runs/{runId}` | Run report with per-bot stats |
| GET | `/runs/{runId}/comparison` | Compare against golden dataset (P/R/F1) |
| GET | `/bots/{botId}/trend` | F1 trend over recent runs |
| GET | `/runs/{runAId}/significance` | McNemar's test between two runs |

### Issue Types (`/api/issue-types`)

| Method | Path | Description |
|:-------|:-----|:------------|
| GET | `/` | List all issue types |
| GET | `/categories` | List issue type categories |
| GET | `/{id}` | Get issue type |
| GET | `/code/{code}` | Get issue type by code |
| POST | `/` | Create issue type |
| PUT | `/{id}` | Update issue type |
| DELETE | `/{id}` | Delete issue type |

Pre-seeded issue types: NULL_DEREF, SQL_INJECTION, XSS, RESOURCE_LEAK, RACE_CONDITION, ERROR_HANDLING, PERFORMANCE, CODE_STYLE, NAMING, DEAD_CODE, COMPLEXITY, DOCUMENTATION.

## Frontend Pages

The React SPA provides these pages via sidebar navigation:

| Page | Route | Description |
|:-----|:------|:------------|
| Dashboard | `/` | Overview stats and recent activity |
| Repositories | `/repos` | Browse, import, tag, describe, and manage repos |
| Report & Docs | `/repo-report` | Generate markdown reports and browse scanned docs |
| Suites | `/suites` | Create and manage benchmark suites |
| Suite Detail | `/suites/:id` | View/add PRs in a suite, collect original comments |
| Bots | `/bots` | Define AI review bots with workflow YAML |
| Runs | `/runs` | Start benchmark runs, view status |
| Run Detail | `/runs/:id` | Monitor replay PRs, view progress, trigger cleanup |
| Replay PR Detail | `/replay-prs/:id` | View collected comments for a single replay PR |
| Golden Dataset | `/golden-dataset` | Curate ground-truth entries, export/import |
| Grading Queue | `/grading-queue` | Grade bot comments (VALID/INVALID/DUPLICATE) |
| Run Report | `/reports/:runId` | Per-bot stats, verdict breakdowns, golden comparison |
| Trends | `/trend` | F1/precision/recall charts over time (Recharts) |
| Issue Types | `/issue-types` | Manage the issue type taxonomy |
| Settings | `/settings` | GitHub token, Ollama status, app config |

**Tech stack:** React 18, TypeScript, Vite 5, TanStack Query 5, Recharts 2, React Router 6.

## Integration with hitorro-gittools

The `ReplayEngine` depends on `hitorro-gittools` (v3.0.0) for all git operations during PR replay:

- **Clone** -- clones the mirror repository to the local workspace (`~/.pr-bench/workspaces/{org}/{repo}`)
- **Fetch** -- fetches latest refs before creating branches
- **Branch creation** -- creates base and head branches at the exact commit SHAs from the original PR
- **Checkout** -- switches between branches during replay
- **Push** -- pushes base and head branches to the mirror remote
- **Raw git commands** -- uses `gitService.getRunner().runOrThrow()` to stage and commit injected workflow files

The `GitService` and `GitCredentials` classes from hitorro-gittools handle authentication using the GitHub PAT.

## Testing

```bash
# Run all tests
mvn test

# Run with verbose output
mvn test -Dtest.verbose=true
```

The project uses:
- **JUnit 5** (Jupiter) via `spring-boot-starter-test`
- **Spring Boot Test** for integration testing with auto-configured H2
- **Flyway** migrations run automatically in test context

## Project Structure

```
hitorro-pr-bench/
|-- pom.xml                              # Maven build (Spring Boot 3.2 parent)
|-- run.sh                               # Start backend + frontend
|-- src/
|   |-- main/
|   |   |-- java/com/hitorro/prbench/
|   |   |   |-- PrBenchApplication.java  # Entry point (@EnableAsync, @EnableScheduling)
|   |   |   |-- controller/
|   |   |   |   |-- RepoController.java          # Repository management + GitHub browsing
|   |   |   |   |-- SuiteController.java          # Benchmark suite CRUD + PR selection
|   |   |   |   |-- BotController.java            # AI bot definitions
|   |   |   |   |-- RunController.java            # Benchmark run lifecycle
|   |   |   |   |-- GradingController.java        # Comment grading + queue
|   |   |   |   |-- GoldenDatasetController.java  # Golden dataset management
|   |   |   |   |-- ReportController.java         # Reporting endpoints
|   |   |   |   |-- IssueTypeController.java      # Issue type taxonomy
|   |   |   |   |-- SetupController.java          # Token + connectivity setup
|   |   |   |-- service/
|   |   |   |   |-- RunOrchestrator.java   # Async run execution with semaphore concurrency
|   |   |   |   |-- ReplayEngine.java      # Git-based PR replay via hitorro-gittools
|   |   |   |   |-- CommentCollector.java  # GitHub comment fetching + normalization
|   |   |   |   |-- SimilarityService.java # Pairwise comment comparison (4 strategies)
|   |   |   |   |-- ReportingService.java  # P/R/F1, McNemar's test, trend data
|   |   |   |   |-- OllamaService.java     # LLM description generation
|   |   |   |   |-- GitHubApiService.java  # GitHub REST API client
|   |   |   |   |-- TextNormalizer.java    # Text normalization + Winnowing + Jaro-Winkler
|   |   |   |-- entity/                    # JPA entities (13 tables)
|   |   |   |-- repository/               # Spring Data JPA repositories
|   |   |-- resources/
|   |       |-- application.yml            # App configuration
|   |       |-- db/migration/
|   |           |-- V1__core_tables.sql    # Core schema (12 tables)
|   |           |-- V2__webhook_events.sql # Webhooks, bot snapshots, issue types, schedules
|   |-- test/
|-- react-app/
|   |-- package.json                       # React 18 + Vite 5 + TanStack Query
|   |-- src/
|   |   |-- App.tsx                        # Router + sidebar navigation
|   |   |-- pages/                         # 15 page components
|-- data/
|   |-- prbench.mv.db                     # H2 database file (created at runtime)
```
