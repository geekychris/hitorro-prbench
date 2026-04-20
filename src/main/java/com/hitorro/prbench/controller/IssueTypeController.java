package com.hitorro.prbench.controller;

import com.hitorro.prbench.entity.IssueType;
import com.hitorro.prbench.repository.IssueTypeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/issue-types")
@CrossOrigin(origins = "*")
public class IssueTypeController {

    private final IssueTypeRepository issueTypeRepo;

    public IssueTypeController(IssueTypeRepository issueTypeRepo) {
        this.issueTypeRepo = issueTypeRepo;
    }

    @GetMapping
    public List<IssueType> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return activeOnly ? issueTypeRepo.findByActiveTrue() : issueTypeRepo.findAll();
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return issueTypeRepo.findDistinctCategories();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return issueTypeRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<?> getByCode(@PathVariable String code) {
        return issueTypeRepo.findByCode(code).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        var it = new IssueType();
        it.setCode(body.get("code"));
        it.setName(body.get("name"));
        it.setDescription(body.get("description"));
        it.setCategory(body.get("category"));
        it.setSeverityHint(body.get("severityHint"));
        return ResponseEntity.ok(issueTypeRepo.save(it));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return issueTypeRepo.findById(id).map(it -> {
            if (body.containsKey("code")) it.setCode(body.get("code"));
            if (body.containsKey("name")) it.setName(body.get("name"));
            if (body.containsKey("description")) it.setDescription(body.get("description"));
            if (body.containsKey("category")) it.setCategory(body.get("category"));
            if (body.containsKey("severityHint")) it.setSeverityHint(body.get("severityHint"));
            return ResponseEntity.ok(issueTypeRepo.save(it));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        return issueTypeRepo.findById(id).map(it -> {
            it.setActive(false);
            issueTypeRepo.save(it);
            return ResponseEntity.ok(Map.of("deactivated", true));
        }).orElse(ResponseEntity.notFound().build());
    }
}
