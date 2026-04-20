package com.hitorro.prbench.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    public GitHubApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
        this.mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public void setGithubToken(String token) { this.githubToken = token; }
    public boolean hasToken() { return githubToken != null && !githubToken.isBlank(); }

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
