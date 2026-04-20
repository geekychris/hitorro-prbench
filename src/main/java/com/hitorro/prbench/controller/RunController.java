package com.hitorro.prbench.controller;

import com.hitorro.prbench.entity.*;
import com.hitorro.prbench.repository.*;
import com.hitorro.prbench.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/runs")
@CrossOrigin(origins = "*")
public class RunController {

    private final BenchmarkRunRepository runRepo;
    private final BenchmarkSuiteRepository suiteRepo;
    private final BotRepository botRepo;
    private final ReplayPrRepository replayPrRepo;
    private final ReviewCommentRepository commentRepo;
    private final BotSnapshotRepository snapshotRepo;
    private final SuitePrRepository suitePrRepo;
    private final RunOrchestrator orchestrator;
    private final ReplayEngine replayEngine;
    private final SimilarityService similarityService;

    public RunController(BenchmarkRunRepository runRepo, BenchmarkSuiteRepository suiteRepo,
                         BotRepository botRepo, ReplayPrRepository replayPrRepo,
                         ReviewCommentRepository commentRepo, BotSnapshotRepository snapshotRepo,
                         SuitePrRepository suitePrRepo, RunOrchestrator orchestrator,
                         ReplayEngine replayEngine, SimilarityService similarityService) {
        this.runRepo = runRepo;
        this.suiteRepo = suiteRepo;
        this.botRepo = botRepo;
        this.replayPrRepo = replayPrRepo;
        this.commentRepo = commentRepo;
        this.snapshotRepo = snapshotRepo;
        this.suitePrRepo = suitePrRepo;
        this.orchestrator = orchestrator;
        this.replayEngine = replayEngine;
        this.similarityService = similarityService;
    }

    @GetMapping
    public List<BenchmarkRun> list(@RequestParam(required = false) Long suiteId) {
        if (suiteId != null) return runRepo.findBySuiteId(suiteId);
        return runRepo.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        Long suiteId = ((Number) body.get("suiteId")).longValue();
        return suiteRepo.findById(suiteId).map(suite -> {
            var run = new BenchmarkRun();
            run.setSuite(suite);
            run.setName((String) body.getOrDefault("name", "Run " + System.currentTimeMillis()));
            if (body.containsKey("concurrency"))
                run.setConcurrency(((Number) body.get("concurrency")).intValue());
            if (body.containsKey("goldenDatasetEnabled"))
                run.setGoldenDatasetEnabled((Boolean) body.get("goldenDatasetEnabled"));

            @SuppressWarnings("unchecked")
            List<Number> botIds = (List<Number>) body.get("botIds");
            List<Bot> bots = new ArrayList<>();
            for (Number botId : botIds) {
                botRepo.findById(botId.longValue()).ifPresent(bots::add);
            }
            run.setBots(bots);
            run = runRepo.save(run);

            // Start async execution
            orchestrator.executeRun(run);

            return ResponseEntity.ok(run);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return runRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<?> progress(@PathVariable Long id) {
        var statusCounts = replayPrRepo.countByRunIdGroupByStatus(id);
        Map<String, Long> progress = new LinkedHashMap<>();
        for (Object[] row : statusCounts) {
            progress.put((String) row[0], (Long) row[1]);
        }
        return ResponseEntity.ok(progress);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        return runRepo.findById(id).map(run -> {
            orchestrator.cancelRun(run);
            return ResponseEntity.ok(Map.of("cancelled", true));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/replay-prs")
    public List<ReplayPr> replayPrs(@PathVariable Long id) {
        return replayPrRepo.findByRunId(id);
    }

    @GetMapping("/{id}/bot-snapshots")
    public List<BotSnapshot> botSnapshots(@PathVariable Long id) {
        return snapshotRepo.findByRunId(id);
    }

    @PostMapping("/{id}/cleanup")
    public ResponseEntity<?> cleanup(@PathVariable Long id) {
        return runRepo.findById(id).map(run -> {
            var repo = run.getSuite().getExemplarRepo();
            var replays = replayPrRepo.findByRunId(id);
            int cleaned = 0;
            for (var rp : replays) {
                replayEngine.cleanup(repo, rp);
                cleaned++;
            }
            return ResponseEntity.ok(Map.of("cleaned", cleaned));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/replay-prs/{replayPrId}/comments")
    public List<ReviewComment> replayPrComments(@PathVariable Long replayPrId) {
        return commentRepo.findByReplayPrId(replayPrId);
    }

    @GetMapping("/{runId}/similarities")
    public ResponseEntity<?> similarities(@PathVariable Long runId) {
        return runRepo.findById(runId).map(run -> {
            var suitePrs = suitePrRepo.findBySuiteId(run.getSuite().getId());
            var results = similarityService.analyzeSimilarities(runId, suitePrs);
            return ResponseEntity.ok(results);
        }).orElse(ResponseEntity.notFound().build());
    }
}
