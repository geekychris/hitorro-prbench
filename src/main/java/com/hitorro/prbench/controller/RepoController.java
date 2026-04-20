package com.hitorro.prbench.controller;

import com.hitorro.prbench.entity.ExemplarRepo;
import com.hitorro.prbench.repository.ExemplarRepoRepository;
import com.hitorro.prbench.service.GitHubApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repos")
@CrossOrigin(origins = "*")
public class RepoController {

    private final ExemplarRepoRepository repoRepo;
    private final GitHubApiService github;

    public RepoController(ExemplarRepoRepository repoRepo, GitHubApiService github) {
        this.repoRepo = repoRepo;
        this.github = github;
    }

    @GetMapping
    public List<ExemplarRepo> list() { return repoRepo.findAll(); }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        String githubUrl = body.get("githubUrl");
        String name = body.getOrDefault("name", "");
        if (githubUrl == null) return ResponseEntity.badRequest().body(Map.of("error", "githubUrl required"));

        // Parse owner/repo from URL
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

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return repoRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return repoRepo.findById(id).map(repo -> {
            if (body.containsKey("name")) repo.setName(body.get("name"));
            if (body.containsKey("mirrorOrg")) repo.setMirrorOrg(body.get("mirrorOrg"));
            if (body.containsKey("mirrorRepoName")) repo.setMirrorRepoName(body.get("mirrorRepoName"));
            if (body.containsKey("defaultBranch")) repo.setDefaultBranch(body.get("defaultBranch"));
            return ResponseEntity.ok(repoRepo.save(repo));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        repoRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
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
}
