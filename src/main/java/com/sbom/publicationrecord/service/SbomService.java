package com.sbom.publicationrecord.service;

import com.sbom.publicationrecord.pipeline.GitWorkspace;
import com.sbom.publicationrecord.pipeline.SbomGenerator;
import com.sbom.publicationrecord.pipeline.SbomValidator;
import com.sbom.publicationrecord.publication.*;
import com.sbom.publicationrecord.report.RunReportWriter;
import com.sbom.publicationrecord.sbom.CombinedSbomResult;
import com.sbom.publicationrecord.sbom.SpdxSwidConcatenator;
import com.sbom.publicationrecord.swid.HashUtil;
import com.sbom.publicationrecord.swid.SwidGenerationResult;
import com.sbom.publicationrecord.swid.SwidService;
import com.sbom.publicationrecord.zenodo.ZenodoPublicationException;
import com.sbom.publicationrecord.zenodo.ZenodoPublicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class SbomService {
    private static final Logger logger = LoggerFactory.getLogger(SbomService.class);

    private final GitWorkspace git;
    private final SbomGenerator sbomGenerator;
    private final SbomValidator sbomValidator;
    private final RunReportWriter reportWriter;
    private final Path outputDir;
    private final Path swidOutputDir;
    private final String format;
    private final String sbomGeneratorName;
    private final SwidService swidService;
    private final SpdxSwidConcatenator spdxSwidConcatenator;
    private final ZenodoPublicationService zenodoPublicationService;
    private final ArtifactPublicationPlanner artifactPublicationPlanner;
    private final ProofRecordBuilder proofRecordBuilder;
    private final OneTimeSbomDownloadService oneTimeSbomDownloadService;
    private final ReentrantLock lock = new ReentrantLock();

    public SbomService(GitWorkspace git,
                       SbomGenerator sbomGenerator,
                       SbomValidator sbomValidator,
                       RunReportWriter reportWriter,
                       SwidService swidService,
                       SpdxSwidConcatenator spdxSwidConcatenator,
                       ZenodoPublicationService zenodoPublicationService,
                       ArtifactPublicationPlanner artifactPublicationPlanner,
                       ProofRecordBuilder proofRecordBuilder,
                       OneTimeSbomDownloadService oneTimeSbomDownloadService,
                       @Value("${sbom.outputDir}") Path outputDir,
                       @Value("${swid.outputDir:${sbom.outputDir}}") Path swidOutputDir,
                       @Value("${sbom.format}") String format,
                       @Value("${sbom.generator:syft}") String sbomGeneratorName) {
        this.git = git;
        this.sbomGenerator = sbomGenerator;
        this.sbomValidator = sbomValidator;
        this.reportWriter = reportWriter;
        this.swidService = swidService;
        this.spdxSwidConcatenator = spdxSwidConcatenator;
        this.zenodoPublicationService = zenodoPublicationService;
        this.artifactPublicationPlanner = artifactPublicationPlanner;
        this.proofRecordBuilder = proofRecordBuilder;
        this.oneTimeSbomDownloadService = oneTimeSbomDownloadService;
        this.outputDir = outputDir;
        this.swidOutputDir = swidOutputDir;
        this.format = format;
        this.sbomGeneratorName = sbomGeneratorName;
    }
    /**
     * Generates SBOM for the given Git repository.
     *
     * @param gitUrl the Git repository URL to generate SBOM for
     * @return a SbomResult object containing the generated SBOM metadata
     * @throws Exception if SBOM generation fails
     */
    public SbomResult generateForRepo(String gitUrl) throws Exception {
        return generateForRepo(gitUrl, PublicationMode.defaultMode());
    }

    public SbomResult generateForRepo(String gitUrl, PublicationMode publicationMode) throws Exception {
        String normalized = normalizeGitUrl(gitUrl);
        PublicationMode effectiveMode = publicationMode == null ? PublicationMode.defaultMode() : publicationMode;
        ArtifactPublicationPlan publicationPlan = artifactPublicationPlanner.plan(effectiveMode);
        lock.lock();
        Instant ts = Instant.now();
        try {
            Files.createDirectories(outputDir);
            Path repoDir = null;
            PublicationRecord publicationRecord = null;
            try {
                logger.info("Starting SBOM generation for {}", normalized);
                repoDir = git.prepareRepo(normalized);
                String fileName = repoDir.getFileName() + "." + format + ".json";
                Path sbomFilePath = outputDir.resolve(fileName);
                sbomGenerator.generate(repoDir, sbomFilePath);
                sbomValidator.validateOrThrow(sbomFilePath, format);

                String sbomSha256 = HashUtil.sha256Hex(sbomFilePath);
                String commitSha = git.getHeadCommitSha(repoDir);
                String commitShort = shortCommit(commitSha);
                String version = git.describeVersion(repoDir);
                Instant generatedAt = Instant.now();

                SwidGenerationResult swidResult = swidService.generateForSbom(
                        normalized,
                        commitSha,
                        version,
                        fileName,
                        sbomSha256,
                        generatedAt
                );

                Path swidFilePath = null;
                String swidSha256 = null;
                if (StringUtils.hasText(swidResult.swidFileName())) {
                    swidFilePath = swidOutputDir.resolve(swidResult.swidFileName());
                    if (Files.isRegularFile(swidFilePath)) {
                        swidSha256 = HashUtil.sha256Hex(swidFilePath);
                    }
                }

                publicationRecord = new PublicationRecord(
                        normalized,
                        commitSha,
                        commitShort,
                        version,
                        sbomGeneratorName,
                        generatedAt,
                        effectiveMode,
                        new PublicationRecord.Artifact(fileName, sbomFilePath, sbomSha256),
                        swidFilePath == null ? null : new PublicationRecord.Artifact(swidResult.swidFileName(), swidFilePath, swidSha256),
                        null,
                        null,
                        swidResult.swidTagId(),
                        publicationPlan
                );

                ZenodoPublicationResult publicationResult;
                OneTimeSbomDownloadService.DownloadRegistration privateDownload = null;
                try {
                    publicationResult = zenodoPublicationService.reserveIfEnabled(publicationRecord);

                    publicationRecord = maybeAddCombinedArtifact(
                            publicationRecord,
                            swidResult,
                            sbomFilePath,
                            normalized,
                            commitSha,
                            version,
                            publicationResult
                    );
                    publicationRecord = maybeAddProofArtifact(publicationRecord, publicationResult);
                    publicationResult = publicationResult.withArtifactUrls(
                            resolvePublicArtifactUrl(publicationResult.zenodoRecordId(), publicationPlan.publishRawSbom(), publicationRecord.sbom()),
                            resolveSwidPublicUrl(publicationRecord, publicationResult),
                            resolvePublicArtifactUrl(publicationResult.zenodoRecordId(), publicationPlan.publishCombinedSbom(), publicationRecord.combinedSbom()),
                            resolvePublicArtifactUrl(publicationResult.zenodoRecordId(), publicationPlan.publishProofArtifact(), publicationRecord.proofRecord()),
                            null
                    );

                    publicationResult = zenodoPublicationService.publishReservedIfEnabled(publicationRecord, publicationResult);
                    boolean preservePrivateSbom = shouldPreservePrivateSbom(effectiveMode, publicationRecord);
                    cleanupPublishedArtifacts(publicationRecord, publicationResult, preservePrivateSbom);
                    if (preservePrivateSbom) {
                        privateDownload = oneTimeSbomDownloadService.createDownload(
                                publicationRecord.sbom().filePath(),
                                publicationRecord.sbom().fileName(),
                                publicationRecord.sbom().sha256()
                        );
                    }
                } catch (ZenodoPublicationException e) {
                    SbomResult partial = mapToSbomResult(
                            publicationRecord,
                            swidResult,
                            publicationResultFromException(e),
                            e.getMessage(),
                            null
                    );
                    reportWriter.record(
                            ts,
                            normalized,
                            "FAILED",
                            e.getMessage(),
                            fileName,
                            sbomSha256,
                            swidResult.swidTagId(),
                            swidResult.swidFileName()
                    );
                    throw new SbomPublicationException(e.getMessage(), partial, e);
                }

                logger.info("SBOM generated successfully: {}", sbomFilePath);
                reportWriter.record(
                        ts,
                        normalized,
                        "OK",
                        sbomFilePath.toString(),
                        fileName,
                        sbomSha256,
                        swidResult.swidTagId(),
                        swidResult.swidFileName()
                );

                return mapToSbomResult(publicationRecord, swidResult, publicationResult, null, privateDownload);
            } catch (SbomPublicationException e) {
                throw e;
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                reportWriter.record(ts, normalized, "FAILED", msg, "", "", "", "");
                throw e;
            } finally {
                if (repoDir != null) {
                    git.deleteRepo(repoDir);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private ZenodoPublicationResult publicationResultFromException(ZenodoPublicationException exception) {
        if (exception.getPartialResult() != null) {
            return exception.getPartialResult();
        }
        PublicationStage failedStage = exception.getFailedStage() == null
                ? PublicationStage.FAILED
                : exception.getFailedStage();
        return ZenodoPublicationResult.disabled().withFailure(failedStage, exception.getMessage());
    }

    private SbomResult mapToSbomResult(PublicationRecord publicationRecord,
                                       SwidGenerationResult swidResult,
                                       ZenodoPublicationResult publicationResult,
                                       String failureMessage,
                                       OneTimeSbomDownloadService.DownloadRegistration privateDownload) {
        PublicationRecord.Artifact sbom = publicationRecord.sbom();
        PublicationRecord.Artifact combined = publicationRecord.combinedSbom();
        PublicationRecord.Artifact proofRecord = publicationRecord.proofRecord();
        boolean rawSbomPublished = publicationResult != null
                && publicationResult.zenodoPublished()
                && StringUtils.hasText(publicationResult.publicSbomUrl());
        boolean combinedArtifactPublished = publicationResult != null
                && publicationResult.zenodoPublished()
                && StringUtils.hasText(publicationResult.publicCombinedSbomUrl());

        return new SbomResult(
                sbom == null ? null : sbom.fileName(),
                sbom == null ? null : sbom.filePath(),
                sbom == null ? null : sbom.sha256(),
                publicationRecord.commitSha(),
                publicationRecord.resolvedVersion(),
                publicationRecord.generatedAt(),
                publicationRecord.publicationMode(),
                swidResult.swidTagId(),
                swidResult.swidFileName(),
                combined == null ? null : combined.fileName(),
                proofRecord == null ? null : proofRecord.fileName(),
                publicationResult == null ? null : publicationResult.publicSbomUrl(),
                publicationResult == null ? null : publicationResult.publicSwidUrl(),
                publicationResult == null ? null : publicationResult.publicCombinedSbomUrl(),
                publicationResult == null ? null : publicationResult.publicProofRecordUrl(),
                publicationResult == null ? null : publicationResult.doi(),
                publicationResult == null ? null : publicationResult.doiUrl(),
                publicationResult == null ? null : publicationResult.zenodoRecordId(),
                publicationResult == null ? null : publicationResult.zenodoConceptRecid(),
                publicationResult == null ? null : publicationResult.zenodoRecordUrl(),
                publicationResult == null ? null : publicationResult.doiReservedAt(),
                publicationResult == null ? null : publicationResult.doiRegisteredObservedAt(),
                publicationResult == null ? null : publicationResult.publicationManifestFileName(),
                publicationResult == null ? null : publicationResult.publicationManifestPath(),
                publicationResult == null ? null : publicationResult.publicPublicationManifestUrl(),
                publicationResult != null && publicationResult.zenodoPublished(),
                publicationResult == null ? null : publicationResult.zenodoDraftUrl(),
                publicationResult == null ? PublicationStage.LOCAL_GENERATION : publicationResult.publicationStage(),
                publicationResult == null ? null : publicationResult.publicationStatus(),
                publicationResult == null ? PublicationStage.LOCAL_GENERATION.progressMessage() : publicationResult.progressMessage(),
                rawSbomPublished,
                combinedArtifactPublished,
                failureMessage == null ? (publicationResult == null ? null : publicationResult.failureMessage()) : failureMessage,
                privateDownload == null ? null : privateDownload.downloadUrl(),
                privateDownload == null ? null : privateDownload.expiresAt(),
                privateDownload != null && privateDownload.available(),
                privateDownload == null ? null : privateDownload.message()
        );
    }

    private boolean shouldPreservePrivateSbom(PublicationMode publicationMode, PublicationRecord publicationRecord) {
        return publicationMode == PublicationMode.HASH_ONLY_PUBLIC
                && publicationRecord != null
                && publicationRecord.sbom() != null
                && Files.isRegularFile(publicationRecord.sbom().filePath());
    }

    private PublicationRecord maybeAddCombinedArtifact(PublicationRecord publicationRecord,
                                                       SwidGenerationResult swidResult,
                                                       Path sbomFilePath,
                                                       String gitUrl,
                                                       String commitSha,
                                                       String version,
                                                       ZenodoPublicationResult publicationResult) {
        if (!publicationRecord.publicationPlan().createCombinedArtifact()
                || !StringUtils.hasText(swidResult.swidTagId())
                || !StringUtils.hasText(swidResult.swidFileName())) {
            return publicationRecord;
        }

        String publicSwidUrl = zenodoPublicationService.buildPublicArtifactUrl(
                publicationResult == null ? null : publicationResult.zenodoRecordId(),
                swidResult.swidFileName()
        );
        CombinedSbomResult combinedSbomResult = spdxSwidConcatenator.concatenate(
                sbomFilePath,
                gitUrl,
                commitSha,
                version,
                swidResult.swidTagId(),
                publicationResult == null ? null : publicationResult.doiUrl(),
                publicSwidUrl
        );
        Path combinedSbomFilePath = combinedSbomResult.filePath();
        String combinedSbomSha256 = null;
        if (Files.isRegularFile(combinedSbomFilePath)) {
            combinedSbomSha256 = HashUtil.sha256Hex(combinedSbomFilePath);
        }
        return publicationRecord.withCombinedSbom(
                new PublicationRecord.Artifact(
                        combinedSbomResult.fileName(),
                        combinedSbomFilePath,
                        combinedSbomSha256
                )
        );
    }

    private PublicationRecord maybeAddProofArtifact(PublicationRecord publicationRecord,
                                                    ZenodoPublicationResult publicationResult) {
        if (!publicationRecord.publicationPlan().createProofArtifact()) {
            return publicationRecord;
        }
        return publicationRecord.withProofRecord(
                proofRecordBuilder.writeProofRecord(publicationRecord, publicationResult, format)
        );
    }

    private String resolveSwidPublicUrl(PublicationRecord publicationRecord,
                                        ZenodoPublicationResult publicationResult) {
        return resolvePublicArtifactUrl(
                publicationResult == null ? null : publicationResult.zenodoRecordId(),
                publicationRecord.publicationPlan().publishSwid(),
                publicationRecord.swid()
        );
    }

    private String resolvePublicArtifactUrl(String zenodoRecordId,
                                            boolean publishArtifact,
                                            PublicationRecord.Artifact artifact) {
        if (!publishArtifact || artifact == null) {
            return null;
        }
        return zenodoPublicationService.buildPublicArtifactUrl(zenodoRecordId, artifact.fileName());
    }

    private void cleanupPublishedArtifacts(PublicationRecord publicationRecord,
                                           ZenodoPublicationResult publicationResult,
                                           boolean preservePrivateSbom) {
        if (publicationRecord == null || publicationResult == null || !publicationResult.zenodoPublished()) {
            return;
        }
        if (!preservePrivateSbom) {
            deleteRequiredFile(publicationRecord.sbom() == null ? null : publicationRecord.sbom().filePath());
        }
        deleteRequiredFile(publicationRecord.swid() == null ? null : publicationRecord.swid().filePath());
        deleteRequiredFile(publicationRecord.combinedSbom() == null ? null : publicationRecord.combinedSbom().filePath());
        deleteRequiredFile(publicationRecord.proofRecord() == null ? null : publicationRecord.proofRecord().filePath());
        deleteRequiredFile(publicationResult.publicationManifestPath());
    }

    private void deleteRequiredFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete generated artifact: " + path, e);
        }
    }

    private String shortCommit(String commitSha) {
        if (!StringUtils.hasText(commitSha)) {
            return "unknown";
        }
        String normalized = commitSha.trim().replaceAll("[^A-Za-z0-9]", "");
        if (!StringUtils.hasText(normalized)) {
            return "unknown";
        }
        return normalized.length() > 7 ? normalized.substring(0, 7) : normalized;
    }

    private String normalizeGitUrl(String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank()) {
            throw new IllegalArgumentException("Git URL is required.");
        }
        String trimmed = gitUrl.trim();
        if (!trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("Only public https:// Git URLs are supported.");
        }
        return trimmed;
    }
}
