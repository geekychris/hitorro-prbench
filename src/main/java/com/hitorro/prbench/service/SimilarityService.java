package com.hitorro.prbench.service;

import com.hitorro.prbench.entity.*;
import com.hitorro.prbench.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Computes pairwise similarity between review comments and original comments
 * using multiple strategies: exact match, file+line, Jaro-Winkler text, and Winnowing hash.
 */
@Service
public class SimilarityService {

    private static final Logger log = LoggerFactory.getLogger(SimilarityService.class);

    private final ReviewCommentRepository reviewCommentRepo;
    private final OriginalCommentRepository originalCommentRepo;
    private final CommentSimilarityRepository similarityRepo;

    @Value("${app.similarity.text-similarity-threshold:0.8}")
    private double textThreshold;

    @Value("${app.similarity.winnowing-k:5}")
    private int winnowingK;

    @Value("${app.similarity.winnowing-w:4}")
    private int winnowingW;

    public SimilarityService(ReviewCommentRepository reviewCommentRepo,
                             OriginalCommentRepository originalCommentRepo,
                             CommentSimilarityRepository similarityRepo) {
        this.reviewCommentRepo = reviewCommentRepo;
        this.originalCommentRepo = originalCommentRepo;
        this.similarityRepo = similarityRepo;
    }

    /** Analyze similarities for a run, comparing bot comments to original comments. */
    public List<CommentSimilarity> analyzeSimilarities(Long runId, List<SuitePr> suitePrs) {
        List<CommentSimilarity> results = new ArrayList<>();
        var botComments = reviewCommentRepo.findByRunId(runId);

        for (SuitePr suitePr : suitePrs) {
            var originals = originalCommentRepo.findBySuitePrId(suitePr.getId());
            var botForPr = botComments.stream()
                    .filter(c -> c.getReplayPr().getSuitePr().getId().equals(suitePr.getId()))
                    .toList();

            for (ReviewComment bot : botForPr) {
                for (OriginalComment orig : originals) {
                    // Exact match
                    if (Objects.equals(bot.getBodyNormalized(), orig.getBodyNormalized())) {
                        results.add(makeSimilarity(bot.getId(), "REVIEW", orig.getId(), "ORIGINAL",
                                "EXACT_MATCH", 1.0, true));
                    }

                    // File + line match
                    if (bot.getFilePath() != null && bot.getFilePath().equals(orig.getFilePath())
                            && bot.getLineNumber() != null && bot.getLineNumber().equals(orig.getLineNumber())) {
                        results.add(makeSimilarity(bot.getId(), "REVIEW", orig.getId(), "ORIGINAL",
                                "FILE_LINE", 1.0, true));
                    }

                    // Jaro-Winkler text similarity
                    double textSim = TextNormalizer.jaroWinklerSimilarity(
                            bot.getBodyNormalized(), orig.getBodyNormalized());
                    if (textSim > 0.5) {
                        results.add(makeSimilarity(bot.getId(), "REVIEW", orig.getId(), "ORIGINAL",
                                "NORMALIZED_TEXT", textSim, textSim >= textThreshold));
                    }

                    // Winnowing hash similarity
                    double winnowSim = TextNormalizer.winnowingSimilarity(
                            bot.getFingerprintHash(), orig.getFingerprintHash());
                    if (winnowSim > 0.3) {
                        results.add(makeSimilarity(bot.getId(), "REVIEW", orig.getId(), "ORIGINAL",
                                "WINNOWING", winnowSim, winnowSim >= 0.5));
                    }
                }
            }
        }

        similarityRepo.saveAll(results);
        log.info("Computed {} similarities for run {}", results.size(), runId);
        return results;
    }

    private CommentSimilarity makeSimilarity(Long aId, String aType, Long bId, String bType,
                                              String strategy, double score, boolean isMatch) {
        var cs = new CommentSimilarity();
        cs.setCommentAId(aId);
        cs.setCommentAType(aType);
        cs.setCommentBId(bId);
        cs.setCommentBType(bType);
        cs.setStrategy(strategy);
        cs.setScore(score);
        cs.setMatch(isMatch);
        return cs;
    }
}
