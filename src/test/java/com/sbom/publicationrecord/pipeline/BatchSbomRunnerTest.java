package com.sbom.publicationrecord.pipeline;

import com.sbom.publicationrecord.report.RunReportWriter;
import com.sbom.publicationrecord.sbom.SpdxSwidConcatenator;
import com.sbom.publicationrecord.swid.SwidService;
import com.sbom.publicationrecord.swid.SwidXmlGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchSbomRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void batchModeRemainsLocalOnly() throws Exception {
        Path sbomOutputDir = tempDir.resolve("sboms");
        Path swidOutputDir = tempDir.resolve("swid");
        Path combinedOutputDir = tempDir.resolve("combined");

        TestGitWorkspace gitWorkspace = new TestGitWorkspace(tempDir.resolve("work"));
        BatchSbomRunner runner = new BatchSbomRunner(
                gitWorkspace,
                new TestSbomGenerator(),
                new SbomValidator(),
                new RunReportWriter(sbomOutputDir),
                new SwidService(true, "Test Publisher", "example.org", "en", "spdx-json",
                        swidOutputDir, new SwidXmlGenerator()),
                new SpdxSwidConcatenator(combinedOutputDir),
                sbomOutputDir,
                "spdx-json"
        );

        runner.run();

        assertEquals(List.of("https://github.com/socketio/socket.io"), gitWorkspace.preparedUrls);
        assertTrue(Files.exists(sbomOutputDir.resolve("repo-batch.spdx-json.json")));
        assertTrue(Files.exists(swidOutputDir.resolve("repo-batch.swid.xml")));
        assertTrue(Files.exists(combinedOutputDir.resolve("socketio_socket.io.0123456.spdx-json+swid.json")));
        assertTrue(Files.exists(sbomOutputDir.resolve("run-report.csv")));
        assertTrue(gitWorkspace.repoDeleted);
        assertFalse(Files.exists(tempDir.resolve("publication").resolve("repo-batch.proof.json")));
    }

    private static final class TestGitWorkspace extends GitWorkspace {
        private final Path repoDir;
        private final List<String> preparedUrls = new ArrayList<>();
        private boolean repoDeleted;

        private TestGitWorkspace(Path workDir) {
            super(workDir.toString(), 1);
            this.repoDir = workDir.resolve("repo-batch");
        }

        @Override
        public Path prepareRepo(String gitUrl) {
            preparedUrls.add(gitUrl);
            try {
                Files.createDirectories(repoDir);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return repoDir;
        }

        @Override
        public String getHeadCommitSha(Path repoDir) {
            return "0123456789abcdef0123456789abcdef01234567";
        }

        @Override
        public String describeVersion(Path repoDir) {
            return "v1.2.3";
        }

        @Override
        public void deleteRepo(Path repoDir) {
            repoDeleted = true;
        }
    }

    private static final class TestSbomGenerator implements SbomGenerator {
        @Override
        public Path generate(Path repoDir, Path outputFile) {
            try {
                Files.createDirectories(outputFile.getParent());
                Files.writeString(outputFile, """
                        {
                          "spdxVersion": "SPDX-2.3",
                          "SPDXID": "SPDXRef-DOCUMENT",
                          "documentNamespace": "https://example.org/spdx/document",
                          "name": "socketio/socket.io",
                          "creationInfo": {
                            "created": "2026-04-17T00:00:00Z",
                            "creators": ["Tool: test"]
                          },
                          "documentDescribes": ["SPDXRef-Package-root"],
                          "packages": [
                            {
                              "name": "root",
                              "SPDXID": "SPDXRef-Package-root",
                              "downloadLocation": "NOASSERTION"
                            }
                          ],
                          "relationships": [
                            {
                              "spdxElementId": "SPDXRef-DOCUMENT",
                              "relationshipType": "DESCRIBES",
                              "relatedSpdxElement": "SPDXRef-Package-root"
                            }
                          ]
                        }
                        """);
                return outputFile;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
