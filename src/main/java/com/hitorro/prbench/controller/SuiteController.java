package com.hitorro.prbench.controller;

import com.hitorro.prbench.entity.*;
import com.hitorro.prbench.repository.*;
import com.hitorro.prbench.service.CommentCollector;
import com.hitorro.prbench.service.GitHubApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/suites")
@CrossOrigin(origins = "*")
public class SuiteController {

    private final BenchmarkSuiteRepository suiteRepo;
    private final SuitePrRepository suitePrRepo;
    private final ExemplarRepoRepository repoRepo;
    private final GitHubApiService github;
    private final CommentCollector commentCollector;

    public SuiteController(BenchmarkSuiteRepository suiteRepo, SuitePrRepository suitePrRepo,
                           ExemplarRepoRepository repoRepo, GitHubApiService github,
                           CommentCollector commentCollector) {
        this.suiteRepo = suiteRepo;
        this.suitePrRepo = suitePrRepo;
        this.repoRepo = repoRepo;
        this.github = github;
        this.commentCollector = commentCollector;
    }

    @GetMapping
    public List<BenchmarkSuite> list(@RequestParam(required = false) Long repoId) {
        if (repoId != null) return suiteRepo.findByExemplarRepoId(repoId);
        return suiteRepo.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        Long repoId = ((Number) body.get("exemplarRepoId")).longValue();
        return repoRepo.findById(repoId).map(repo -> {
            var suite = new BenchmarkSuite();
            suite.setName((String) body.get("name"));
            suite.setDescription((String) body.get("description"));
            suite.setExemplarRepo(repo);
            return ResponseEntity.ok(suiteRepo.save(suite));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return suiteRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        suiteRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping("/{id}/prs")
    public List<SuitePr> listPrs(@PathVariable Long id) {
        return suitePrRepo.findBySuiteId(id);
    }

    @PostMapping("/{id}/prs")
    public ResponseEntity<?> addPr(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int prNumber = ((Number) body.get("originalPrNumber")).intValue();
        return suiteRepo.findById(id).map(suite -> {
            try {
                var repo = suite.getExemplarRepo();
                var prNode = github.getPullRequest(repo.getOwner(), repo.getRepoName(), prNumber);

                var suitePr = new SuitePr();
                suitePr.setSuite(suite);
                suitePr.setOriginalPrNumber(prNumber);
                suitePr.setTitle(prNode.get("title").asText());
                suitePr.setDescription(prNode.has("body") ? prNode.get("body").asText("") : "");
                suitePr.setBaseBranch(prNode.get("base").get("ref").asText());
                suitePr.setBaseCommitSha(prNode.get("base").get("sha").asText());
                suitePr.setHeadBranch(prNode.get("head").get("ref").asText());
                suitePr.setHeadCommitSha(prNode.get("head").get("sha").asText());
                suitePr.setAuthor(prNode.get("user").get("login").asText());
                if (prNode.has("merged_at") && !prNode.get("merged_at").isNull()) {
                    suitePr.setMergedAt(Instant.parse(prNode.get("merged_at").asText()));
                }
                suitePr.setFilesChanged(prNode.has("changed_files") ? prNode.get("changed_files").asInt() : 0);
                suitePr.setAdditions(prNode.has("additions") ? prNode.get("additions").asInt() : 0);
                suitePr.setDeletions(prNode.has("deletions") ? prNode.get("deletions").asInt() : 0);

                return ResponseEntity.ok(suitePrRepo.save(suitePr));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{suiteId}/prs/{prId}")
    public ResponseEntity<?> removePr(@PathVariable Long suiteId, @PathVariable Long prId) {
        suitePrRepo.deleteById(prId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @PostMapping("/suite-prs/{suitePrId}/collect-original-comments")
    public ResponseEntity<?> collectOriginalComments(@PathVariable Long suitePrId) {
        return suitePrRepo.findById(suitePrId).map(suitePr -> {
            var repo = suitePr.getSuite().getExemplarRepo();
            var comments = commentCollector.collectOriginalComments(suitePr, repo.getOwner(), repo.getRepoName());
            return ResponseEntity.ok(Map.of("collected", comments.size()));
        }).orElse(ResponseEntity.notFound().build());
    }
}
