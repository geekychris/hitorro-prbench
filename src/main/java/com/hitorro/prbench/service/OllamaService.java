package com.hitorro.prbench.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Ollama LLM integration for generating repo descriptions by scanning
 * README, file tree, recent commits, topics, and package manifests.
 */
@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${app.ollama.model:llama3.2}")
    private String model;

    public OllamaService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isAvailable() {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generate a description from all available repo context:
     * README, file tree, recent commit messages, topics, and package manifest.
     */
    public String generateDescription(String repoName, String owner, String language,
                                       String readmeContent, List<String> fileTree,
                                       List<String> recentCommits, List<String> topics,
                                       String packageManifest, String githubDescription) {
        try {
            StringBuilder context = new StringBuilder();
            context.append("Repository: ").append(owner).append("/").append(repoName).append("\n");
            if (language != null) context.append("Primary language: ").append(language).append("\n");
            if (githubDescription != null && !githubDescription.isBlank()) {
                context.append("GitHub description: ").append(githubDescription).append("\n");
            }
            if (topics != null && !topics.isEmpty()) {
                context.append("Topics: ").append(String.join(", ", topics)).append("\n");
            }

            if (readmeContent != null && !readmeContent.isBlank()) {
                String truncated = readmeContent.length() > 2000
                        ? readmeContent.substring(0, 2000) + "..." : readmeContent;
                context.append("\nREADME:\n").append(truncated).append("\n");
            }

            if (fileTree != null && !fileTree.isEmpty()) {
                context.append("\nFile structure (top-level):\n");
                for (String f : fileTree.subList(0, Math.min(50, fileTree.size()))) {
                    context.append("  ").append(f).append("\n");
                }
            }

            if (recentCommits != null && !recentCommits.isEmpty()) {
                context.append("\nRecent commits:\n");
                for (String c : recentCommits.subList(0, Math.min(10, recentCommits.size()))) {
                    context.append("  - ").append(c).append("\n");
                }
            }

            if (packageManifest != null && !packageManifest.isBlank()) {
                String truncated = packageManifest.length() > 1000
                        ? packageManifest.substring(0, 1000) + "..." : packageManifest;
                context.append("\nBuild/package manifest:\n").append(truncated).append("\n");
            }

            String prompt = "Based on the following information about a GitHub repository, " +
                    "write a clear, informative description of 2-3 sentences. " +
                    "Explain what the project does, its purpose, and key technologies. " +
                    "Output ONLY the description, no preamble or labels.\n\n" +
                    context;

            var payload = mapper.createObjectNode();
            payload.put("model", model);
            payload.put("prompt", prompt.toString());
            payload.put("stream", false);

            var req = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/generate"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode result = mapper.readTree(resp.body());
                return result.get("response").asText().trim();
            }
            log.warn("Ollama returned {}: {}", resp.statusCode(), resp.body());
            return null;
        } catch (Exception e) {
            log.error("Ollama generation failed: {}", e.getMessage());
            return null;
        }
    }
}
