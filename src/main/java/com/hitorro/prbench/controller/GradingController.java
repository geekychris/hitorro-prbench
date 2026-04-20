package com.hitorro.prbench.controller;

import com.hitorro.prbench.entity.Grading;
import com.hitorro.prbench.entity.ReviewComment;
import com.hitorro.prbench.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class GradingController {

    private final GradingRepository gradingRepo;
    private final ReviewCommentRepository reviewCommentRepo;
    private final ReplayPrRepository replayPrRepo;

    public GradingController(GradingRepository gradingRepo,
                             ReviewCommentRepository reviewCommentRepo,
                             ReplayPrRepository replayPrRepo) {
        this.gradingRepo = gradingRepo;
        this.reviewCommentRepo = reviewCommentRepo;
        this.replayPrRepo = replayPrRepo;
    }

    @PostMapping("/gradings")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        var g = new Grading();
        g.setCommentId(((Number) body.get("commentId")).longValue());
        g.setCommentTableType((String) body.getOrDefault("commentTableType", "REVIEW"));
        g.setGraderType((String) body.getOrDefault("graderType", "HUMAN"));
        g.setGraderId((String) body.get("graderId"));
        g.setVerdict((String) body.get("verdict"));
        g.setSeverity((String) body.get("severity"));
        if (body.containsKey("stars")) g.setStars(((Number) body.get("stars")).intValue());
        if (body.containsKey("flagged")) g.setFlagged((Boolean) body.get("flagged"));
        g.setNotes((String) body.get("notes"));
        return ResponseEntity.ok(gradingRepo.save(g));
    }

    @PutMapping("/gradings/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return gradingRepo.findById(id).map(g -> {
            if (body.containsKey("verdict")) g.setVerdict((String) body.get("verdict"));
            if (body.containsKey("severity")) g.setSeverity((String) body.get("severity"));
            if (body.containsKey("stars")) g.setStars(((Number) body.get("stars")).intValue());
            if (body.containsKey("flagged")) g.setFlagged((Boolean) body.get("flagged"));
            if (body.containsKey("notes")) g.setNotes((String) body.get("notes"));
            return ResponseEntity.ok(gradingRepo.save(g));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/gradings/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        gradingRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping("/comments/{commentId}/gradings")
    public List<Grading> getForComment(@PathVariable Long commentId,
                                        @RequestParam(defaultValue = "REVIEW") String type) {
        return gradingRepo.findByCommentIdAndCommentTableType(commentId, type);
    }

    @PostMapping("/gradings/bulk")
    public ResponseEntity<?> bulkGrade(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> commentIds = (List<Number>) body.get("commentIds");
        String tableType = (String) body.getOrDefault("commentTableType", "REVIEW");
        String verdict = (String) body.get("verdict");
        String severity = (String) body.get("severity");
        String graderId = (String) body.get("graderId");

        List<Grading> gradings = new ArrayList<>();
        for (Number cid : commentIds) {
            var g = new Grading();
            g.setCommentId(cid.longValue());
            g.setCommentTableType(tableType);
            g.setGraderType("HUMAN");
            g.setGraderId(graderId);
            g.setVerdict(verdict);
            g.setSeverity(severity);
            if (body.containsKey("stars")) g.setStars(((Number) body.get("stars")).intValue());
            if (body.containsKey("flagged")) g.setFlagged((Boolean) body.get("flagged"));
            g.setNotes((String) body.get("notes"));
            gradings.add(g);
        }
        return ResponseEntity.ok(gradingRepo.saveAll(gradings));
    }

    @GetMapping("/grading-queue")
    public ResponseEntity<?> gradingQueue(@RequestParam(required = false) Long runId,
                                           @RequestParam(defaultValue = "50") int limit) {
        if (runId == null) return ResponseEntity.ok(List.of());

        var comments = reviewCommentRepo.findByRunId(runId);
        List<Long> commentIds = comments.stream().map(ReviewComment::getId).toList();
        var existingGradings = gradingRepo.findByCommentIdInAndCommentTableType(commentIds, "REVIEW");
        Set<Long> gradedIds = existingGradings.stream()
                .map(Grading::getCommentId).collect(Collectors.toSet());

        var ungraded = comments.stream()
                .filter(c -> !gradedIds.contains(c.getId()))
                .limit(limit)
                .toList();
        return ResponseEntity.ok(ungraded);
    }

    @GetMapping("/grading-progress")
    public ResponseEntity<?> gradingProgress(@RequestParam(required = false) Long runId) {
        if (runId == null) return ResponseEntity.ok(Map.of());
        var comments = reviewCommentRepo.findByRunId(runId);
        List<Long> commentIds = comments.stream().map(ReviewComment::getId).toList();
        var gradings = gradingRepo.findByCommentIdInAndCommentTableType(commentIds, "REVIEW");

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("totalComments", comments.size());
        progress.put("gradedCount", gradings.size());
        progress.put("ungradedCount", comments.size() - gradings.size());

        Map<String, Long> verdictCounts = new LinkedHashMap<>();
        for (var g : gradings) {
            verdictCounts.merge(g.getVerdict(), 1L, Long::sum);
        }
        progress.put("verdicts", verdictCounts);
        return ResponseEntity.ok(progress);
    }
}
