package com.sbom.publicationrecord.pipeline;

import com.sbom.publicationrecord.pipeline.util.Cmd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class GitWorkspace {
    private static final Logger logger = LoggerFactory.getLogger(GitWorkspace.class);
    private static final Map<String, String> NON_INTERACTIVE_GIT_ENV = Map.of(
            "GIT_TERMINAL_PROMPT", "0",
            "GCM_INTERACTIVE", "never"
    );
    private final Path workDir;
    private final int depth;

    public GitWorkspace(@Value("${sbom.workDir}") String workDir,
                        @Value("${git.depth:1}") int depth) {
        this.workDir = Path.of(workDir);
        this.depth = depth;
    }

    public Path prepareRepo(String gitUrl) {
        try {
            Files.createDirectories(workDir);
            String name = sanitizeRepoName(gitUrl);
            Path repoDir = workDir.resolve(name);
            logger.info("Preparing to clone or pull the repo: {}", gitUrl);
            prepareCloneDestination(repoDir);

            // Pull existing repo
            if(Files.exists(repoDir.resolve(".git"))) {
                Cmd.Result r = Cmd.run(
                        List.of("git", "-C", repoDir.toString(), "pull", "--ff-only"),
                        Duration.ofMinutes(3),
                        NON_INTERACTIVE_GIT_ENV
                );
                logger.info("Git pull stdout: {}", r.stdout());
                if (!r.stderr().isEmpty()) {
                    logger.error("Git pull stderr: {}", r.stderr());
                } else {
                    logger.info("No changes to pull. Repo is already up-to-date.");
                }

                if(r.exitCode() != 0){
                    throw new IllegalStateException(("Failed to git pull " + r.stderr()));
                }
                // Clone new repo
            } else {
                Cmd.Result r = Cmd.run(
                        List.of("git", "clone", "--depth", String.valueOf(depth), gitUrl, repoDir.toString()),
                        Duration.ofMinutes(5),
                        NON_INTERACTIVE_GIT_ENV
                );
                logger.info("Git clone stdout: {}", r.stdout());

                if(r.exitCode() != 0){
                    logger.error("Git clone stderr: {}", r.stderr());
                    throw new IllegalStateException(("Failed to git clone " + r.stderr()));
                }
                else {
                    // If there are no errors, log that the repo was cloned successfully
                    logger.info("Successfully cloned the repository: {}", gitUrl);
                }
            }
            return repoDir;

        } catch (Exception e) {

            throw new IllegalStateException("Failed to prepare repo: " + gitUrl, e);
        }
    }

    void prepareCloneDestination(Path repoDir) {
        try {
            if (Files.exists(repoDir) && !Files.exists(repoDir.resolve(".git"))) {
                logger.warn("Removing stale non-git workspace before cloning: {}", repoDir);
                deleteRepo(repoDir);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare clone destination: " + repoDir, e);
        }
    }

    // Runs git-rev-parse Head in repo dir and returns the non empty full commit SHA
    public String getHeadCommitSha(Path repoDir) {
        Cmd.Result result = Cmd.run(
                List.of("git", "-C", repoDir.toString(), "rev-parse", "HEAD"),
                Duration.ofSeconds(30)
        );
        if (result.exitCode() != 0) {
            throw new IllegalStateException("Failed to resolve HEAD commit for " + repoDir + ": " + result.stderr());
        }
        String sha = result.stdout().trim();
        if (sha.isBlank()) {
            throw new IllegalStateException("HEAD commit is empty for " + repoDir);
        }
        return sha;
    }
    // returns the version using git describe --tags --always --abbrev=7
    public String describeVersion(Path repoDir) {
        Cmd.Result describe = Cmd.run(
                List.of("git", "-C", repoDir.toString(), "describe", "--tags", "--always", "--abbrev=7"),
                Duration.ofSeconds(30)
        );
        if (describe.exitCode() == 0 && !describe.stdout().trim().isBlank()) {
            return describe.stdout().trim();
        }
        String commitSha = getHeadCommitSha(repoDir);
        return commitSha.length() > 7 ? commitSha.substring(0, 7) : commitSha;
    }

    private String sanitizeRepoName(String gitUrl) {
        String s = gitUrl.replace("https://", "").replace("git@", "");
        s = s.replaceAll("[^a-zA-Z0-9._-]", "_");
        return s.length() > 120 ? s.substring(0,120) : s;
    }
    public void deleteRepo(Path repoDir) {
        try {
            if (Files.exists(repoDir)) {
                // Log the beginning of the deletion process
                logger.info("Deleting repo directory: {}", repoDir);

                // Walk through the directory tree and delete files first, then the folder
                try (Stream<Path> paths = Files.walk(repoDir)) {
                    paths.sorted((path1, path2) -> path2.compareTo(path1))  // Delete files before the folder itself
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                    // Log each file deletion
                                    logger.info("Deleted: {}", path);
                                } catch (IOException e) {
                                    logger.error("Failed to delete file: {}", path, e);
                                }
                            });
                }
                // Log after the entire repo is deleted
                logger.info("Successfully deleted repo directory: {}", repoDir);
            } else {
                logger.warn("Repo directory does not exist: {}", repoDir);
            }
        } catch (IOException e) {
            // Log any error encountered during the deletion process
            logger.error("Failed to delete repo directory: {}", repoDir, e);
        }
    }
}
