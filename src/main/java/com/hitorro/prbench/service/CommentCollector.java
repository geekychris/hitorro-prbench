package com.hitorro.prbench.service;

import tools.jackson.databind.JsonNode;
import com.hitorro.prbench.entity.*;
import com.hitorro.prbench.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.hitorro.util.json.JsonValues;

/**
 * Collects review comments from GitHub PRs (both bot-generated replay PR comments
 * and original human comments from exemplar PRs).
 */
@Service
public class CommentCollector {

    private static final Logger log = LoggerFactory.getLogger(CommentCollector.class);

    private final GitHubApiService github;
    private final ReviewCommentRepository reviewCommentRepo;
    private final OriginalCommentRepository originalCommentRepo;

    public CommentCollector(GitHubApiService github,
                            ReviewCommentRepository reviewCommentRepo,
                            OriginalCommentRepository originalCommentRepo) {
        this.github = github;
        this.reviewCommentRepo = reviewCommentRepo;
        this.originalCommentRepo = originalCommentRepo;
    }

    /** Collect comments from a replay PR in the mirror repo. */
    public List<ReviewComment> collectReplayPrComments(ReplayPr replayPr,
                                                        String owner, String repo) {
        List<ReviewComment> collected = new ArrayList<>();
        try {
            int prNumber = replayPr.getMirrorPrNumber();
            if (prNumber <= 0) return collected;

            // Inline review comments
            for (JsonNode c : github.listPrReviewComments(owner, repo, prNumber)) {
                collected.add(toReviewComment(c, replayPr, "REVIEW_COMMENT"));
            }

            // PR reviews with body
            for (JsonNode r : github.listPrReviews(owner, repo, prNumber)) {
                String body = r.has("body") ? JsonValues.asText(r.get("body"), "") : "";
                if (!body.isBlank()) {
                    collected.add(toReviewComment(r, replayPr, "REVIEW"));
                }
            }

            // Issue comments
            for (JsonNode c : github.listIssueComments(owner, repo, prNumber)) {
                collected.add(toReviewComment(c, replayPr, "PR_COMMENT"));
            }

            reviewCommentRepo.saveAll(collected);
            log.info("Collected {} comments from replay PR #{}", collected.size(), prNumber);
        } catch (Exception e) {
            log.error("Failed to collect comments for replay PR {}: {}", replayPr.getId(), e.getMessage());
        }
        return collected;
    }

    /** Collect original human comments from an exemplar PR. */
    public List<OriginalComment> collectOriginalComments(SuitePr suitePr,
                                                          String owner, String repo) {
        List<OriginalComment> collected = new ArrayList<>();
        try {
            int prNumber = suitePr.getOriginalPrNumber();

            for (JsonNode c : github.listPrReviewComments(owner, repo, prNumber)) {
                collected.add(toOriginalComment(c, suitePr, "REVIEW_COMMENT"));
            }

            for (JsonNode r : github.listPrReviews(owner, repo, prNumber)) {
                String body = r.has("body") ? JsonValues.asText(r.get("body"), "") : "";
                if (!body.isBlank()) {
                    collected.add(toOriginalComment(r, suitePr, "REVIEW"));
                }
            }

            for (JsonNode c : github.listIssueComments(owner, repo, prNumber)) {
                collected.add(toOriginalComment(c, suitePr, "PR_COMMENT"));
            }

            originalCommentRepo.saveAll(collected);
            log.info("Collected {} original comments from PR #{}", collected.size(), prNumber);
        } catch (Exception e) {
            log.error("Failed to collect original comments for PR {}: {}", suitePr.getId(), e.getMessage());
        }
        return collected;
    }

    private ReviewComment toReviewComment(JsonNode node, ReplayPr replayPr, String commentType) {
        var rc = new ReviewComment();
        rc.setReplayPr(replayPr);
        rc.setSource("BOT");
        rc.setBot(replayPr.getBot());
        rc.setGithubCommentId(node.has("id") ? JsonValues.asLong(node.get("id")) : 0);
        rc.setCommentType(commentType);
        rc.setFilePath(node.has("path") ? JsonValues.asText(node.get("path")) : null);
        rc.setLineNumber(node.has("line") ? JsonValues.asInt(node.get("line")) : null);
        rc.setDiffHunk(node.has("diff_hunk") ? JsonValues.asText(node.get("diff_hunk")) : null);
        String body = node.has("body") ? JsonValues.asText(node.get("body"), "") : "";
        rc.setBody(body);
        rc.setBodyNormalized(TextNormalizer.normalize(body));
        rc.setFingerprintHash(TextNormalizer.winnowingHash(body, 5, 4));
        if (node.has("created_at")) {
            try { rc.setGithubCreatedAt(Instant.parse(JsonValues.asText(node.get("created_at")))); }
            catch (Exception ignored) {}
        }
        return rc;
    }

    private OriginalComment toOriginalComment(JsonNode node, SuitePr suitePr, String commentType) {
        var oc = new OriginalComment();
        oc.setSuitePr(suitePr);
        oc.setAuthor(node.has("user") ? JsonValues.asText(node.get("user").get("login")) : "unknown");
        oc.setGithubCommentId(node.has("id") ? JsonValues.asLong(node.get("id")) : 0);
        oc.setCommentType(commentType);
        oc.setFilePath(node.has("path") ? JsonValues.asText(node.get("path")) : null);
        oc.setLineNumber(node.has("line") ? JsonValues.asInt(node.get("line")) : null);
        oc.setDiffHunk(node.has("diff_hunk") ? JsonValues.asText(node.get("diff_hunk")) : null);
        String body = node.has("body") ? JsonValues.asText(node.get("body"), "") : "";
        oc.setBody(body);
        oc.setBodyNormalized(TextNormalizer.normalize(body));
        oc.setFingerprintHash(TextNormalizer.winnowingHash(body, 5, 4));
        if (node.has("created_at")) {
            try { oc.setGithubCreatedAt(Instant.parse(JsonValues.asText(node.get("created_at")))); }
            catch (Exception ignored) {}
        }
        return oc;
    }
}
