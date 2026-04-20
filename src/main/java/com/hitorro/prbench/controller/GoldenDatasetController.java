package com.hitorro.prbench.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.prbench.entity.*;
import com.hitorro.prbench.repository.*;
import com.hitorro.prbench.service.TextNormalizer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/golden-dataset")
@CrossOrigin(origins = "*")
public class GoldenDatasetController {

    private final GoldenDatasetEntryRepository goldenRepo;
    private final ReviewCommentRepository reviewCommentRepo;
    private final OriginalCommentRepository originalCommentRepo;
    private final SuitePrRepository suitePrRepo;

    public GoldenDatasetController(GoldenDatasetEntryRepository goldenRepo,
                                   ReviewCommentRepository reviewCommentRepo,
                                   OriginalCommentRepository originalCommentRepo,
                                   SuitePrRepository suitePrRepo) {
        this.goldenRepo = goldenRepo;
        this.reviewCommentRepo = reviewCommentRepo;
        this.originalCommentRepo = originalCommentRepo;
        this.suitePrRepo = suitePrRepo;
    }

    @GetMapping
    public List<GoldenDatasetEntry> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return activeOnly ? goldenRepo.findByActiveTrue() : goldenRepo.findAll();
    }

    @PostMapping("/promote")
    public ResponseEntity<?> promote(@RequestBody Map<String, Object> body) {
        Long commentId = ((Number) body.get("commentId")).longValue();
        String commentType = (String) body.getOrDefault("commentTableType", "REVIEW");

        String filePath = null, bodyText = null;
        Integer lineNumber = null;
        SuitePr suitePr = null;

        if ("REVIEW".equals(commentType)) {
            var comment = reviewCommentRepo.findById(commentId).orElse(null);
            if (comment == null) return ResponseEntity.notFound().build();
            filePath = comment.getFilePath();
            lineNumber = comment.getLineNumber();
            bodyText = comment.getBody();
            suitePr = comment.getReplayPr().getSuitePr();
        } else {
            var comment = originalCommentRepo.findById(commentId).orElse(null);
            if (comment == null) return ResponseEntity.notFound().build();
            filePath = comment.getFilePath();
            lineNumber = comment.getLineNumber();
            bodyText = comment.getBody();
            suitePr = comment.getSuitePr();
        }

        var entry = new GoldenDatasetEntry();
        entry.setSuitePr(suitePr);
        entry.setSourceCommentId(commentId);
        entry.setSourceCommentType(commentType);
        entry.setFilePath(filePath);
        entry.setLineNumber(lineNumber);
        entry.setIssueType((String) body.get("issueType"));
        entry.setDescription((String) body.get("description"));
        entry.setCanonicalBody(TextNormalizer.normalize(bodyText));
        return ResponseEntity.ok(goldenRepo.save(entry));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return goldenRepo.findById(id).map(entry -> {
            if (body.containsKey("issueType")) entry.setIssueType((String) body.get("issueType"));
            if (body.containsKey("description")) entry.setDescription((String) body.get("description"));
            if (body.containsKey("canonicalBody")) entry.setCanonicalBody((String) body.get("canonicalBody"));
            if (body.containsKey("active")) entry.setActive((Boolean) body.get("active"));
            if (body.containsKey("includedByDefault")) entry.setIncludedByDefault((Boolean) body.get("includedByDefault"));
            return ResponseEntity.ok(goldenRepo.save(entry));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        return goldenRepo.findById(id).map(entry -> {
            entry.setActive(false);
            goldenRepo.save(entry);
            return ResponseEntity.ok(Map.of("deactivated", true));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/export")
    public ResponseEntity<?> export() {
        return ResponseEntity.ok(goldenRepo.findByActiveTrue());
    }
}
