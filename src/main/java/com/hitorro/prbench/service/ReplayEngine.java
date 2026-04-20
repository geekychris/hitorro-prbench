package com.hitorro.prbench.service;

import com.hitorro.gittools.git.GitCredentials;
import com.hitorro.gittools.git.GitService;
import com.hitorro.prbench.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Replays a suite PR as a synthetic PR in a mirror repository.
 * Uses hitorro-gittools for git operations and the GitHub API for PR creation.
 */
@Service
public class ReplayEngine {

    private static final Logger log = LoggerFactory.getLogger(ReplayEngine.class);

    private final GitService gitService;
    private final GitHubApiService github;

    @Value("${app.workspace.base-path:${user.home}/.pr-bench/workspaces}")
    private String workspaceBasePath;

    public ReplayEngine(GitHubApiService github) {
        this.gitService = new GitService();
        this.github = github;
    }

    public void configureCredentials(String token, String username) {
        var creds = new GitCredentials();
        creds.setGithubToken(token);
        creds.setGithubUsername(username);
        gitService.setCredentials(creds);
    }

    /**
     * Create a synthetic PR in the mirror repository that replays the changes
     * from the original PR.
     *
     * Steps:
     * 1. Ensure local clone of mirror repo exists
     * 2. Create base branch at the base commit SHA
     * 3. Create head branch with the head commit changes
     * 4. Push both branches to mirror remote
     * 5. Open a PR via GitHub API
     */
    public ReplayResult replay(SuitePr suitePr, Bot bot, ExemplarRepo repo) {
        String mirrorOrg = repo.getMirrorOrg() != null ? repo.getMirrorOrg() : repo.getOwner();
        String mirrorRepo = repo.getMirrorRepoName() != null ? repo.getMirrorRepoName() : repo.getRepoName() + "-mirror";
        String runId = String.valueOf(System.currentTimeMillis());

        String baseBranch = "bench-base-" + runId + "-pr" + suitePr.getOriginalPrNumber();
        String headBranch = "bench-head-" + runId + "-pr" + suitePr.getOriginalPrNumber() + "-" + bot.getName();

        try {
            Path workspace = Path.of(workspaceBasePath, mirrorOrg, mirrorRepo);

            // Clone if not present
            if (!Files.isDirectory(workspace.resolve(".git"))) {
                Files.createDirectories(workspace.getParent());
                String cloneUrl = "https://github.com/" + mirrorOrg + "/" + mirrorRepo + ".git";
                gitService.clone(cloneUrl, workspace);
                log.info("Cloned mirror repo to {}", workspace);
            }

            // Fetch latest
            gitService.fetch(workspace);

            // Create base branch at the base commit
            try {
                gitService.createBranch(workspace, baseBranch, suitePr.getBaseCommitSha());
            } catch (Exception e) {
                // Branch may already exist from another bot in same run
                gitService.checkout(workspace, baseBranch);
            }

            // Push base branch
            gitService.push(workspace, "origin", baseBranch);

            // Create head branch from head commit
            gitService.checkout(workspace, suitePr.getBaseCommitSha());
            gitService.createBranch(workspace, headBranch, suitePr.getHeadCommitSha());

            // If bot has a workflow file, inject it
            if (bot.getWorkflowContent() != null && !bot.getWorkflowContent().isBlank()) {
                Path workflowDir = workspace.resolve(".github/workflows");
                Files.createDirectories(workflowDir);
                String fileName = bot.getWorkflowFileName() != null ? bot.getWorkflowFileName() : bot.getName() + ".yml";
                Files.writeString(workflowDir.resolve(fileName), bot.getWorkflowContent());
                // Commit the workflow file
                gitService.getRunner().runOrThrow(workspace, "add", ".github/workflows/" + fileName);
                gitService.getRunner().runOrThrow(workspace, "commit", "-m",
                        "Add " + bot.getName() + " workflow for benchmarking");
            }

            // Push head branch
            gitService.push(workspace, "origin", headBranch);

            // Create PR via GitHub API
            String title = "[Bench] " + suitePr.getTitle();
            String body = "Replay of PR #" + suitePr.getOriginalPrNumber() +
                    " from " + repo.getOwner() + "/" + repo.getRepoName() +
                    "\nBot: " + bot.getName();

            var prNode = github.createPullRequest(mirrorOrg, mirrorRepo, title, body, headBranch, baseBranch);

            int prNumber = prNode.get("number").asInt();
            String prUrl = prNode.get("html_url").asText();

            // Switch back to default branch
            gitService.checkout(workspace, repo.getDefaultBranch());

            return new ReplayResult(true, prNumber, prUrl, baseBranch, headBranch, null);

        } catch (Exception e) {
            log.error("Replay failed for PR #{} with bot {}: {}", suitePr.getOriginalPrNumber(), bot.getName(), e.getMessage());
            return new ReplayResult(false, 0, null, baseBranch, headBranch, e.getMessage());
        }
    }

    /** Clean up mirror branches and close PR. */
    public void cleanup(ExemplarRepo repo, ReplayPr replayPr) {
        String mirrorOrg = repo.getMirrorOrg() != null ? repo.getMirrorOrg() : repo.getOwner();
        String mirrorRepo = repo.getMirrorRepoName() != null ? repo.getMirrorRepoName() : repo.getRepoName() + "-mirror";

        try {
            if (replayPr.getMirrorPrNumber() != null && replayPr.getMirrorPrNumber() > 0) {
                github.closePullRequest(mirrorOrg, mirrorRepo, replayPr.getMirrorPrNumber());
            }
            if (replayPr.getHeadBranch() != null) {
                github.deleteRef(mirrorOrg, mirrorRepo, "heads/" + replayPr.getHeadBranch());
            }
            if (replayPr.getBaseBranch() != null) {
                github.deleteRef(mirrorOrg, mirrorRepo, "heads/" + replayPr.getBaseBranch());
            }
        } catch (Exception e) {
            log.warn("Cleanup failed for replay PR {}: {}", replayPr.getId(), e.getMessage());
        }
    }

    public record ReplayResult(boolean success, int prNumber, String prUrl,
                                String baseBranch, String headBranch, String error) {}
}
