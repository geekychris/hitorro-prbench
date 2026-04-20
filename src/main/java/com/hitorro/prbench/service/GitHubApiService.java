package com.hitorro.prbench.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * GitHub REST API client using personal access token authentication.
 * Uses hitorro-gittools GitHubApiClient for PR creation, and adds
 * additional endpoints needed for benchmarking (list PRs, comments, check runs).
 */
@Service
public class GitHubApiService {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiService.class);
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    @Value("${app.github.token:}")
    private String githubToken;

    @Value("${app.github.api-url:https://api.github.com}")
    private String apiUrl;

    private static final Path CONFIG_FILE = Path.of("data/prbench-config.json");

    public GitHubApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
        this.mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @PostConstruct
    void init() {
        loadConfig();
        // Env var overrides saved config
        String envToken = System.getenv("GITHUB_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            this.githubToken = envToken;
        }
    }

    public void setGithubToken(String token) {
        this.githubToken = token;
        saveConfig();
    }

    public boolean hasToken() { return githubToken != null && !githubToken.isBlank(); }

    private void loadConfig() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                var configMapper = new ObjectMapper();
                var config = configMapper.readTree(CONFIG_FILE.toFile());
                if (config.has("githubToken") && !config.get("githubToken").asText().isBlank()) {
                    this.githubToken = config.get("githubToken").asText();
                    log.info("Loaded GitHub token from {}", CONFIG_FILE);
                }
            }
        } catch (Exception e) {
            log.debug("No saved config: {}", e.getMessage());
        }
    }

    private void saveConfig() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            var configMapper = new ObjectMapper()
                    .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            var node = configMapper.createObjectNode();
            if (githubToken != null) node.put("githubToken", githubToken);
            configMapper.writeValue(CONFIG_FILE.toFile(), node);
            log.info("Saved config to {}", CONFIG_FILE);
        } catch (Exception e) {
            log.warn("Failed to save config: {}", e.getMessage());
        }
    }

    public JsonNode get(String path) throws Exception {
        var req = newRequest(path).GET().build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("GitHub API error " + resp.statusCode() + ": " + resp.body());
        }
        return mapper.readTree(resp.body());
    }

    public JsonNode post(String path, Object body) throws Exception {
        String json = mapper.writeValueAsString(body);
        var req = newRequest(path)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("GitHub API error " + resp.statusCode() + ": " + resp.body());
        }
        return mapper.readTree(resp.body());
    }

    public JsonNode patch(String path, Object body) throws Exception {
        String json = mapper.writeValueAsString(body);
        var req = newRequest(path)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("GitHub API error " + resp.statusCode() + ": " + resp.body());
        }
        return mapper.readTree(resp.body());
    }

    public void delete(String path) throws Exception {
        var req = newRequest(path).DELETE().build();
        httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /** Update a repository's description on GitHub. */
    public JsonNode updateRepoDescription(String owner, String repo, String description) throws Exception {
        var payload = mapper.createObjectNode();
        payload.put("description", description);
        return patch("/repos/" + owner + "/" + repo, payload);
    }

    /** Replace a repository's topics on GitHub. */
    public JsonNode replaceRepoTopics(String owner, String repo, List<String> topics) throws Exception {
        var payload = mapper.createObjectNode();
        var arr = payload.putArray("names");
        for (String t : topics) arr.add(t.toLowerCase().replaceAll("[^a-z0-9-]", "-"));
        var req = newRequest("/repos/" + owner + "/" + repo + "/topics")
                .method("PUT", HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .header("Content-Type", "application/json")
                .build();
        var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("GitHub API error " + resp.statusCode() + ": " + resp.body());
        }
        return mapper.readTree(resp.body());
    }

    /** Get a repository's current topics from GitHub. */
    public List<String> getRepoTopics(String owner, String repo) throws Exception {
        var node = get("/repos/" + owner + "/" + repo + "/topics");
        List<String> topics = new ArrayList<>();
        if (node.has("names") && node.get("names").isArray()) {
            node.get("names").forEach(t -> topics.add(t.asText()));
        }
        return topics;
    }

    /** Create a git tag on a repo (lightweight tag pointing to a commit SHA). */
    public JsonNode createGitTag(String owner, String repo, String tagName, String sha) throws Exception {
        var payload = mapper.createObjectNode();
        payload.put("ref", "refs/tags/" + tagName);
        payload.put("sha", sha);
        return post("/repos/" + owner + "/" + repo + "/git/refs", payload);
    }

    /** Delete a git tag from a repo. */
    public void deleteGitTag(String owner, String repo, String tagName) throws Exception {
        delete("/repos/" + owner + "/" + repo + "/git/refs/tags/" + tagName);
    }

    /** List git tags on a repo. */
    public List<String> listGitTags(String owner, String repo) throws Exception {
        var tags = getAllPages("/repos/" + owner + "/" + repo + "/tags");
        List<String> names = new ArrayList<>();
        for (var t : tags) names.add(t.get("name").asText());
        return names;
    }

    /** Get HEAD SHA of the default branch. */
    public String getHeadSha(String owner, String repo, String branch) throws Exception {
        var ref = get("/repos/" + owner + "/" + repo + "/git/ref/heads/" + branch);
        return ref.get("object").get("sha").asText();
    }

    /** List PRs for a repo with optional state filter. */
    public List<JsonNode> listPullRequests(String owner, String repo, String state, int perPage) throws Exception {
        String path = "/repos/" + owner + "/" + repo + "/pulls?state=" + state + "&per_page=" + perPage;
        JsonNode arr = get(path);
        List<JsonNode> result = new ArrayList<>();
        if (arr.isArray()) arr.forEach(result::add);
        return result;
    }

    /** Get a specific PR. */
    public JsonNode getPullRequest(String owner, String repo, int prNumber) throws Exception {
        return get("/repos/" + owner + "/" + repo + "/pulls/" + prNumber);
    }

    /** List PR review comments (inline). */
    public List<JsonNode> listPrReviewComments(String owner, String repo, int prNumber) throws Exception {
        return getAllPages("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/comments");
    }

    /** List PR reviews. */
    public List<JsonNode> listPrReviews(String owner, String repo, int prNumber) throws Exception {
        return getAllPages("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/reviews");
    }

    /** List issue comments on a PR. */
    public List<JsonNode> listIssueComments(String owner, String repo, int prNumber) throws Exception {
        return getAllPages("/repos/" + owner + "/" + repo + "/issues/" + prNumber + "/comments");
    }

    /** List check runs for a commit SHA. */
    public JsonNode listCheckRuns(String owner, String repo, String sha) throws Exception {
        return get("/repos/" + owner + "/" + repo + "/commits/" + sha + "/check-runs");
    }

    /** Create a PR in a repo. */
    public JsonNode createPullRequest(String owner, String repo, String title, String body,
                                       String head, String base) throws Exception {
        var payload = mapper.createObjectNode();
        payload.put("title", title);
        payload.put("body", body);
        payload.put("head", head);
        payload.put("base", base);
        return post("/repos/" + owner + "/" + repo + "/pulls", payload);
    }

    /** Close a PR. */
    public void closePullRequest(String owner, String repo, int prNumber) throws Exception {
        var payload = mapper.createObjectNode();
        payload.put("state", "closed");
        patch("/repos/" + owner + "/" + repo + "/pulls/" + prNumber, payload);
    }

    /** Create a git reference (branch). */
    public JsonNode createRef(String owner, String repo, String ref, String sha) throws Exception {
        var payload = mapper.createObjectNode();
        payload.put("ref", ref);
        payload.put("sha", sha);
        return post("/repos/" + owner + "/" + repo + "/git/refs", payload);
    }

    /** Delete a git reference (branch). */
    public void deleteRef(String owner, String repo, String ref) throws Exception {
        delete("/repos/" + owner + "/" + repo + "/git/refs/" + ref);
    }

    /** Get the authenticated user. */
    public JsonNode getAuthenticatedUser() throws Exception {
        return get("/user");
    }

    /** List repos for the authenticated user (all types, sorted by updated). */
    public List<JsonNode> listUserRepos(String visibility, String sort, int perPage) throws Exception {
        return getAllPages("/user/repos?visibility=" + visibility + "&sort=" + sort
                + "&per_page=" + perPage + "&affiliation=owner,collaborator,organization_member");
    }

    /** List repos for a specific user or organization. */
    public List<JsonNode> listOrgRepos(String org) throws Exception {
        return getAllPages("/orgs/" + org + "/repos?per_page=100&sort=updated");
    }

    /** List organizations the authenticated user belongs to. */
    public List<JsonNode> listUserOrgs() throws Exception {
        return getAllPages("/user/orgs?per_page=100");
    }

    /** Get rate limit info. */
    public JsonNode getRateLimit() throws Exception {
        return get("/rate_limit");
    }

    private List<JsonNode> getAllPages(String path) throws Exception {
        List<JsonNode> all = new ArrayList<>();
        String currentPath = path + (path.contains("?") ? "&" : "?") + "per_page=100";
        int page = 1;
        while (page <= 10) { // safety limit
            JsonNode arr = get(currentPath + "&page=" + page);
            if (!arr.isArray() || arr.isEmpty()) break;
            arr.forEach(all::add);
            if (arr.size() < 100) break;
            page++;
        }
        return all;
    }

    private HttpRequest.Builder newRequest(String path) {
        String url = path.startsWith("http") ? path : apiUrl + path;
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json");
        if (githubToken != null && !githubToken.isBlank()) {
            builder.header("Authorization", "Bearer " + githubToken);
        }
        return builder;
    }
}
