package com.sbom.publicationrecord.web;

import com.sbom.publicationrecord.pipeline.GitWorkspace;
import com.sbom.publicationrecord.pipeline.SbomGenerator;
import com.sbom.publicationrecord.pipeline.SbomValidator;
import com.sbom.publicationrecord.publication.*;
import com.sbom.publicationrecord.report.RunReportWriter;
import com.sbom.publicationrecord.sbom.SpdxSwidConcatenator;
import com.sbom.publicationrecord.service.OneTimeSbomDownloadService;
import com.sbom.publicationrecord.service.SbomPublicationException;
import com.sbom.publicationrecord.service.SbomResult;
import com.sbom.publicationrecord.service.SbomService;
import com.sbom.publicationrecord.swid.SwidService;
import com.sbom.publicationrecord.swid.SwidXmlGenerator;
import com.sbom.publicationrecord.zenodo.DoiPublisher;
import com.sbom.publicationrecord.zenodo.ZenodoProperties;
import com.sbom.publicationrecord.zenodo.ZenodoPublicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SbomControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultsPublicationModeToFullSbomPublic() throws Exception {
        StubSbomService service = new StubSbomService();
        service.nextResult = sampleResult(PublicationMode.FULL_SBOM_PUBLIC);
        MockMvc mockMvc = mvc(service);

        mockMvc.perform(post("/api/sbom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gitUrl":"https://github.com/example/repo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationMode").value("FULL_SBOM_PUBLIC"));

        assertEquals(PublicationMode.FULL_SBOM_PUBLIC, service.capturedPublicationMode);
        assertEquals("https://github.com/example/repo", service.capturedGitUrl);
    }

    @Test
    void rejectsInvalidPublicationModeWithBadRequest() throws Exception {
        MockMvc mockMvc = mvc(new StubSbomService());

        mockMvc.perform(post("/api/sbom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gitUrl":"https://github.com/example/repo","publicationMode":"NOT_A_MODE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "publicationMode must be one of: FULL_SBOM_PUBLIC, HASH_ONLY_PUBLIC."
                ));
    }

    @Test
    void returnsPartialModeAwareResponseWhenPublicationFails() throws Exception {
        StubSbomService service = new StubSbomService();
        service.nextFailure = new SbomPublicationException(
                "Zenodo publication failed at stage ARTIFACTS_UPLOADING.",
                sampleResult(PublicationMode.HASH_ONLY_PUBLIC),
                null
        );
        MockMvc mockMvc = mvc(service);

        mockMvc.perform(post("/api/sbom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gitUrl":"https://github.com/example/repo","publicationMode":"HASH_ONLY_PUBLIC"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.publicationMode").value("HASH_ONLY_PUBLIC"))
                .andExpect(jsonPath("$.proofRecordUrl").value("https://zenodo.example/proof.json"))
                .andExpect(jsonPath("$.message").value(
                        "SBOM generation finished, but publication failed: Zenodo publication failed at stage ARTIFACTS_UPLOADING."
                ));
    }

    @Test
    void hashOnlyResponseIncludesPrivateDownloadFieldsWhenAvailable() throws Exception {
        StubSbomService service = new StubSbomService();
        service.nextResult = sampleResult(PublicationMode.HASH_ONLY_PUBLIC, true);
        MockMvc mockMvc = mvc(service);

        mockMvc.perform(post("/api/sbom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gitUrl":"https://github.com/example/repo","publicationMode":"HASH_ONLY_PUBLIC"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationMode").value("HASH_ONLY_PUBLIC"))
                .andExpect(jsonPath("$.privateSbomDownloadAvailable").value(true))
                .andExpect(jsonPath("$.privateSbomDownloadUrl").value("/api/sbom/private-download/test-token"))
                .andExpect(jsonPath("$.privateSbomDownloadExpiresAt").value("2026-04-17T12:15:00Z"))
                .andExpect(jsonPath("$.privateSbomDownloadMessage").value(
                        "The full SBOM was generated and hashed, but it was not published."
                ));
    }

    @Test
    void fullResponseDoesNotIncludePrivateDownloadFields() throws Exception {
        StubSbomService service = new StubSbomService();
        service.nextResult = sampleResult(PublicationMode.FULL_SBOM_PUBLIC);
        MockMvc mockMvc = mvc(service);

        mockMvc.perform(post("/api/sbom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gitUrl":"https://github.com/example/repo","publicationMode":"FULL_SBOM_PUBLIC"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationMode").value("FULL_SBOM_PUBLIC"))
                .andExpect(jsonPath("$.privateSbomDownloadAvailable").doesNotExist())
                .andExpect(jsonPath("$.privateSbomDownloadUrl").doesNotExist())
                .andExpect(jsonPath("$.privateSbomDownloadExpiresAt").doesNotExist())
                .andExpect(jsonPath("$.privateSbomDownloadMessage").doesNotExist());
    }

    @Test
    void privateDownloadReturnsSbomOnceAndDeletesFile() throws Exception {
        Path sbomDir = tempDir.resolve("sboms");
        Files.createDirectories(sbomDir);
        Path sbomFile = sbomDir.resolve("repo.spdx-json.json");
        String sbomJson = """
                {"spdxVersion":"SPDX-2.3","SPDXID":"SPDXRef-DOCUMENT","documentNamespace":"https://example.org/test"}
                """;
        Files.writeString(sbomFile, sbomJson);
        OneTimeSbomDownloadService downloadService = new OneTimeSbomDownloadService(sbomDir);
        OneTimeSbomDownloadService.DownloadRegistration registration = downloadService.createDownload(
                sbomFile,
                "repo.spdx-json.json",
                "abc123"
        );
        MockMvc mockMvc = mvc(new StubSbomService(), downloadService);

        mockMvc.perform(get(registration.downloadUrl()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"repo.spdx-json.json\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(content().json(sbomJson));

        assertFalse(Files.exists(sbomFile));

        mockMvc.perform(get(registration.downloadUrl()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("Private SBOM download link has already been used."));
    }

    @Test
    void expiredPrivateDownloadReturnsGoneAndDeletesFile() throws Exception {
        Path sbomDir = tempDir.resolve("expired-sboms");
        Files.createDirectories(sbomDir);
        Path sbomFile = sbomDir.resolve("repo.spdx-json.json");
        Files.writeString(sbomFile, "{\"spdxVersion\":\"SPDX-2.3\"}");
        OneTimeSbomDownloadService downloadService = new OneTimeSbomDownloadService(sbomDir);
        OneTimeSbomDownloadService.DownloadRegistration registration = downloadService.createDownload(
                sbomFile,
                "repo.spdx-json.json",
                "abc123",
                Duration.ofMillis(-1)
        );
        MockMvc mockMvc = mvc(new StubSbomService(), downloadService);

        mockMvc.perform(get(registration.downloadUrl()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("Private SBOM download link has expired."));

        assertFalse(Files.exists(sbomFile));
    }

    @Test
    void invalidPrivateDownloadReturnsNotFound() throws Exception {
        MockMvc mockMvc = mvc(new StubSbomService());

        mockMvc.perform(get("/api/sbom/private-download/not-a-real-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Private SBOM download link was not found."));
    }

    private MockMvc mvc(StubSbomService service) {
        return mvc(service, new OneTimeSbomDownloadService(tempDir.resolve("sboms")));
    }

    private MockMvc mvc(StubSbomService service, OneTimeSbomDownloadService downloadService) {
        return MockMvcBuilders
                .standaloneSetup(new SbomController(service, downloadService, "spdx-json"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static SbomResult sampleResult(PublicationMode publicationMode) {
        return sampleResult(publicationMode, false);
    }

    private static SbomResult sampleResult(PublicationMode publicationMode, boolean includePrivateDownload) {
        return new SbomResult(
                "repo.spdx-json.json",
                Path.of("/tmp/repo.spdx-json.json"),
                "abc123",
                "0123456789abcdef0123456789abcdef01234567",
                "v1.2.3",
                Instant.parse("2026-04-17T12:00:00Z"),
                publicationMode,
                "tag:example",
                "repo.swid.xml",
                publicationMode == PublicationMode.FULL_SBOM_PUBLIC ? "repo.spdx-json+swid.json" : null,
                publicationMode == PublicationMode.HASH_ONLY_PUBLIC ? "repo.proof.json" : null,
                publicationMode == PublicationMode.FULL_SBOM_PUBLIC ? "https://zenodo.example/sbom.json" : null,
                publicationMode == PublicationMode.FULL_SBOM_PUBLIC ? "https://zenodo.example/swid.xml" : null,
                publicationMode == PublicationMode.FULL_SBOM_PUBLIC ? "https://zenodo.example/combined.json" : null,
                publicationMode == PublicationMode.HASH_ONLY_PUBLIC ? "https://zenodo.example/proof.json" : null,
                "10.5072/zenodo.12345",
                "https://doi.org/10.5072/zenodo.12345",
                "12345",
                "12300",
                "https://sandbox.zenodo.org/records/12345",
                Instant.parse("2026-04-17T12:01:00Z"),
                Instant.parse("2026-04-17T12:02:00Z"),
                "repo.publication-record.json",
                Path.of("/tmp/repo.publication-record.json"),
                "https://zenodo.example/publication-record.json",
                publicationMode == PublicationMode.FULL_SBOM_PUBLIC,
                "https://sandbox.zenodo.org/deposit/12345",
                PublicationStage.DOI_VERIFIED,
                PublicationStatusView.Published,
                "DOI verified.",
                publicationMode == PublicationMode.FULL_SBOM_PUBLIC,
                publicationMode == PublicationMode.FULL_SBOM_PUBLIC,
                null,
                includePrivateDownload ? "/api/sbom/private-download/test-token" : null,
                includePrivateDownload ? Instant.parse("2026-04-17T12:15:00Z") : null,
                includePrivateDownload,
                includePrivateDownload ? "The full SBOM was generated and hashed, but it was not published." : null
        );
    }

    private static final class StubSbomService extends SbomService {
        private String capturedGitUrl;
        private PublicationMode capturedPublicationMode;
        private SbomResult nextResult = sampleResult(PublicationMode.FULL_SBOM_PUBLIC);
        private RuntimeException nextFailure;

        private StubSbomService() {
            super(
                    new GitWorkspace(System.getProperty("java.io.tmpdir"), 1),
                    new NoOpSbomGenerator(),
                    new SbomValidator(),
                    new RunReportWriter(Path.of(System.getProperty("java.io.tmpdir"))),
                    new SwidService(false, "test", "example.org", "en", "spdx-json",
                            Path.of(System.getProperty("java.io.tmpdir")), new SwidXmlGenerator()),
                    new SpdxSwidConcatenator(Path.of(System.getProperty("java.io.tmpdir"))),
                    new ZenodoPublicationService(new ZenodoProperties(), new NoOpDoiPublisher()),
                    new ArtifactPublicationPlanner(new ZenodoProperties()),
                    new ProofRecordBuilder(new ZenodoProperties()),
                    new OneTimeSbomDownloadService(Path.of(System.getProperty("java.io.tmpdir"))),
                    Path.of(System.getProperty("java.io.tmpdir")),
                    Path.of(System.getProperty("java.io.tmpdir")),
                    "spdx-json",
                    "syft"
            );
        }

        @Override
        public SbomResult generateForRepo(String gitUrl, PublicationMode publicationMode) {
            this.capturedGitUrl = gitUrl;
            this.capturedPublicationMode = publicationMode;
            if (nextFailure != null) {
                throw nextFailure;
            }
            return nextResult;
        }
    }

    private static final class NoOpSbomGenerator implements SbomGenerator {
        @Override
        public Path generate(Path repoDir, Path outputFile) {
            return outputFile;
        }
    }

    private static final class NoOpDoiPublisher implements DoiPublisher {
        @Override
        public com.sbom.publicationrecord.publication.ZenodoPublicationResult reserve(
                com.sbom.publicationrecord.publication.PublicationRecord publicationRecord) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.sbom.publicationrecord.publication.ZenodoPublicationResult publish(
                com.sbom.publicationrecord.publication.PublicationRecord publicationRecord,
                com.sbom.publicationrecord.publication.ZenodoPublicationResult reservationResult) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String publicArtifactUrl(String zenodoRecordId, String fileName) {
            return null;
        }
    }
}
