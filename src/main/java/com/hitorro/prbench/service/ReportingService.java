package com.hitorro.prbench.service;

import com.hitorro.prbench.entity.*;
import com.hitorro.prbench.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Generates reports: run summaries, golden dataset comparison (precision/recall/F1),
 * bot trends, and statistical significance tests.
 */
@Service
public class ReportingService {

    private final ReviewCommentRepository reviewCommentRepo;
    private final GoldenDatasetEntryRepository goldenRepo;
    private final GradingRepository gradingRepo;
    private final ReplayPrRepository replayPrRepo;
    private final BenchmarkRunRepository runRepo;

    public ReportingService(ReviewCommentRepository reviewCommentRepo,
                            GoldenDatasetEntryRepository goldenRepo,
                            GradingRepository gradingRepo,
                            ReplayPrRepository replayPrRepo,
                            BenchmarkRunRepository runRepo) {
        this.reviewCommentRepo = reviewCommentRepo;
        this.goldenRepo = goldenRepo;
        this.gradingRepo = gradingRepo;
        this.replayPrRepo = replayPrRepo;
        this.runRepo = runRepo;
    }

    /** Generate run report with per-bot stats. */
    public Map<String, Object> generateRunReport(Long runId) {
        Map<String, Object> report = new LinkedHashMap<>();
        var replays = replayPrRepo.findByRunId(runId);
        var comments = reviewCommentRepo.findByRunId(runId);

        // Group by bot
        Map<Long, List<ReviewComment>> commentsByBot = new HashMap<>();
        for (var c : comments) {
            commentsByBot.computeIfAbsent(c.getBot().getId(), k -> new ArrayList<>()).add(c);
        }

        List<Map<String, Object>> botStats = new ArrayList<>();
        for (var entry : commentsByBot.entrySet()) {
            var botComments = entry.getValue();
            String botName = botComments.get(0).getBot().getName();

            // Get gradings for these comments
            List<Long> commentIds = botComments.stream().map(ReviewComment::getId).toList();
            var gradings = gradingRepo.findByCommentIdInAndCommentTableType(commentIds, "REVIEW");

            Map<String, Long> verdicts = new LinkedHashMap<>();
            for (var g : gradings) {
                verdicts.merge(g.getVerdict(), 1L, Long::sum);
            }

            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("botId", entry.getKey());
            stat.put("botName", botName);
            stat.put("totalComments", botComments.size());
            stat.put("gradedCount", gradings.size());
            stat.put("verdicts", verdicts);
            botStats.add(stat);
        }

        report.put("runId", runId);
        report.put("totalReplayPrs", replays.size());
        report.put("totalComments", comments.size());
        report.put("botStats", botStats);
        return report;
    }

    /** Compare bot comments against golden dataset entries. */
    public Map<String, Object> compareWithGoldenDataset(Long runId, Long suiteId) {
        var goldenEntries = goldenRepo.findBySuitePrSuiteId(suiteId).stream()
                .filter(GoldenDatasetEntry::isActive).toList();
        var comments = reviewCommentRepo.findByRunId(runId);

        // Group comments by bot
        Map<Long, List<ReviewComment>> commentsByBot = new HashMap<>();
        for (var c : comments) {
            commentsByBot.computeIfAbsent(c.getBot().getId(), k -> new ArrayList<>()).add(c);
        }

        List<Map<String, Object>> botComparisons = new ArrayList<>();
        for (var entry : commentsByBot.entrySet()) {
            var botComments = entry.getValue();
            String botName = botComments.get(0).getBot().getName();

            int truePositives = 0;
            Set<Long> matchedGolden = new HashSet<>();

            for (var comment : botComments) {
                for (var golden : goldenEntries) {
                    if (matchedGolden.contains(golden.getId())) continue;
                    if (isMatch(comment, golden)) {
                        truePositives++;
                        matchedGolden.add(golden.getId());
                        break;
                    }
                }
            }

            int falsePositives = botComments.size() - truePositives;
            int falseNegatives = goldenEntries.size() - truePositives;

            double precision = botComments.isEmpty() ? 0 : (double) truePositives / botComments.size();
            double recall = goldenEntries.isEmpty() ? 0 : (double) truePositives / goldenEntries.size();
            double f1 = (precision + recall) == 0 ? 0 : 2 * precision * recall / (precision + recall);

            Map<String, Object> comp = new LinkedHashMap<>();
            comp.put("botId", entry.getKey());
            comp.put("botName", botName);
            comp.put("truePositives", truePositives);
            comp.put("falsePositives", falsePositives);
            comp.put("falseNegatives", falseNegatives);
            comp.put("precision", Math.round(precision * 1000.0) / 1000.0);
            comp.put("recall", Math.round(recall * 1000.0) / 1000.0);
            comp.put("f1Score", Math.round(f1 * 1000.0) / 1000.0);
            botComparisons.add(comp);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("goldenEntryCount", goldenEntries.size());
        result.put("botComparisons", botComparisons);
        return result;
    }

    /** McNemar's chi-squared test between two runs. */
    public Map<String, Object> testSignificance(Long runAId, Long runBId, Long suiteId) {
        var goldenEntries = goldenRepo.findBySuitePrSuiteId(suiteId).stream()
                .filter(GoldenDatasetEntry::isActive).toList();
        var commentsA = reviewCommentRepo.findByRunId(runAId);
        var commentsB = reviewCommentRepo.findByRunId(runBId);

        // For each golden entry, check if A and B found it
        int bothFound = 0, onlyA = 0, onlyB = 0, neither = 0;
        for (var golden : goldenEntries) {
            boolean foundA = commentsA.stream().anyMatch(c -> isMatch(c, golden));
            boolean foundB = commentsB.stream().anyMatch(c -> isMatch(c, golden));
            if (foundA && foundB) bothFound++;
            else if (foundA) onlyA++;
            else if (foundB) onlyB++;
            else neither++;
        }

        // McNemar's test statistic (with continuity correction)
        double chi2 = (onlyA + onlyB) == 0 ? 0 :
                Math.pow(Math.abs(onlyA - onlyB) - 1, 2) / (onlyA + onlyB);
        double pValue = 1.0 - normalCdf(Math.sqrt(chi2));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runAId", runAId);
        result.put("runBId", runBId);
        result.put("bothFound", bothFound);
        result.put("onlyA", onlyA);
        result.put("onlyB", onlyB);
        result.put("neither", neither);
        result.put("chiSquared", Math.round(chi2 * 1000.0) / 1000.0);
        result.put("pValue", Math.round(pValue * 10000.0) / 10000.0);
        result.put("significant", pValue < 0.05);
        return result;
    }

    /** Bot F1 trend over recent runs. */
    public List<Map<String, Object>> getBotTrend(Long botId, Long suiteId, int limit) {
        var runs = runRepo.findBySuiteIdOrderByCreatedAtDesc(suiteId);
        List<Map<String, Object>> trend = new ArrayList<>();

        for (var run : runs.subList(0, Math.min(limit, runs.size()))) {
            var comparison = compareWithGoldenDataset(run.getId(), suiteId);
            @SuppressWarnings("unchecked")
            var botComps = (List<Map<String, Object>>) comparison.get("botComparisons");
            for (var bc : botComps) {
                if (botId.equals(bc.get("botId"))) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("runId", run.getId());
                    point.put("runName", run.getName());
                    point.put("createdAt", run.getCreatedAt());
                    point.put("precision", bc.get("precision"));
                    point.put("recall", bc.get("recall"));
                    point.put("f1Score", bc.get("f1Score"));
                    trend.add(point);
                    break;
                }
            }
        }
        Collections.reverse(trend); // oldest first
        return trend;
    }

    private boolean isMatch(ReviewComment comment, GoldenDatasetEntry golden) {
        // File + line match
        if (comment.getFilePath() != null && comment.getFilePath().equals(golden.getFilePath())
                && comment.getLineNumber() != null && comment.getLineNumber().equals(golden.getLineNumber())) {
            return true;
        }
        // Normalized body substring match
        if (comment.getBodyNormalized() != null && golden.getCanonicalBody() != null) {
            String normalizedGolden = TextNormalizer.normalize(golden.getCanonicalBody());
            return comment.getBodyNormalized().contains(normalizedGolden)
                    || normalizedGolden.contains(comment.getBodyNormalized());
        }
        return false;
    }

    /** Approximation of normal CDF using Abramowitz & Stegun. */
    private static double normalCdf(double x) {
        if (x < 0) return 1.0 - normalCdf(-x);
        double t = 1.0 / (1.0 + 0.2316419 * x);
        double d = 0.3989422804014327;
        double p = d * Math.exp(-x * x / 2.0) *
                (t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 +
                        t * (-1.821255978 + t * 1.330274429)))));
        return 1.0 - p;
    }
}
