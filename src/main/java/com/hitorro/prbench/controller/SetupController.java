package com.hitorro.prbench.controller;

import com.hitorro.prbench.service.GitHubApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/setup")
@CrossOrigin(origins = "*")
public class SetupController {

    private final GitHubApiService github;

    public SetupController(GitHubApiService github) { this.github = github; }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("githubTokenSet", github.hasToken());
        s.put("ready", github.hasToken());
        return ResponseEntity.ok(s);
    }

    @PostMapping("/token")
    public ResponseEntity<?> setToken(@RequestBody Map<String, String> body) {
        github.setGithubToken(body.get("token"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/rate-limit")
    public ResponseEntity<?> rateLimit() {
        try {
            return ResponseEntity.ok(github.getRateLimit());
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }
}
