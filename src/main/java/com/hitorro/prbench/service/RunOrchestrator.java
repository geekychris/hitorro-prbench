package com.hitorro.prbench.service;

import com.hitorro.prbench.entity.*;
import com.hitorro.prbench.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Orchestrates benchmark runs: creates replay PRs, waits for bot reviews,
 * collects comments, and updates status.
 */
@Service
public class RunOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RunOrchestrator.class);

    private final BenchmarkRunRepository runRepo;
    private final SuitePrRepository suitePrRepo;
    private final ReplayPrRepository replayPrRepo;
    private final BotSnapshotRepository snapshotRepo;
    private final ReplayEngine replayEngine;
    private final CommentCollector commentCollector;
    private final GitHubApiService github;

    @Value("${app.github.poll-interval-seconds:30}")
    private int pollIntervalSeconds;

    @Value("${app.run.max-concurrency:10}")
    private int maxConcurrency;

    public RunOrchestrator(BenchmarkRunRepository runRepo,
                           SuitePrRepository suitePrRepo,
                           ReplayPrRepository replayPrRepo,
                           BotSnapshotRepository snapshotRepo,
                           ReplayEngine replayEngine,
                           CommentCollector commentCollector,
                           GitHubApiService github) {
        this.runRepo = runRepo;
        this.suitePrRepo = suitePrRepo;
        this.replayPrRepo = replayPrRepo;
        this.snapshotRepo = snapshotRepo;
        this.replayEngine = replayEngine;
        this.commentCollector = commentCollector;
        this.github = github;
    }

    @Async
    public void executeRun(BenchmarkRun run) {
        try {
            run.setStatus("RUNNING");
            run.setStartedAt(Instant.now());
            runRepo.save(run);

            var suite = run.getSuite();
            var repo = suite.getExemplarRepo();
            var suitePrs = suitePrRepo.findBySuiteId(suite.getId());
            var bots = run.getBots();

            // Snapshot bot configs
            for (Bot bot : bots) {
                var snapshot = new BotSnapshot();
                snapshot.setRun(run);
                snapshot.setBot(bot);
                snapshot.setName(bot.getName());
                snapshot.setWorkflowFileName(bot.getWorkflowFileName());
                snapshot.setWorkflowContent(bot.getWorkflowContent());
                snapshot.setWaitStrategy(bot.getWaitStrategy());
                snapshot.setTimeoutSeconds(bot.getTimeoutSeconds());
                snapshotRepo.save(snapshot);
            }

            // Create all replay PR records
            for (SuitePr suitePr : suitePrs) {
                for (Bot bot : bots) {
                    var replayPr = new ReplayPr();
                    replayPr.setRun(run);
                    replayPr.setSuitePr(suitePr);
                    replayPr.setBot(bot);
                    replayPr.setStatus("PENDING");
                    replayPrRepo.save(replayPr);
                }
            }

            // Execute replays with concurrency control
            int concurrency = Math.min(run.getConcurrency(), maxConcurrency);
            Semaphore semaphore = new Semaphore(concurrency);
            List<ReplayPr> allReplays = replayPrRepo.findByRunId(run.getId());

            for (ReplayPr replayPr : allReplays) {
                if ("CANCELLED".equals(run.getStatus())) break;

                semaphore.acquire();
                try {
                    processReplayPr(replayPr, repo);
                } finally {
                    semaphore.release();
                }
            }

            // Refresh run status
            run = runRepo.findById(run.getId()).orElse(run);
            if (!"CANCELLED".equals(run.getStatus())) {
                run.setStatus("COMPLETED");
            }
            run.setCompletedAt(Instant.now());
            runRepo.save(run);

            log.info("Benchmark run {} completed", run.getId());

        } catch (Exception e) {
            log.error("Run {} failed: {}", run.getId(), e.getMessage(), e);
            run.setStatus("FAILED");
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(Instant.now());
            runRepo.save(run);
        }
    }

    private void processReplayPr(ReplayPr replayPr, ExemplarRepo repo) {
        try {
            // Phase 1: Create branches and PR
            replayPr.setStatus("CREATING_BRANCHES");
            replayPrRepo.save(replayPr);

            var result = replayEngine.replay(replayPr.getSuitePr(), replayPr.getBot(), repo);
            if (!result.success()) {
                replayPr.setStatus("FAILED");
                replayPr.setErrorMessage(result.error());
                replayPr.setCompletedAt(Instant.now());
                replayPrRepo.save(replayPr);
                return;
            }

            replayPr.setMirrorPrNumber(result.prNumber());
            replayPr.setMirrorPrUrl(result.prUrl());
            replayPr.setBaseBranch(result.baseBranch());
            replayPr.setHeadBranch(result.headBranch());

            // Phase 2: Wait for bot
            replayPr.setStatus("WAITING_FOR_BOTS");
            replayPrRepo.save(replayPr);

            waitForBot(replayPr, repo);

            // Phase 3: Collect comments
            replayPr.setStatus("COLLECTING_COMMENTS");
            replayPrRepo.save(replayPr);

            String mirrorOrg = repo.getMirrorOrg() != null ? repo.getMirrorOrg() : repo.getOwner();
            String mirrorRepo = repo.getMirrorRepoName() != null ? repo.getMirrorRepoName() : repo.getRepoName() + "-mirror";
            commentCollector.collectReplayPrComments(replayPr, mirrorOrg, mirrorRepo);

            // Done
            replayPr.setStatus("COMPLETED");
            replayPr.setCompletedAt(Instant.now());
            replayPrRepo.save(replayPr);

        } catch (Exception e) {
            log.error("Replay PR {} failed: {}", replayPr.getId(), e.getMessage());
            replayPr.setStatus("FAILED");
            replayPr.setErrorMessage(e.getMessage());
            replayPr.setCompletedAt(Instant.now());
            replayPrRepo.save(replayPr);
        }
    }

    private void waitForBot(ReplayPr replayPr, ExemplarRepo repo) throws Exception {
        Bot bot = replayPr.getBot();
        int timeout = bot.getTimeoutSeconds();
        String strategy = bot.getWaitStrategy();
        String mirrorOrg = repo.getMirrorOrg() != null ? repo.getMirrorOrg() : repo.getOwner();
        String mirrorRepo = repo.getMirrorRepoName() != null ? repo.getMirrorRepoName() : repo.getRepoName() + "-mirror";

        long deadline = System.currentTimeMillis() + (timeout * 1000L);

        while (System.currentTimeMillis() < deadline) {
            boolean checksReady = false;
            boolean reviewsReady = false;

            if ("CHECKS".equals(strategy) || "BOTH".equals(strategy)) {
                try {
                    // Get head branch SHA for check runs
                    var pr = github.getPullRequest(mirrorOrg, mirrorRepo, replayPr.getMirrorPrNumber());
                    String sha = pr.get("head").get("sha").asText();
                    var checkRuns = github.listCheckRuns(mirrorOrg, mirrorRepo, sha);
                    int total = checkRuns.get("total_count").asInt();
                    if (total > 0) {
                        boolean allComplete = true;
                        for (var cr : checkRuns.get("check_runs")) {
                            if (!"completed".equals(cr.get("status").asText())) {
                                allComplete = false;
                                break;
                            }
                        }
                        checksReady = allComplete;
                    }
                } catch (Exception e) {
                    log.debug("Check run query failed: {}", e.getMessage());
                }
            }

            if ("REVIEWS".equals(strategy) || "BOTH".equals(strategy)) {
                try {
                    var reviews = github.listPrReviews(mirrorOrg, mirrorRepo, replayPr.getMirrorPrNumber());
                    reviewsReady = !reviews.isEmpty();
                } catch (Exception e) {
                    log.debug("Review query failed: {}", e.getMessage());
                }
            }

            boolean ready = switch (strategy) {
                case "CHECKS" -> checksReady;
                case "REVIEWS" -> reviewsReady;
                default -> checksReady && reviewsReady;
            };

            if (ready) return;

            Thread.sleep(pollIntervalSeconds * 1000L);
        }

        log.warn("Timeout waiting for bot {} on PR #{}", bot.getName(), replayPr.getMirrorPrNumber());
    }

    public void cancelRun(BenchmarkRun run) {
        run.setStatus("CANCELLED");
        run.setCompletedAt(Instant.now());
        runRepo.save(run);
    }
}
