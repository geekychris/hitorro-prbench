package com.hitorro.prbench.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.prbench.entity.ExemplarRepo;
import com.hitorro.prbench.repository.ExemplarRepoRepository;
import com.hitorro.prbench.service.GitHubApiService;
import com.hitorro.prbench.service.OllamaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/repos")
@CrossOrigin(origins = "*")
public class RepoController {

    private static final Logger log = LoggerFactory.getLogger(RepoController.class);

    private final ExemplarRepoRepository repoRepo;
    private final GitHubApiService github;
    private final OllamaService ollama;

    public RepoController(ExemplarRepoRepository repoRepo, GitHubApiService github, OllamaService ollama) {
        this.repoRepo = repoRepo;
        this.github = github;
        this.ollama = ollama;
    }

    @GetMapping
    public List<ExemplarRepo> list(@RequestParam(required = false) String tag,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(required = false) String language,
                                    @RequestParam(required = false) String owner,
                                    @RequestParam(required = false) Boolean hasNotes,
                                    @RequestParam(required = false) Boolean isFork) {
        var repos = repoRepo.findAll();
        if (tag != null && !tag.isBlank()) {
            repos = repos.stream().filter(r -> r.getTagList().contains(tag)).collect(Collectors.toList());
        }
        if (language != null && !language.isBlank()) {
            repos = repos.stream().filter(r -> language.equalsIgnoreCase(r.getLanguage())).collect(Collectors.toList());
        }
        if (owner != null && !owner.isBlank()) {
            repos = repos.stream().filter(r -> owner.equalsIgnoreCase(r.getOwner())).collect(Collectors.toList());
        }
        if (hasNotes != null) {
            repos = repos.stream().filter(r -> {
                boolean hasDesc = (r.getNotes() != null && !r.getNotes().isBlank())
                        || (r.getGithubDescription() != null && !r.getGithubDescription().isBlank());
                return hasNotes == hasDesc;
            }).collect(Collectors.toList());
        }
        if (isFork != null) {
            repos = repos.stream().filter(r -> isFork == r.isFork()).collect(Collectors.toList());
        }
        if (search != null && !search.isBlank()) {
            String lower = search.toLowerCase();
            repos = repos.stream().filter(r ->
                    r.getName().toLowerCase().contains(lower) ||
                    r.getOwner().toLowerCase().contains(lower) ||
                    r.getRepoName().toLowerCase().contains(lower) ||
                    (r.getNotes() != null && r.getNotes().toLowerCase().contains(lower)) ||
                    (r.getGithubDescription() != null && r.getGithubDescription().toLowerCase().contains(lower)) ||
                    (r.getTags() != null && r.getTags().toLowerCase().contains(lower)) ||
                    (r.getLanguage() != null && r.getLanguage().toLowerCase().contains(lower))
            ).collect(Collectors.toList());
        }
        return repos;
    }

    /** Faceted stats for the repo collection. */
    @GetMapping("/meta/stats")
    public ResponseEntity<?> stats() {
        var repos = repoRepo.findAll();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", repos.size());

        // By owner
        Map<String, Long> byOwner = repos.stream()
                .collect(Collectors.groupingBy(ExemplarRepo::getOwner, Collectors.counting()));
        stats.put("byOwner", byOwner);

        // By language
        Map<String, Long> byLanguage = repos.stream()
                .filter(r -> r.getLanguage() != null && !r.getLanguage().isBlank())
                .collect(Collectors.groupingBy(ExemplarRepo::getLanguage, Collectors.counting()));
        stats.put("byLanguage", byLanguage);

        // By tag
        Map<String, Long> byTag = new TreeMap<>();
        for (var r : repos) {
            for (String t : r.getTagList()) {
                byTag.merge(t, 1L, Long::sum);
            }
        }
        stats.put("byTag", byTag);

        // Counts
        stats.put("withNotes", repos.stream().filter(r ->
                (r.getNotes() != null && !r.getNotes().isBlank()) ||
                (r.getGithubDescription() != null && !r.getGithubDescription().isBlank())).count());
        stats.put("withoutNotes", repos.stream().filter(r ->
                (r.getNotes() == null || r.getNotes().isBlank()) &&
                (r.getGithubDescription() == null || r.getGithubDescription().isBlank())).count());
        stats.put("forks", repos.stream().filter(ExemplarRepo::isFork).count());
        stats.put("owned", repos.stream().filter(r -> !r.isFork()).count());
        stats.put("ollamaAvailable", ollama.isAvailable());

        return ResponseEntity.ok(stats);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        String githubUrl = body.get("githubUrl");
        String name = body.getOrDefault("name", "");
        if (githubUrl == null) return ResponseEntity.badRequest().body(Map.of("error", "githubUrl required"));

        String[] parts = githubUrl.replaceAll("https?://github\\.com/", "")
                .replaceAll("\\.git$", "").split("/");
        if (parts.length < 2) return ResponseEntity.badRequest().body(Map.of("error", "Invalid GitHub URL"));

        var repo = new ExemplarRepo();
        repo.setName(name.isBlank() ? parts[1] : name);
        repo.setGithubUrl(githubUrl);
        repo.setOwner(parts[0]);
        repo.setRepoName(parts[1]);
        repo.setMirrorOrg(body.get("mirrorOrg"));
        repo.setMirrorRepoName(body.get("mirrorRepoName"));
        repo.setDefaultBranch(body.getOrDefault("defaultBranch", "main"));
        return ResponseEntity.ok(repoRepo.save(repo));
    }

    @PostMapping("/import")
    public ResponseEntity<?> importFromGitHub(@RequestBody Map<String, String> body) {
        String ownerName = body.get("owner");
        String repoName = body.get("repoName");
        if (ownerName == null || repoName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "owner and repoName required"));
        }
        var existing = repoRepo.findByOwnerAndRepoName(ownerName, repoName);
        if (existing.isPresent()) {
            return ResponseEntity.ok(Map.of("repo", existing.get(), "alreadyExists", true));
        }

        var repo = new ExemplarRepo();
        repo.setName(body.getOrDefault("name", repoName));
        repo.setGithubUrl("https://github.com/" + ownerName + "/" + repoName);
        repo.setOwner(ownerName);
        repo.setRepoName(repoName);
        repo.setDefaultBranch(body.getOrDefault("defaultBranch", "main"));
        if (body.containsKey("description")) repo.setGithubDescription(body.get("description"));
        if (body.containsKey("language")) repo.setLanguage(body.get("language"));
        if ("true".equals(body.get("fork"))) repo.setFork(true);
        if ("true".equals(body.get("isPrivate"))) repo.setIsPrivate(true);
        if (body.containsKey("stars")) {
            try { repo.setStars(Integer.parseInt(body.get("stars"))); } catch (NumberFormatException ignored) {}
        }
        return ResponseEntity.ok(Map.of("repo", repoRepo.save(repo), "alreadyExists", false));
    }

    @PostMapping("/import-all")
    public ResponseEntity<?> importAll(@RequestBody List<Map<String, String>> repos) {
        int imported = 0, skipped = 0;
        for (var body : repos) {
            String ownerName = body.get("owner");
            String repoName = body.get("repoName");
            if (ownerName == null || repoName == null) continue;
            if (repoRepo.findByOwnerAndRepoName(ownerName, repoName).isPresent()) {
                skipped++;
                continue;
            }
            var repo = new ExemplarRepo();
            repo.setName(body.getOrDefault("name", repoName));
            repo.setGithubUrl("https://github.com/" + ownerName + "/" + repoName);
            repo.setOwner(ownerName);
            repo.setRepoName(repoName);
            repo.setDefaultBranch(body.getOrDefault("defaultBranch", "main"));
            if (body.containsKey("description")) repo.setGithubDescription(body.get("description"));
            if (body.containsKey("language")) repo.setLanguage(body.get("language"));
            if ("true".equals(body.get("fork"))) repo.setFork(true);
            if ("true".equals(body.get("isPrivate"))) repo.setIsPrivate(true);
            if (body.containsKey("stars")) {
                try { repo.setStars(Integer.parseInt(body.get("stars"))); } catch (NumberFormatException ignored) {}
            }
            repoRepo.save(repo);
            imported++;
        }
        return ResponseEntity.ok(Map.of("imported", imported, "skipped", skipped));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return repoRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Fetch live state from GitHub for comparison with local. */
    @GetMapping("/{id}/github-status")
    public ResponseEntity<?> githubStatus(@PathVariable Long id) {
        return repoRepo.findById(id).map(repo -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("local", Map.of(
                    "notes", repo.getNotes() != null ? repo.getNotes() : "",
                    "tags", repo.getTagList(),
                    "githubDescription", repo.getGithubDescription() != null ? repo.getGithubDescription() : ""));
            try {
                var ghRepo = github.get("/repos/" + repo.getOwner() + "/" + repo.getRepoName());
                var topics = github.getRepoTopics(repo.getOwner(), repo.getRepoName());
                result.put("github", Map.of(
                        "description", ghRepo.has("description") && !ghRepo.get("description").isNull()
                                ? ghRepo.get("description").asText() : "",
                        "topics", topics));
                result.put("synced", true);
            } catch (Exception e) {
                result.put("github", Map.of("error", e.getMessage()));
                result.put("synced", false);
            }
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Force-sync local description and tags to GitHub for a repo. */
    @PostMapping("/{id}/sync-to-github")
    public ResponseEntity<?> syncToGitHub(@PathVariable Long id) {
        return repoRepo.findById(id).map(repo -> {
            Map<String, Object> result = new LinkedHashMap<>();
            // Push description
            String desc = repo.getNotes() != null ? repo.getNotes() : repo.getGithubDescription();
            if (desc != null && !desc.isBlank()) {
                try {
                    github.updateRepoDescription(repo.getOwner(), repo.getRepoName(), desc);
                    result.put("descriptionPushed", true);
                } catch (Exception e) {
                    result.put("descriptionPushed", false);
                    result.put("descriptionError", e.getMessage());
                }
            }
            // Push tags as GitHub topics
            if (!repo.getTagList().isEmpty()) {
                try {
                    syncTopicsToGitHub(repo);
                    result.put("topicsPushed", true);
                } catch (Exception e) {
                    result.put("topicsPushed", false);
                    result.put("topicsError", e.getMessage());
                }
            }
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return repoRepo.findById(id).map(repo -> {
            if (body.containsKey("name")) repo.setName(body.get("name"));
            if (body.containsKey("mirrorOrg")) repo.setMirrorOrg(body.get("mirrorOrg"));
            if (body.containsKey("mirrorRepoName")) repo.setMirrorRepoName(body.get("mirrorRepoName"));
            if (body.containsKey("defaultBranch")) repo.setDefaultBranch(body.get("defaultBranch"));
            if (body.containsKey("notes")) repo.setNotes(body.get("notes"));
            return ResponseEntity.ok(repoRepo.save(repo));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        repoRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ─── Tags (saved locally + pushed to GitHub as Topics) ──

    @PostMapping("/{id}/tags")
    public ResponseEntity<?> addTag(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return repoRepo.findById(id).map(repo -> {
            String tag = body.get("tag");
            repo.addTag(tag);
            repoRepo.save(repo);
            syncTopicsToGitHub(repo);
            return ResponseEntity.ok(repo);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/tags/{tag}")
    public ResponseEntity<?> removeTag(@PathVariable Long id, @PathVariable String tag) {
        return repoRepo.findById(id).map(repo -> {
            repo.removeTag(tag);
            repoRepo.save(repo);
            syncTopicsToGitHub(repo);
            return ResponseEntity.ok(repo);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Bulk tag: add a tag to multiple repos (pushes to GitHub as Topics). */
    @PostMapping("/bulk-tag")
    public ResponseEntity<?> bulkTag(@RequestBody Map<String, Object> body) {
        String tag = (String) body.get("tag");
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("repoIds");
        int count = 0;
        for (Number id : ids) {
            repoRepo.findById(id.longValue()).ifPresent(repo -> {
                repo.addTag(tag);
                repoRepo.save(repo);
                syncTopicsToGitHub(repo);
            });
            count++;
        }
        return ResponseEntity.ok(Map.of("tagged", count));
    }

    @GetMapping("/meta/tags")
    public ResponseEntity<?> allTags() {
        Set<String> allTags = new TreeSet<>();
        for (var repo : repoRepo.findAll()) allTags.addAll(repo.getTagList());
        return ResponseEntity.ok(allTags);
    }

    /** Sync local tags to GitHub Topics (visible on repo page under About). */
    private void syncTopicsToGitHub(ExemplarRepo repo) {
        try {
            // GitHub topics must be lowercase, alphanumeric + hyphens
            List<String> topics = repo.getTagList().stream()
                    .map(t -> t.toLowerCase().replaceAll("[^a-z0-9-]", "-"))
                    .distinct().toList();
            github.replaceRepoTopics(repo.getOwner(), repo.getRepoName(), topics);
            log.info("Set GitHub topics on {}/{}: {}", repo.getOwner(), repo.getRepoName(), topics);
        } catch (Exception e) {
            log.warn("Failed to set topics on {}/{}: {}",
                    repo.getOwner(), repo.getRepoName(), e.getMessage());
        }
    }

    // ─── Description / Notes (saved locally + pushed to GitHub) ─

    @PostMapping("/{id}/notes")
    public ResponseEntity<?> setNotes(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return repoRepo.findById(id).map(repo -> {
            String notes = body.get("notes");
            repo.setNotes(notes);
            repoRepo.save(repo);
            // Push description to GitHub (max 350 chars)
            String ghDesc = notes != null && notes.length() > 350
                    ? notes.substring(0, 347) + "..." : notes;
            try {
                github.updateRepoDescription(repo.getOwner(), repo.getRepoName(), ghDesc);
                repo.setGithubDescription(ghDesc);
                repoRepo.save(repo);
                log.info("Pushed description to {}/{}", repo.getOwner(), repo.getRepoName());
            } catch (Exception e) {
                log.warn("Failed to push description to GitHub for {}/{}: {}",
                        repo.getOwner(), repo.getRepoName(), e.getMessage());
            }
            return ResponseEntity.ok(repo);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ─── LLM Description Generation ─────────────────────────

    /** Scan a repo via GitHub API and generate a description with Ollama.
     *  If save=true (default), auto-saves the generated description to the notes field. */
    @PostMapping("/{id}/generate-description")
    public ResponseEntity<?> generateDescription(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "true") boolean save) {
        return repoRepo.findById(id).map(repo -> {
            try {
                var context = scanRepoContext(repo);
                String description = ollama.generateDescription(
                        repo.getRepoName(), repo.getOwner(), repo.getLanguage(),
                        context.readme, context.fileTree, context.recentCommits,
                        context.topics, context.packageManifest, repo.getGithubDescription());

                if (description != null) {
                    if (save) {
                        repo.setNotes(description);
                        repoRepo.save(repo);
                        String ghDesc = description.length() > 350
                                ? description.substring(0, 347) + "..." : description;
                        try {
                            github.updateRepoDescription(repo.getOwner(), repo.getRepoName(), ghDesc);
                            repo.setGithubDescription(ghDesc);
                            repoRepo.save(repo);
                        } catch (Exception e) {
                            log.warn("Failed to push AI description to GitHub: {}", e.getMessage());
                        }
                    }
                    return ResponseEntity.ok(Map.of("description", description, "generated", true, "saved", save));
                } else {
                    return ResponseEntity.ok(Map.of("error",
                            "Ollama unavailable or failed. Is it running at localhost:11434?", "generated", false));
                }
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("error", e.getMessage(), "generated", false));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Bulk generate descriptions for all repos without notes. */
    @PostMapping("/meta/generate-descriptions")
    public ResponseEntity<?> generateAllDescriptions() {
        var repos = repoRepo.findAll().stream()
                .filter(r -> r.getNotes() == null || r.getNotes().isBlank())
                .toList();
        int generated = 0, failed = 0;
        for (var repo : repos) {
            try {
                var context = scanRepoContext(repo);
                String desc = ollama.generateDescription(
                        repo.getRepoName(), repo.getOwner(), repo.getLanguage(),
                        context.readme, context.fileTree, context.recentCommits,
                        context.topics, context.packageManifest, repo.getGithubDescription());
                if (desc != null) {
                    repo.setNotes(desc);
                    repoRepo.save(repo);
                    String ghDesc = desc.length() > 350 ? desc.substring(0, 347) + "..." : desc;
                    try {
                        github.updateRepoDescription(repo.getOwner(), repo.getRepoName(), ghDesc);
                        repo.setGithubDescription(ghDesc);
                        repoRepo.save(repo);
                    } catch (Exception e2) {
                        log.warn("Failed to push description to GitHub for {}: {}", repo.getRepoName(), e2.getMessage());
                    }
                    generated++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
                log.debug("Failed to generate for {}: {}", repo.getRepoName(), e.getMessage());
            }
        }
        return ResponseEntity.ok(Map.of("generated", generated, "failed", failed, "total", repos.size()));
    }

    /** Gather context about a repo from GitHub for LLM consumption. */
    private RepoContext scanRepoContext(ExemplarRepo repo) {
        String path = "/repos/" + repo.getOwner() + "/" + repo.getRepoName();
        var ctx = new RepoContext();

        // README
        try {
            var readmeNode = github.get(path + "/readme");
            if (readmeNode.has("content")) {
                String encoded = readmeNode.get("content").asText().replaceAll("\\n", "");
                ctx.readme = new String(java.util.Base64.getDecoder().decode(encoded));
            }
        } catch (Exception e) {
            log.debug("No README for {}: {}", repo.getRepoName(), e.getMessage());
        }

        // Topics
        try {
            var repoNode = github.get(path);
            if (repoNode.has("topics") && repoNode.get("topics").isArray()) {
                ctx.topics = new ArrayList<>();
                repoNode.get("topics").forEach(t -> ctx.topics.add(t.asText()));
            }
        } catch (Exception ignored) {}

        // File tree (root level)
        try {
            var treeNode = github.get(path + "/contents");
            if (treeNode.isArray()) {
                ctx.fileTree = new ArrayList<>();
                treeNode.forEach(f -> {
                    String type = f.has("type") ? f.get("type").asText() : "";
                    String name = f.get("name").asText();
                    ctx.fileTree.add(("dir".equals(type) ? name + "/" : name));
                });
            }
        } catch (Exception ignored) {}

        // Recent commits
        try {
            var commitsNode = github.get(path + "/commits?per_page=10");
            if (commitsNode.isArray()) {
                ctx.recentCommits = new ArrayList<>();
                commitsNode.forEach(c -> {
                    if (c.has("commit") && c.get("commit").has("message")) {
                        String msg = c.get("commit").get("message").asText();
                        // Just the first line
                        ctx.recentCommits.add(msg.contains("\n") ? msg.substring(0, msg.indexOf('\n')) : msg);
                    }
                });
            }
        } catch (Exception ignored) {}

        // Package manifest (pom.xml, package.json, build.gradle, Cargo.toml, etc.)
        for (String manifest : List.of("pom.xml", "package.json", "build.gradle", "Cargo.toml",
                "pyproject.toml", "setup.py", "go.mod", "Gemfile")) {
            try {
                var fileNode = github.get(path + "/contents/" + manifest);
                if (fileNode.has("content")) {
                    String encoded = fileNode.get("content").asText().replaceAll("\\n", "");
                    ctx.packageManifest = new String(java.util.Base64.getDecoder().decode(encoded));
                    break;
                }
            } catch (Exception ignored) {}
        }

        return ctx;
    }

    private static class RepoContext {
        String readme;
        List<String> fileTree;
        List<String> recentCommits;
        List<String> topics;
        String packageManifest;
    }

    // ─── GitHub Browsing ─────────────────────────────────────

    @GetMapping("/github/browse")
    public ResponseEntity<?> browseGitHubRepos(
            @RequestParam(defaultValue = "all") String visibility,
            @RequestParam(defaultValue = "updated") String sort) {
        try {
            var repos = github.listUserRepos(visibility, sort, 100);
            Set<String> registered = repoRepo.findAll().stream()
                    .map(r -> r.getOwner() + "/" + r.getRepoName())
                    .collect(Collectors.toSet());

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode r : repos) {
                result.add(ghRepoToMap(r, registered));
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/github/orgs")
    public ResponseEntity<?> listOrgs() {
        try {
            return ResponseEntity.ok(github.listUserOrgs());
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/github/orgs/{org}/repos")
    public ResponseEntity<?> listOrgRepos(@PathVariable String org) {
        try {
            var repos = github.listOrgRepos(org);
            Set<String> registered = repoRepo.findAll().stream()
                    .map(r -> r.getOwner() + "/" + r.getRepoName())
                    .collect(Collectors.toSet());
            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode r : repos) result.add(ghRepoToMap(r, registered));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/prs")
    public ResponseEntity<?> listPrs(@PathVariable Long id,
                                      @RequestParam(defaultValue = "all") String state) {
        return repoRepo.findById(id).map(repo -> {
            try {
                var prs = github.listPullRequests(repo.getOwner(), repo.getRepoName(), state, 30);
                return ResponseEntity.ok(prs);
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    // ─── Helpers ─────────────────────────────────────────────

    private Map<String, Object> ghRepoToMap(JsonNode r, Set<String> registered) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fullName", r.get("full_name").asText());
        item.put("owner", r.get("owner").get("login").asText());
        item.put("name", r.get("name").asText());
        item.put("description", r.has("description") && !r.get("description").isNull()
                ? r.get("description").asText() : "");
        item.put("defaultBranch", r.has("default_branch") ? r.get("default_branch").asText() : "main");
        item.put("private", r.get("private").asBoolean());
        item.put("fork", r.has("fork") && r.get("fork").asBoolean());
        item.put("language", r.has("language") && !r.get("language").isNull()
                ? r.get("language").asText() : "");
        item.put("stars", r.has("stargazers_count") ? r.get("stargazers_count").asInt() : 0);
        item.put("updatedAt", r.has("updated_at") ? r.get("updated_at").asText() : "");
        item.put("htmlUrl", r.get("html_url").asText());
        item.put("registered", registered.contains(
                r.get("owner").get("login").asText() + "/" + r.get("name").asText()));
        return item;
    }

    // ─── Docs Scanning ──────────────────────────────────────

    /** Scan a single repo for markdown/doc files via GitHub API. */
    @PostMapping("/{id}/scan-docs")
    public ResponseEntity<?> scanDocs(@PathVariable Long id) {
        return repoRepo.findById(id).map(repo -> {
            try {
                var docs = scanRepoForDocs(repo);
                var mapper = new ObjectMapper();
                repo.setDocsJson(mapper.writeValueAsString(docs));
                repo.setDocsScannedAt(Instant.now());
                repoRepo.save(repo);
                return ResponseEntity.ok(Map.of("docs", docs, "count", docs.size()));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("error", e.getMessage(), "docs", List.of()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Bulk scan: scan multiple repos for docs. */
    @PostMapping("/meta/scan-docs")
    public ResponseEntity<?> bulkScanDocs(@RequestBody(required = false) Map<String, Object> body) {
        List<ExemplarRepo> toScan;
        if (body != null && body.containsKey("repoIds")) {
            @SuppressWarnings("unchecked")
            List<Number> ids = (List<Number>) body.get("repoIds");
            toScan = ids.stream()
                    .map(n -> repoRepo.findById(n.longValue()))
                    .filter(Optional::isPresent).map(Optional::get)
                    .toList();
        } else {
            // Scan all repos that haven't been scanned yet
            toScan = repoRepo.findAll().stream()
                    .filter(r -> r.getDocsScannedAt() == null)
                    .toList();
        }

        int scanned = 0, failed = 0;
        for (var repo : toScan) {
            try {
                var docs = scanRepoForDocs(repo);
                var mapper = new ObjectMapper();
                repo.setDocsJson(mapper.writeValueAsString(docs));
                repo.setDocsScannedAt(Instant.now());
                repoRepo.save(repo);
                scanned++;
            } catch (Exception e) {
                log.debug("Failed to scan docs for {}: {}", repo.getRepoName(), e.getMessage());
                failed++;
            }
        }
        return ResponseEntity.ok(Map.of("scanned", scanned, "failed", failed, "total", toScan.size()));
    }

    /** Get docs for a repo. */
    @GetMapping("/{id}/docs")
    public ResponseEntity<?> getDocs(@PathVariable Long id) {
        return repoRepo.findById(id).map(repo -> {
            if (repo.getDocsJson() == null) return ResponseEntity.ok(Map.of("docs", List.of(), "scanned", false));
            try {
                var mapper = new ObjectMapper();
                var docs = mapper.readValue(repo.getDocsJson(), new TypeReference<List<Map<String, String>>>() {});
                return ResponseEntity.ok(Map.of("docs", docs, "scanned", true,
                        "scannedAt", repo.getDocsScannedAt()));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("docs", List.of(), "error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Scan a repo for markdown and documentation files. */
    private List<Map<String, String>> scanRepoForDocs(ExemplarRepo repo) throws Exception {
        String basePath = "/repos/" + repo.getOwner() + "/" + repo.getRepoName();
        String baseUrl = "https://github.com/" + repo.getOwner() + "/" + repo.getRepoName();
        List<Map<String, String>> docs = new ArrayList<>();

        // Use the git tree API with recursive flag to find all files
        try {
            String branch = repo.getDefaultBranch() != null ? repo.getDefaultBranch() : "main";
            var tree = github.get(basePath + "/git/trees/" + branch + "?recursive=1");

            if (tree.has("tree") && tree.get("tree").isArray()) {
                for (var node : tree.get("tree")) {
                    if (!"blob".equals(node.get("type").asText())) continue;
                    String path = node.get("path").asText();
                    String lower = path.toLowerCase();

                    // Match markdown files and common doc files
                    boolean isDoc = lower.endsWith(".md") || lower.endsWith(".mdx")
                            || lower.endsWith(".rst") || lower.endsWith(".adoc")
                            || lower.equals("license") || lower.equals("licence")
                            || lower.equals("changelog") || lower.equals("changes")
                            || lower.equals("contributing");

                    // Also match files in docs-like directories
                    boolean inDocsDir = lower.startsWith("docs/") || lower.startsWith("doc/")
                            || lower.startsWith("documentation/") || lower.startsWith("wiki/")
                            || lower.startsWith(".github/");

                    if (isDoc || (inDocsDir && (lower.endsWith(".md") || lower.endsWith(".mdx")
                            || lower.endsWith(".rst") || lower.endsWith(".adoc")
                            || lower.endsWith(".txt")))) {
                        var doc = new LinkedHashMap<String, String>();
                        doc.put("path", path);
                        doc.put("url", baseUrl + "/blob/" + branch + "/" + path);
                        doc.put("name", path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path);

                        // Categorize
                        if (lower.equals("readme.md") || lower.equals("readme.rst")) {
                            doc.put("category", "readme");
                        } else if (lower.contains("changelog") || lower.contains("changes")) {
                            doc.put("category", "changelog");
                        } else if (lower.contains("contributing")) {
                            doc.put("category", "contributing");
                        } else if (lower.contains("license") || lower.contains("licence")) {
                            doc.put("category", "license");
                        } else if (inDocsDir) {
                            doc.put("category", "docs");
                        } else {
                            doc.put("category", "other");
                        }

                        docs.add(doc);
                    }
                }
            }
        } catch (Exception e) {
            // Fallback: try root contents for empty/minimal repos
            log.debug("Tree API failed for {}, trying contents: {}", repo.getRepoName(), e.getMessage());
            try {
                var contents = github.get(basePath + "/contents");
                if (contents.isArray()) {
                    for (var f : contents) {
                        String name = f.get("name").asText().toLowerCase();
                        if (name.endsWith(".md") || name.equals("readme") || name.equals("license")) {
                            var doc = new LinkedHashMap<String, String>();
                            doc.put("path", f.get("name").asText());
                            doc.put("url", f.get("html_url").asText());
                            doc.put("name", f.get("name").asText());
                            doc.put("category", name.startsWith("readme") ? "readme" : "other");
                            docs.add(doc);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Sort: readme first, then docs, then others
        docs.sort(Comparator.comparingInt(d -> {
            return switch (d.get("category")) {
                case "readme" -> 0;
                case "docs" -> 1;
                case "contributing" -> 2;
                case "changelog" -> 3;
                case "license" -> 4;
                default -> 5;
            };
        }));

        return docs;
    }

    // ─── Report Generation ──────────────────────────────────

    /** Generate a markdown report for selected repos. */
    @PostMapping("/meta/report")
    public ResponseEntity<?> generateReport(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> repoIds = (List<Number>) body.get("repoIds");
        String title = (String) body.getOrDefault("title", "Repository Report");
        boolean includeDocs = body.containsKey("includeDocs") ? (Boolean) body.get("includeDocs") : true;
        boolean includeTags = body.containsKey("includeTags") ? (Boolean) body.get("includeTags") : true;

        List<ExemplarRepo> repos;
        if (repoIds != null && !repoIds.isEmpty()) {
            repos = repoIds.stream()
                    .map(n -> repoRepo.findById(n.longValue()))
                    .filter(Optional::isPresent).map(Optional::get)
                    .toList();
        } else {
            repos = repoRepo.findAll();
        }

        var mapper = new ObjectMapper();
        StringBuilder md = new StringBuilder();
        md.append("# ").append(title).append("\n\n");
        md.append("Generated: ").append(Instant.now().toString()).append("\n\n");

        // Summary table
        md.append("## Summary\n\n");
        md.append("| Repository | Language | Tags | Description |\n");
        md.append("|:-----------|:---------|:-----|:------------|\n");
        for (var repo : repos) {
            String desc = repo.getNotes() != null ? repo.getNotes() : (repo.getGithubDescription() != null ? repo.getGithubDescription() : "");
            if (desc.length() > 100) desc = desc.substring(0, 97) + "...";
            String tags = repo.getTags() != null ? repo.getTags().replace(",", ", ") : "";
            md.append("| [").append(repo.getOwner()).append("/").append(repo.getRepoName()).append("](")
                    .append("https://github.com/").append(repo.getOwner()).append("/").append(repo.getRepoName()).append(")")
                    .append(repo.isFork() ? " (fork)" : "")
                    .append(" | ").append(repo.getLanguage() != null ? repo.getLanguage() : "-")
                    .append(" | ").append(tags.isEmpty() ? "-" : tags)
                    .append(" | ").append(desc.isEmpty() ? "-" : desc)
                    .append(" |\n");
        }

        // Detailed sections per repo
        if (includeDocs || includeTags) {
            md.append("\n## Repository Details\n\n");
            for (var repo : repos) {
                md.append("### [").append(repo.getRepoName()).append("](https://github.com/")
                        .append(repo.getOwner()).append("/").append(repo.getRepoName()).append(")\n\n");

                String desc = repo.getNotes() != null ? repo.getNotes() : repo.getGithubDescription();
                if (desc != null && !desc.isBlank()) {
                    md.append(desc).append("\n\n");
                }

                if (includeTags && repo.getTags() != null && !repo.getTags().isBlank()) {
                    md.append("**Tags:** ").append(repo.getTags().replace(",", ", ")).append("\n\n");
                }

                md.append("**Language:** ").append(repo.getLanguage() != null ? repo.getLanguage() : "Unknown");
                if (repo.isFork()) md.append(" | **Fork**");
                if (repo.isIsPrivate()) md.append(" | **Private**");
                md.append("\n\n");

                if (includeDocs && repo.getDocsJson() != null) {
                    try {
                        var docs = mapper.readValue(repo.getDocsJson(), new TypeReference<List<Map<String, String>>>() {});
                        if (!docs.isEmpty()) {
                            md.append("**Documentation:**\n\n");
                            for (var doc : docs) {
                                String icon = switch (doc.getOrDefault("category", "")) {
                                    case "readme" -> "📖";
                                    case "docs" -> "📄";
                                    case "changelog" -> "📋";
                                    case "contributing" -> "🤝";
                                    case "license" -> "⚖️";
                                    default -> "📝";
                                };
                                md.append("- ").append(icon).append(" [").append(doc.get("path"))
                                        .append("](").append(doc.get("url")).append(")\n");
                            }
                            md.append("\n");
                        }
                    } catch (Exception ignored) {}
                }

                md.append("---\n\n");
            }
        }

        // Stats footer
        md.append("## Statistics\n\n");
        md.append("- **Total repositories:** ").append(repos.size()).append("\n");
        md.append("- **Owned:** ").append(repos.stream().filter(r -> !r.isFork()).count()).append("\n");
        md.append("- **Forks:** ").append(repos.stream().filter(ExemplarRepo::isFork).count()).append("\n");

        Map<String, Long> byLang = repos.stream()
                .filter(r -> r.getLanguage() != null && !r.getLanguage().isBlank())
                .collect(Collectors.groupingBy(ExemplarRepo::getLanguage, Collectors.counting()));
        if (!byLang.isEmpty()) {
            md.append("- **Languages:** ");
            md.append(byLang.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .map(e -> e.getKey() + " (" + e.getValue() + ")")
                    .collect(Collectors.joining(", ")));
            md.append("\n");
        }

        return ResponseEntity.ok(Map.of("markdown", md.toString(), "repoCount", repos.size()));
    }
}
