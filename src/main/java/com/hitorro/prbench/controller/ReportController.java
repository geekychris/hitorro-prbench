package com.hitorro.prbench.controller;

import com.hitorro.prbench.repository.BenchmarkRunRepository;
import com.hitorro.prbench.service.ReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    private final ReportingService reportingService;
    private final BenchmarkRunRepository runRepo;

    public ReportController(ReportingService reportingService, BenchmarkRunRepository runRepo) {
        this.reportingService = reportingService;
        this.runRepo = runRepo;
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<?> runReport(@PathVariable Long runId) {
        return ResponseEntity.ok(reportingService.generateRunReport(runId));
    }

    @GetMapping("/runs/{runId}/comparison")
    public ResponseEntity<?> goldenComparison(@PathVariable Long runId) {
        return runRepo.findById(runId).map(run ->
                ResponseEntity.ok(reportingService.compareWithGoldenDataset(runId, run.getSuite().getId()))
        ).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/bots/{botId}/trend")
    public ResponseEntity<?> botTrend(@PathVariable Long botId,
                                       @RequestParam(required = false) Long suiteId,
                                       @RequestParam(defaultValue = "10") int limit) {
        if (suiteId == null) return ResponseEntity.badRequest().body(Map.of("error", "suiteId required"));
        return ResponseEntity.ok(reportingService.getBotTrend(botId, suiteId, limit));
    }

    @GetMapping("/runs/{runAId}/significance")
    public ResponseEntity<?> significance(@PathVariable Long runAId,
                                           @RequestParam Long compareRunId) {
        return runRepo.findById(runAId).map(run ->
                ResponseEntity.ok(reportingService.testSignificance(runAId, compareRunId, run.getSuite().getId()))
        ).orElse(ResponseEntity.notFound().build());
    }
}
