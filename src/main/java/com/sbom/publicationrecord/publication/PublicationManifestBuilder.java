package com.sbom.publicationrecord.publication;

import com.sbom.publicationrecord.swid.HashUtil;
import com.sbom.publicationrecord.zenodo.ZenodoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

@Component
public class PublicationManifestBuilder {
    private static final Logger logger = LoggerFactory.getLogger(PublicationManifestBuilder.class);
    private static final String SCHEMA_VERSION = "1.0";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path manifestOutputDir;

    public PublicationManifestBuilder(ZenodoProperties properties) {
        this.manifestOutputDir = properties.getManifestOutputDir();
    }

    public ManifestResult writeManifest(PublicationRecord record, ZenodoPublicationResult publicationResult) {
        try {
            Files.createDirectories(manifestOutputDir);
            String fileName = buildManifestFileName(record);
            Path filePath = manifestOutputDir.resolve(fileName).toAbsolutePath().normalize();
            if (!filePath.startsWith(manifestOutputDir.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Invalid manifest path.");
            }

            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("gitUrl", record.gitUrl());
            root.put("commitSha", record.commitSha());
            root.put("resolvedVersion", record.resolvedVersion());
            root.put("sbomGenerator", record.sbomGenerator());
            root.put("publicationMode", record.publicationMode().name());

            ObjectNode sbom = root.putObject("sbom");
            if (record.sbom() != null) {
                sbom.put("fileName", record.sbom().fileName());
                sbom.put("sha256", record.sbom().sha256());
            }

            ObjectNode swid = root.putObject("swid");
            if (record.swid() != null) {
                swid.put("fileName", record.swid().fileName());
                swid.put("sha256", record.swid().sha256());
            }
            swid.put("tagId", record.swidTagId());

            ObjectNode combined = root.putObject("combinedSbom");
            if (record.combinedSbom() != null) {
                combined.put("fileName", record.combinedSbom().fileName());
                combined.put("sha256", record.combinedSbom().sha256());
            }

            ObjectNode proofRecord = root.putObject("proofRecord");
            if (record.proofRecord() != null) {
                proofRecord.put("fileName", record.proofRecord().fileName());
                proofRecord.put("sha256", record.proofRecord().sha256());
            }

            root.put("generatedAt", record.generatedAt() == null ? null : record.generatedAt().toString());
            root.put("doiReserved", StringUtils.hasText(publicationResult.doi()));
            root.put("doiReservedAt", publicationResult.doiReservedAt() == null ? null : publicationResult.doiReservedAt().toString());
            root.put("zenodoRecordId", publicationResult.zenodoRecordId());
            root.put("zenodoConceptRecid", publicationResult.zenodoConceptRecid());
            root.put("doi", publicationResult.doi());
            root.put("doiUrl", publicationResult.doiUrl());
            root.put("doiRegisteredObservedAt", publicationResult.doiRegisteredObservedAt() == null ? null : publicationResult.doiRegisteredObservedAt().toString());
            root.put("zenodoRecordUrl", publicationResult.zenodoRecordUrl());
            root.put("publicationStage", publicationResult.publicationStage() == null ? null : publicationResult.publicationStage().name());
            root.put("publicationStatus", publicationResult.publicationStatus() == null ? null : publicationResult.publicationStatus().name());
            root.put("zenodoDraftUrl", publicationResult.zenodoDraftUrl());
            root.put("publicSbomUrl", publicationResult.publicSbomUrl());
            root.put("publicSwidUrl", publicationResult.publicSwidUrl());
            root.put("publicCombinedSbomUrl", publicationResult.publicCombinedSbomUrl());
            root.put("publicProofRecordUrl", publicationResult.publicProofRecordUrl());
            root.put("publicPublicationManifestUrl", publicationResult.publicPublicationManifestUrl());
            root.put("rawSbomPublished", isPubliclyPublished(publicationResult.zenodoPublished(), publicationResult.publicSbomUrl()));
            root.put("swidPublished", isPubliclyPublished(publicationResult.zenodoPublished(), publicationResult.publicSwidUrl()));
            root.put("combinedArtifactPublished", isPubliclyPublished(publicationResult.zenodoPublished(), publicationResult.publicCombinedSbomUrl()));
            root.put("proofRecordPublished", isPubliclyPublished(publicationResult.zenodoPublished(), publicationResult.publicProofRecordUrl()));
            root.set("requestedPublicArtifacts", requestedArtifacts(record, fileName));
            root.set("publishedPublicArtifacts", publishedArtifacts(record, publicationResult, fileName));
            root.put("failureMessage", publicationResult.failureMessage());
            root.put("lastUpdatedAt", Instant.now().toString());

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), root);
            String sha256 = HashUtil.sha256Hex(filePath);

            appendIndexLine(record, publicationResult, fileName, filePath, sha256);
            return new ManifestResult(fileName, filePath, sha256);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write publication record file.", e);
        }
    }

    public Path getManifestOutputDir() {
        return manifestOutputDir;
    }

    private void appendIndexLine(PublicationRecord record,
                                 ZenodoPublicationResult result,
                                 String fileName,
                                 Path filePath,
                                 String sha256) throws Exception {
        Path indexFile = manifestOutputDir.resolve("zenodo-publication-index.jsonl");
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("timestamp", Instant.now().toString());
        entry.put("gitUrl", record.gitUrl());
        entry.put("commitSha", record.commitSha());
        entry.put("publicationStage", result.publicationStage() == null ? null : result.publicationStage().name());
        entry.put("publicationStatus", result.publicationStatus() == null ? null : result.publicationStatus().name());
        entry.put("publicationMode", record.publicationMode().name());
        entry.put("zenodoRecordId", result.zenodoRecordId());
        entry.put("doi", result.doi());
        entry.put("manifestFileName", fileName);
        entry.put("manifestPath", filePath.toString());
        entry.put("manifestSha256", sha256);
        entry.put("rawSbomPublished", isPubliclyPublished(result.zenodoPublished(), result.publicSbomUrl()));
        entry.put("proofRecordPublished", isPubliclyPublished(result.zenodoPublished(), result.publicProofRecordUrl()));
        entry.set("publishedPublicArtifacts", publishedArtifacts(record, result, fileName));

        Files.writeString(
                indexFile,
                objectMapper.writeValueAsString(entry) + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
        logger.info("Updated Zenodo publication index: {}", indexFile.toAbsolutePath().normalize());
    }

    private String buildManifestFileName(PublicationRecord record) {
        String repoName = sanitizeRepoName(record.gitUrl());
        String commit = StringUtils.hasText(record.commitShort()) ? record.commitShort() : "unknown";
        return repoName + "." + commit + ".publication-record.json";
    }

    private String sanitizeRepoName(String gitUrl) {
        String source = StringUtils.hasText(gitUrl) ? gitUrl : "repository";
        String value = source.replace("https://", "").replaceAll("[^A-Za-z0-9._-]", "_");
        if (!StringUtils.hasText(value)) {
            return "repository";
        }
        return value.length() > 120 ? value.substring(0, 120) : value;
    }

    private boolean isPubliclyPublished(boolean zenodoPublished, String publicUrl) {
        return zenodoPublished && StringUtils.hasText(publicUrl);
    }

    private ArrayNode requestedArtifacts(PublicationRecord record, String manifestFileName) {
        ArrayNode requested = objectMapper.createArrayNode();
        ArtifactPublicationPlan plan = record.publicationPlan();
        if (plan.publishRawSbom() && record.sbom() != null) {
            requested.add(record.sbom().fileName());
        }
        if (plan.publishSwid() && record.swid() != null) {
            requested.add(record.swid().fileName());
        }
        if (plan.publishCombinedSbom() && record.combinedSbom() != null) {
            requested.add(record.combinedSbom().fileName());
        }
        if (plan.publishProofArtifact() && record.proofRecord() != null) {
            requested.add(record.proofRecord().fileName());
        }
        if (plan.publishPublicationManifest() && StringUtils.hasText(manifestFileName)) {
            requested.add(manifestFileName);
        }
        return requested;
    }

    private ArrayNode publishedArtifacts(PublicationRecord record,
                                         ZenodoPublicationResult publicationResult,
                                         String manifestFileName) {
        ArrayNode published = objectMapper.createArrayNode();
        if (isPubliclyPublished(publicationResult.zenodoPublished(), publicationResult.publicSbomUrl()) && record.sbom() != null) {
            published.add(record.sbom().fileName());
        }
        if (isPubliclyPublished(publicationResult.zenodoPublished(), publicationResult.publicSwidUrl()) && record.swid() != null) {
            published.add(record.swid().fileName());
        }
        if (isPubliclyPublished(publicationResult.zenodoPublished(), publicationResult.publicCombinedSbomUrl()) && record.combinedSbom() != null) {
            published.add(record.combinedSbom().fileName());
        }
        if (isPubliclyPublished(publicationResult.zenodoPublished(), publicationResult.publicProofRecordUrl()) && record.proofRecord() != null) {
            published.add(record.proofRecord().fileName());
        }
        if (isPubliclyPublished(publicationResult.zenodoPublished(), publicationResult.publicPublicationManifestUrl())
                && StringUtils.hasText(manifestFileName)) {
            published.add(manifestFileName);
        }
        return published;
    }

    public record ManifestResult(String fileName, Path filePath, String sha256) {
    }
}
