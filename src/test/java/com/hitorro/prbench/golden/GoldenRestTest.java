package com.hitorro.prbench.golden;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Golden REST test: every controller's JSON output, the JSON the app stores (database CLOB columns,
 * {@code data/prbench-config.json}) and the JSON it sends to GitHub must be exactly what the app produced
 * on Jackson 2 (Spring Boot 4.1.3 with the spring-boot-jackson2 bridge, main before the migration).
 *
 * <p>{@code src/test/resources/golden/jackson2-rest.txt} was recorded by running this same class on main
 * with {@code -Dgolden.record=true}. GitHub is a local stub ({@link GitHubStub}) whose received requests are
 * recorded too; Ollama points at a closed port; the database is an in-memory H2 migrated by Flyway and
 * seeded with fixed rows for runs, replay PRs, review comments and snapshots (runs are not started: that
 * clones repositories).
 *
 * <p>Controllers covered: SetupController, IssueTypeController, BotController, RepoController,
 * SuiteController, RunController, GradingController, GoldenDatasetController, ReportController, plus the
 * actuator health endpoint, the OpenAPI document and a probe of the MVC JSON converter.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prbench-golden;DB_CLOSE_DELAY=-1",
        "app.github.token=golden-token",
        "app.ollama.url=http://127.0.0.1:9",
        "app.workspace.base-path=target/golden-workspaces",
        "logging.level.com.hitorro.prbench=INFO"
})
@AutoConfigureMockMvc
@DisplayName("Golden REST responses (recorded on Jackson 2)")
class GoldenRestTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden/jackson2-rest.txt");
    private static final Path CONFIG = Path.of("data/prbench-config.json");
    private static GitHubStub github;
    private static byte[] savedConfig;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RequestMappingHandlerAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    private GoldenRecorder g;

    @DynamicPropertySource
    static void githubUrl(DynamicPropertyRegistry r) throws IOException {
        if (github == null) github = new GitHubStub();
        r.add("app.github.api-url", github::url);
    }

    @BeforeAll
    static void saveConfig() throws IOException {
        // POST /api/setup/token rewrites this committed file: put it back afterwards
        savedConfig = Files.exists(CONFIG) ? Files.readAllBytes(CONFIG) : null;
    }

    @AfterAll
    static void restore() throws IOException {
        if (savedConfig != null) Files.write(CONFIG, savedConfig);
        else Files.deleteIfExists(CONFIG);
        if (github != null) github.close();
    }

    @Test
    @DisplayName("every endpoint returns the Jackson 2 JSON")
    void endpointsMatchJackson2() throws Exception {
        g = new GoldenRecorder(GOLDEN)
                // timestamps the app sets with Instant.now(); seeded rows use 2020 and stay visible
                .normalizer(s -> s.replaceAll("(\"[A-Za-z_]*(At|_at)\"\\s*:\\s*)\"(?!2020-)[0-9][^\"]*\"", "$1\"~\""))
                .normalizer(s -> s.replaceAll("Generated: [0-9T:.Z-]+", "Generated: ~"))
                .normalizer(s -> s.replaceAll("Run [0-9]{13}", "Run ~"))
                .replace(github.url(), "<GITHUB>");
        github.drain();

        setup();
        issueTypes();
        bots();
        repos();
        suites();
        seedRun();
        runs();
        gradings();
        goldenDataset();
        reports();
        cleanup();
        g.rest(mvc, "actuator health", get("/actuator/health"));
        g.restUnordered(mvc, "openapi document", get("/api-docs"));
        storedJson();
        ConverterProbe.run(ConverterProbe.jsonConverter(adapter), g);

        List<String> diffs = g.finish();
        assertTrue(diffs.isEmpty(), diffs.size() + " responses differ from Jackson 2:\n" + String.join("\n", diffs));
    }

    // ------------------------------------------------------------------ helpers

    /** A request, then what the app sent to GitHub while handling it. */
    private void step(String name, MockHttpServletRequestBuilder req) throws Exception {
        step(name, req, false);
    }

    private void step(String name, MockHttpServletRequestBuilder req, boolean anyKeyOrder) throws Exception {
        if (anyKeyOrder) g.restUnordered(mvc, name, req);
        else g.rest(mvc, name, req);
        String sent = github.drain();
        if (!sent.isEmpty()) g.put(name + " -> github", sent);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ------------------------------------------------------------------ SetupController

    private void setup() throws Exception {
        step("setup status", get("/api/setup/status"));
        step("setup rate limit", get("/api/setup/rate-limit"));
        step("setup token", json(post("/api/setup/token"), "{\"token\":\"golden-token\"}"), true);
        g.put("stored prbench-config.json", Files.readString(CONFIG, StandardCharsets.UTF_8));
        step("setup status after token", get("/api/setup/status"));
    }

    // ------------------------------------------------------------------ IssueTypeController

    private void issueTypes() throws Exception {
        step("issue types", get("/api/issue-types"));
        step("issue types all", get("/api/issue-types").param("activeOnly", "false"));
        step("issue type categories", get("/api/issue-types/categories"));
        step("issue type 1", get("/api/issue-types/1"));
        step("issue type missing", get("/api/issue-types/999"));
        step("issue type by code", get("/api/issue-types/code/SQL_INJECTION"));
        step("issue type by code missing", get("/api/issue-types/code/NOPE"));
        step("issue type create", json(post("/api/issue-types"),
                "{\"code\":\"GOLDEN\",\"name\":\"Golden issue\",\"category\":\"TEST\",\"severityHint\":\"MINOR\",\"description\":\"d \\\"q\\\"\"}"));
        step("issue type update", json(put("/api/issue-types/13"), "{\"name\":\"Golden issue 2\",\"description\":\"changed\"}"));
        step("issue type deactivate", delete("/api/issue-types/13"));
        step("issue type create unknown field", json(post("/api/issue-types"),
                "{\"code\":\"X2\",\"name\":\"x\",\"extra\":{\"nested\":[1]}}"));
    }

    // ------------------------------------------------------------------ BotController

    private void bots() throws Exception {
        step("bots empty", get("/api/bots"));
        step("bot create", json(post("/api/bots"),
                "{\"name\":\"golden-bot\",\"description\":\"A bot\",\"workflowFileName\":\"review.yml\","
                        + "\"workflowContent\":\"on: pull_request\\njobs: {}\\n\",\"waitStrategy\":\"COMMENTS\",\"timeoutSeconds\":120}"));
        step("bot create 2", json(post("/api/bots"), "{\"name\":\"second-bot\"}"));
        step("bot create duplicate", json(post("/api/bots"), "{\"name\":\"golden-bot\"}"));
        step("bot get", get("/api/bots/1"));
        step("bot get missing", get("/api/bots/99"));
        step("bot update", json(put("/api/bots/2"), "{\"description\":\"updated\",\"timeoutSeconds\":30}"));
        step("bots", get("/api/bots"));
    }

    // ------------------------------------------------------------------ RepoController

    private void repos() throws Exception {
        step("repos empty", get("/api/repos"));
        step("repo create", json(post("/api/repos"),
                "{\"githubUrl\":\"https://github.com/golden/app.git\",\"mirrorOrg\":\"golden-mirror\",\"mirrorRepoName\":\"app-mirror\"}"));
        step("repo create bad url", json(post("/api/repos"), "{\"githubUrl\":\"nope\"}"));
        step("repo create missing url", json(post("/api/repos"), "{}"));
        step("repo import", json(post("/api/repos/import"),
                "{\"owner\":\"golden\",\"repoName\":\"lib\",\"description\":\"A lib\",\"language\":\"Kotlin\",\"fork\":\"true\",\"stars\":\"7\"}"),
                true);
        step("repo import existing", json(post("/api/repos/import"), "{\"owner\":\"golden\",\"repoName\":\"app\"}"), true);
        step("repo import missing", json(post("/api/repos/import"), "{\"owner\":\"golden\"}"), true);
        step("repo import all", json(post("/api/repos/import-all"),
                "[{\"owner\":\"golden-org\",\"repoName\":\"svc\",\"isPrivate\":\"true\",\"stars\":\"x\"},{\"owner\":\"golden\",\"repoName\":\"app\"},{\"repoName\":\"bad\"}]"),
                true);
        step("repo get", get("/api/repos/1"));
        step("repo get missing", get("/api/repos/99"));
        step("repo update", json(put("/api/repos/1"),
                "{\"name\":\"Golden App\",\"defaultBranch\":\"develop\",\"language\":\"Java\",\"notes\":\"Local notes\"}"));
        step("repo add tag", json(post("/api/repos/1/tags"), "{\"tag\":\"Backend\"}"));
        step("repo add tag 2", json(post("/api/repos/1/tags"), "{\"tag\":\"golden\"}"));
        step("repo remove tag", delete("/api/repos/1/tags/backend"));
        step("repo bulk tag", json(post("/api/repos/bulk-tag"), "{\"repoIds\":[1,2],\"tag\":\"bulk\"}"), true);
        step("repo tags", get("/api/repos/meta/tags"));
        step("repo notes", json(post("/api/repos/2/notes"), "{\"notes\":\"Lib notes é\"}"));
        step("repos", get("/api/repos"));
        step("repos by tag", get("/api/repos").param("tag", "bulk"));
        step("repos search", get("/api/repos").param("search", "lib"));
        step("repos filtered", get("/api/repos").param("language", "Java").param("hasNotes", "true").param("isFork", "false"));
        step("repo stats", get("/api/repos/meta/stats"), true);
        step("repo github status", get("/api/repos/1/github-status"), true);
        step("repo sync to github", post("/api/repos/1/sync-to-github"));
        step("repo generate description", post("/api/repos/1/generate-description").param("save", "false"), true);
        step("repo generate descriptions", post("/api/repos/meta/generate-descriptions"), true);
        step("repo github browse", get("/api/repos/github/browse"));
        step("repo github orgs", get("/api/repos/github/orgs"));
        step("repo github org repos", get("/api/repos/github/orgs/golden-org/repos"));
        step("repo prs", get("/api/repos/1/prs"));
        step("repo prs closed", get("/api/repos/1/prs").param("state", "closed"));
        step("repo docs before scan", get("/api/repos/1/docs"), true);
        step("repo scan docs", post("/api/repos/1/scan-docs"), true);
        step("repo docs", get("/api/repos/1/docs"), true);
        step("repo bulk scan docs", json(post("/api/repos/meta/scan-docs"), "{\"repoIds\":[2]}"), true);
        step("repo bulk scan docs unscanned", post("/api/repos/meta/scan-docs"), true);
        step("repo report", json(post("/api/repos/meta/report"), "{\"repoIds\":[1,2],\"title\":\"Golden report\"}"), true);
        step("repo report plain", json(post("/api/repos/meta/report"), "{\"includeDocs\":false,\"includeTags\":false}"), true);
    }

    // ------------------------------------------------------------------ SuiteController

    private void suites() throws Exception {
        step("suites empty", get("/api/suites"));
        step("suite create", json(post("/api/suites"), "{\"name\":\"Golden suite\",\"description\":\"desc\",\"exemplarRepoId\":1}"));
        step("suite create 2", json(post("/api/suites"), "{\"name\":\"To delete\",\"exemplarRepoId\":2}"));
        step("suite get", get("/api/suites/1"));
        step("suite get missing", get("/api/suites/99"));
        step("suite add pr", json(post("/api/suites/1/prs"), "{\"originalPrNumber\":5}"));
        step("suite add pr 2", json(post("/api/suites/1/prs"), "{\"originalPrNumber\":6}"));
        step("suite add pr duplicate", json(post("/api/suites/1/prs"), "{\"originalPrNumber\":5}"), true);
        step("suite prs", get("/api/suites/1/prs"));
        step("suite collect original comments", post("/api/suites/suite-prs/1/collect-original-comments"), true);
        step("suite remove pr", delete("/api/suites/1/prs/2"), true);
        step("suites", get("/api/suites"));
        step("suites by repo", get("/api/suites").param("repoId", "1"));
        step("suite delete", delete("/api/suites/2"), true);
    }

    // ------------------------------------------------------------------ RunController (seeded)

    private void seedRun() {
        String t = "TIMESTAMP '2020-01-02 03:04:05'";
        jdbc.update("INSERT INTO benchmark_runs (suite_id, name, status, concurrency, golden_dataset_enabled, started_at,"
                + " completed_at, error_message, config_json, created_at) VALUES (1, 'Seeded run', 'COMPLETED', 3, TRUE, "
                + t + ", TIMESTAMP '2020-01-02 04:05:06', NULL, '{\"concurrency\":3,\"bots\":[1]}', " + t + ")");
        jdbc.update("INSERT INTO benchmark_runs (suite_id, name, status, created_at) VALUES (1, 'Second run', 'FAILED', " + t + ")");
        jdbc.update("INSERT INTO run_bots (run_id, bot_id) VALUES (1, 1)");
        jdbc.update("INSERT INTO run_bots (run_id, bot_id) VALUES (2, 1)");
        jdbc.update("INSERT INTO replay_prs (run_id, suite_pr_id, bot_id, mirror_pr_number, mirror_pr_url, base_branch, head_branch,"
                + " status, created_at, completed_at) VALUES (1, 1, 1, 42, 'https://github.com/golden-mirror/app-mirror/pull/42',"
                + " 'replay/base-5', 'replay/head-5', 'COMPLETED', " + t + ", " + t + ")");
        jdbc.update("INSERT INTO replay_prs (run_id, suite_pr_id, bot_id, status, error_message, created_at)"
                + " VALUES (2, 1, 1, 'FAILED', 'boom', " + t + ")");
        jdbc.update("INSERT INTO review_comments (replay_pr_id, source, bot_id, github_comment_id, comment_type, file_path,"
                + " line_number, diff_hunk, body, body_normalized, fingerprint_hash, github_created_at, metadata_json, created_at)"
                + " VALUES (1, 'BOT', 1, 7001, 'REVIEW_COMMENT', 'src/Main.java', 12, '@@ -1 +1 @@', "
                + "'Possible null dereference of x here.', 'possible null dereference of x here', 'abc123', " + t
                + ", '{\"severity\":\"high\"}', " + t + ")");
        jdbc.update("INSERT INTO review_comments (replay_pr_id, source, bot_id, github_comment_id, comment_type, body,"
                + " body_normalized, created_at) VALUES (1, 'BOT', 1, 7002, 'ISSUE_COMMENT', 'Nice work', 'nice work', " + t + ")");
        jdbc.update("INSERT INTO bot_snapshots (run_id, bot_id, name, workflow_file_name, workflow_content, wait_strategy,"
                + " timeout_seconds, snapshotted_at) VALUES (1, 1, 'golden-bot', 'review.yml', 'on: pull_request', 'COMMENTS', 120, " + t + ")");
    }

    private void runs() throws Exception {
        step("runs", get("/api/runs"));
        step("runs by suite", get("/api/runs").param("suiteId", "1"));
        step("run get", get("/api/runs/1"));
        step("run get missing", get("/api/runs/99"));
        step("run progress", get("/api/runs/1/progress"));
        step("run progress empty", get("/api/runs/99/progress"));
        step("run replay prs", get("/api/runs/1/replay-prs"));
        step("run bot snapshots", get("/api/runs/1/bot-snapshots"));
        step("run replay pr comments", get("/api/runs/replay-prs/1/comments"));
        step("run similarities", get("/api/runs/1/similarities"));
        step("run cancel", post("/api/runs/2/cancel"), true);
        step("run cancel missing", post("/api/runs/99/cancel"));
        step("run create missing suite", json(post("/api/runs"), "{\"suiteId\":99,\"botIds\":[1]}"));
    }

    // ------------------------------------------------------------------ GradingController

    private void gradings() throws Exception {
        step("grading queue", get("/api/grading-queue"));
        step("grading queue by run", get("/api/grading-queue").param("runId", "1").param("limit", "1"));
        step("grading progress", get("/api/grading-progress"));
        step("grading create", json(post("/api/gradings"),
                "{\"commentId\":1,\"commentTableType\":\"REVIEW\",\"graderType\":\"HUMAN\",\"graderId\":\"alice\","
                        + "\"verdict\":\"TRUE_POSITIVE\",\"severity\":\"MAJOR\",\"stars\":4,\"flagged\":false,\"notes\":\"good\"}"));
        step("grading update", json(put("/api/gradings/1"), "{\"stars\":5,\"flagged\":true,\"notes\":\"great\"}"));
        step("grading bulk", json(post("/api/gradings/bulk"),
                "{\"commentIds\":[2],\"commentTableType\":\"REVIEW\",\"graderType\":\"HUMAN\",\"verdict\":\"FALSE_POSITIVE\"}"), true);
        step("comment gradings", get("/api/comments/1/gradings"));
        step("comment gradings original", get("/api/comments/1/gradings").param("type", "ORIGINAL"));
        step("grading progress after", get("/api/grading-progress").param("runId", "1"));
        step("grading delete", delete("/api/gradings/2"));
    }

    // ------------------------------------------------------------------ GoldenDatasetController

    private void goldenDataset() throws Exception {
        step("golden dataset empty", get("/api/golden-dataset"));
        step("golden promote", json(post("/api/golden-dataset/promote"),
                "{\"commentId\":1,\"commentTableType\":\"REVIEW\",\"issueType\":\"NULL_DEREF\",\"description\":\"null deref\"}"));
        step("golden promote original", json(post("/api/golden-dataset/promote"),
                "{\"commentId\":1,\"commentTableType\":\"ORIGINAL\",\"issueType\":\"RESOURCE_LEAK\"}"));
        step("golden promote missing", json(post("/api/golden-dataset/promote"), "{\"commentId\":99,\"commentTableType\":\"REVIEW\"}"));
        step("golden update", json(put("/api/golden-dataset/1"),
                "{\"canonicalBody\":\"x may be null\",\"includedByDefault\":false,\"active\":true}"));
        step("golden dataset", get("/api/golden-dataset"));
        step("golden export", get("/api/golden-dataset/export"));
        step("golden deactivate", delete("/api/golden-dataset/2"), true);
        step("golden dataset all", get("/api/golden-dataset").param("activeOnly", "false"));
    }

    // ------------------------------------------------------------------ ReportController

    private void reports() throws Exception {
        step("report run", get("/api/reports/runs/1"));
        step("report run missing", get("/api/reports/runs/99"));
        step("report comparison", get("/api/reports/runs/1/comparison"));
        step("report bot trend", get("/api/reports/bots/1/trend").param("suiteId", "1"));
        step("report bot trend no suite", get("/api/reports/bots/1/trend"));
        step("report significance", get("/api/reports/runs/1/significance").param("compareRunId", "2"));
    }

    private void cleanup() throws Exception {
        step("run cleanup", post("/api/runs/1/cleanup"), true);
        step("bot delete", delete("/api/bots/2"));
        step("issue type delete missing", delete("/api/issue-types/999"));
        step("repo delete", delete("/api/repos/3"));
        step("malformed body", json(post("/api/bots"), "{\"name\":"));
        step("wrong content type", post("/api/bots").contentType(MediaType.TEXT_PLAIN).content("x"));
    }

    // ------------------------------------------------------------------ stored JSON

    /** The JSON the app keeps in the database (CLOB columns written from Java code or from requests). */
    private void storedJson() {
        Map<String, String> queries = Map.of(
                "exemplar_repos", "SELECT id, name, tags, notes, docs_json, config_json FROM exemplar_repos ORDER BY id",
                "benchmark_suites", "SELECT id, name, pr_selection_config_json FROM benchmark_suites ORDER BY id",
                "suite_prs", "SELECT id, original_pr_number, title, description, author, pr_labels, metadata_json FROM suite_prs ORDER BY id",
                "original_comments", "SELECT id, author, github_comment_id, comment_type, file_path, line_number, body,"
                        + " body_normalized, fingerprint_hash, metadata_json FROM original_comments ORDER BY id",
                "gradings", "SELECT id, comment_id, comment_table_type, grader_type, verdict, severity, stars, flagged, notes FROM gradings ORDER BY id",
                "golden_dataset_entries", "SELECT id, suite_pr_id, source_comment_id, source_comment_type, issue_type, description,"
                        + " canonical_body, active, included_by_default FROM golden_dataset_entries ORDER BY id");
        for (String table : new java.util.TreeSet<>(queries.keySet())) {
            StringJoiner rows = new StringJoiner("\n");
            jdbc.query(queries.get(table), rs -> {
                StringJoiner cols = new StringJoiner(" | ");
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    cols.add(rs.getMetaData().getColumnLabel(i).toLowerCase() + "=" + rs.getString(i));
                }
                rows.add(cols.toString());
            });
            g.put("stored table " + table, rows.toString());
        }
    }
}
