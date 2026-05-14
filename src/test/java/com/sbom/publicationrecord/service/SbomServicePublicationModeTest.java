package com.sbom.publicationrecord.service;

import com.sbom.publicationrecord.pipeline.GitWorkspace;
import com.sbom.publicationrecord.pipeline.SbomGenerator;
import com.sbom.publicationrecord.pipeline.SbomValidator;
import com.sbom.publicationrecord.publication.*;
import com.sbom.publicationrecord.report.RunReportWriter;
import com.sbom.publicationrecord.sbom.SpdxSwidConcatenator;
import com.sbom.publicationrecord.swid.SwidService;
import com.sbom.publicationrecord.swid.SwidXmlGenerator;
import com.sbom.publicationrecord.zenodo.DoiPublisher;
import com.sbom.publicationrecord.zenodo.ZenodoProperties;
import com.sbom.publicationrecord.zenodo.ZenodoPublicationException;
import com.sbom.publicationrecord.zenodo.ZenodoPublicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
public class SbomServicePublicationModeTest {
    @TempDir
    Path tempDir;

    @Test
    void fullPublicationModeKeepsFullArtifactPath() throws Exception {
        TestHarness harness = createHarness();

        SbomResult result = harness.service.generateForRepo(
                "https://github.com/example/repo",
                PublicationMode.FULL_SBOM_PUBLIC
        );

        PublicationRecord publishedRecord = harness.zenodoPublicationService.publishedRecord;
        assertNotNull(publishedRecord);
        assertNotNull(publishedRecord.sbom());
        assertNotNull(publishedRecord.swid());
        assertNotNull(publishedRecord.combinedSbom());
        assertNull(publishedRecord.proofRecord());

        assertTrue(result.rawSbomPublished());
        assertTrue(result.combinedArtifactPublished());
        assertNotNull(result.publicSbomUrl());
        assertNotNull(result.publicCombinedSbomUrl());
        assertNull(result.proofRecordFileName());
        assertNull(result.privateSbomDownloadUrl());
        assertNull(result.privateSbomDownloadExpiresAt());
        assertFalse(result.privateSbomDownloadAvailable());
        assertNull(result.privateSbomDownloadMessage());

        assertFalse(Files.exists(publishedRecord.sbom().filePath()));
        assertFalse(Files.exists(publishedRecord.swid().filePath()));
        assertFalse(Files.exists(publishedRecord.combinedSbom().filePath()));
        assertTrue(harness.gitWorkspace.repoDeleted);
    }

    @Test
    void hashOnlyPublicationModePublishesOnlyProofArtifact() throws Exception {
        TestHarness harness = createHarness();

        SbomResult result = harness.service.generateForRepo(
                "https://github.com/example/repo",
                PublicationMode.HASH_ONLY_PUBLIC
        );

        PublicationRecord publishedRecord = harness.zenodoPublicationService.publishedRecord;
        assertNotNull(publishedRecord);
        assertNotNull(publishedRecord.sbom());
        assertNotNull(publishedRecord.swid());
        assertNull(publishedRecord.combinedSbom());
        assertNotNull(publishedRecord.proofRecord());
        assertFalse(publishedRecord.publicationPlan().publishRawSbom());
        assertFalse(publishedRecord.publicationPlan().publishSwid());
        assertFalse(publishedRecord.publicationPlan().publishCombinedSbom());
        assertTrue(publishedRecord.publicationPlan().publishProofArtifact());
        assertTrue(publishedRecord.publicationPlan().publishPublicationManifest());

        assertFalse(result.rawSbomPublished());
        assertFalse(result.combinedArtifactPublished());
        assertNull(result.publicSbomUrl());
        assertNull(result.publicCombinedSbomUrl());
        assertNotNull(result.proofRecordUrl());
        assertNotNull(result.proofRecordFileName());
        assertNotNull(result.privateSbomDownloadUrl());
        assertNotNull(result.privateSbomDownloadExpiresAt());
        assertTrue(result.privateSbomDownloadAvailable());
        assertNotNull(result.privateSbomDownloadMessage());

        assertTrue(harness.zenodoPublicationService.capturedProofJson.contains("sbom-publication-proof"));
        assertTrue(harness.zenodoPublicationService.capturedProofJson.contains(result.sbomSha256()));
        assertFalse(harness.zenodoPublicationService.capturedProofJson.contains("\"packages\""));
        assertTrue(Files.exists(publishedRecord.sbom().filePath()));
        assertFalse(Files.exists(publishedRecord.swid().filePath()));
        assertFalse(Files.exists(publishedRecord.proofRecord().filePath()));
        assertTrue(harness.gitWorkspace.repoDeleted);
    }

    private TestHarness createHarness() {
        Path sbomOutputDir = tempDir.resolve("sboms");
        Path swidOutputDir = tempDir.resolve("swid");
        Path combinedOutputDir = tempDir.resolve("combined");
        Path manifestOutputDir = tempDir.resolve("publication");

        ZenodoProperties zenodoProperties = new ZenodoProperties();
        zenodoProperties.setManifestOutputDir(manifestOutputDir);
        zenodoProperties.setUploadRawSbom(true);
        zenodoProperties.setUploadSwid(true);
        zenodoProperties.setUploadCombinedSbom(true);
        zenodoProperties.setUploadPublicationManifest(true);

        TestGitWorkspace gitWorkspace = new TestGitWorkspace(tempDir.resolve("work"));
        RecordingZenodoPublicationService zenodoPublicationService = new RecordingZenodoPublicationService();

        SbomService service = new SbomService(
                gitWorkspace,
                new TestSbomGenerator(),
                new SbomValidator(),
                new RunReportWriter(sbomOutputDir),
                new SwidService(true, "Test Publisher", "example.org", "en", "spdx-json",
                        swidOutputDir, new SwidXmlGenerator()),
                new SpdxSwidConcatenator(combinedOutputDir),
                zenodoPublicationService,
                new ArtifactPublicationPlanner(zenodoProperties),
                new ProofRecordBuilder(zenodoProperties),
                new OneTimeSbomDownloadService(sbomOutputDir),
                sbomOutputDir,
                swidOutputDir,
                "spdx-json",
                "syft"
        );

        return new TestHarness(service, gitWorkspace, zenodoPublicationService);
    }

    private record TestHarness(SbomService service,
                               TestGitWorkspace gitWorkspace,
                               RecordingZenodoPublicationService zenodoPublicationService) {
    }

    private static final class TestGitWorkspace extends GitWorkspace {
        private final Path repoDir;
        private boolean repoDeleted;

        private TestGitWorkspace(Path workDir) {
            super(workDir.toString(), 1);
            this.repoDir = workDir.resolve("repo-under-test");
        }

        @Override
        public Path prepareRepo(String gitUrl) {
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
                          "name": "example/repo",
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

    private static final class RecordingZenodoPublicationService extends ZenodoPublicationService {
        private PublicationRecord reservedRecord;
        private PublicationRecord publishedRecord;
        private String capturedProofJson;

        private RecordingZenodoPublicationService() {
            super(new ZenodoProperties(), new NoOpDoiPublisher());
        }

        @Override
        public ZenodoPublicationResult reserveIfEnabled(PublicationRecord publicationRecord) {
            this.reservedRecord = publicationRecord;
            return new ZenodoPublicationResult(
                    "10.5072/zenodo.55555",
                    "https://doi.org/10.5072/zenodo.55555",
                    "55555",
                    "55500",
                    "https://sandbox.zenodo.org/records/55555",
                    "https://sandbox.zenodo.org/deposit/55555",
                    null,
                    null,
                    null,
                    null,
                    null,
                    Instant.parse("2026-04-17T12:01:00Z"),
                    null,
                    false,
                    PublicationStage.DOI_RESERVED,
                    PublicationStatusView.Draft,
                    PublicationStage.DOI_RESERVED.progressMessage(),
                    null,
                    null,
                    null
            );
        }

        @Override
        public ZenodoPublicationResult publishReservedIfEnabled(PublicationRecord publicationRecord,
                                                                ZenodoPublicationResult reservationResult) {
            this.publishedRecord = publicationRecord;
            try {
                if (publicationRecord.proofRecord() != null) {
                    this.capturedProofJson = Files.readString(publicationRecord.proofRecord().filePath());
                }
            } catch (Exception e) {
                throw new ZenodoPublicationException("Failed to inspect proof artifact.", PublicationStage.FAILED, reservationResult, e);
            }
            return new ZenodoPublicationResult(
                    reservationResult.doi(),
                    reservationResult.doiUrl(),
                    reservationResult.zenodoRecordId(),
                    reservationResult.zenodoConceptRecid(),
                    reservationResult.zenodoRecordUrl(),
                    reservationResult.zenodoDraftUrl(),
                    reservationResult.publicSbomUrl(),
                    reservationResult.publicSwidUrl(),
                    reservationResult.publicCombinedSbomUrl(),
                    reservationResult.publicProofRecordUrl(),
                    reservationResult.publicPublicationManifestUrl(),
                    reservationResult.doiReservedAt(),
                    Instant.parse("2026-04-17T12:03:00Z"),
                    true,
                    PublicationStage.DOI_VERIFIED,
                    PublicationStatusView.Published,
                    PublicationStage.DOI_VERIFIED.progressMessage(),
                    null,
                    null,
                    null
            );
        }

        @Override
        public String buildPublicArtifactUrl(String zenodoRecordId, String fileName) {
            if (zenodoRecordId == null || fileName == null) {
                return null;
            }
            return "https://sandbox.zenodo.org/records/" + zenodoRecordId + "/files/" + fileName;
        }
    }

    private static final class NoOpDoiPublisher implements DoiPublisher {
        @Override
        public ZenodoPublicationResult reserve(PublicationRecord publicationRecord) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ZenodoPublicationResult publish(PublicationRecord publicationRecord,
                                               ZenodoPublicationResult reservationResult) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String publicArtifactUrl(String zenodoRecordId, String fileName) {
            return null;
        }
    }
}
