package com.sbom.publicationrecord.pipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
public class GitWorkspaceTest {
    @TempDir
    Path tempDir;

    @Test
    void removesStaleNonGitCloneDestination() throws Exception {
        GitWorkspace workspace = new GitWorkspace(tempDir.toString(), 1);
        Path repoDir = tempDir.resolve("github.com_example_repo");
        Files.createDirectories(repoDir);
        Files.writeString(repoDir.resolve("leftover.txt"), "stale");

        workspace.prepareCloneDestination(repoDir);

        assertFalse(Files.exists(repoDir));
    }

    @Test
    void keepsExistingGitWorkspaceForPullPath() throws Exception {
        GitWorkspace workspace = new GitWorkspace(tempDir.toString(), 1);
        Path repoDir = tempDir.resolve("github.com_example_repo");
        Files.createDirectories(repoDir.resolve(".git"));
        Files.writeString(repoDir.resolve("tracked.txt"), "keep");

        workspace.prepareCloneDestination(repoDir);

        assertTrue(Files.exists(repoDir.resolve(".git")));
        assertTrue(Files.exists(repoDir.resolve("tracked.txt")));
    }
}
