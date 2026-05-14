package com.sbom.publicationrecord.publication;

import com.sbom.publicationrecord.swid.HashUtil;
import com.sbom.publicationrecord.zenodo.ZenodoProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ProofRecordBuilder {
    private static final String RECORD_TYPE = "sbom-publication-proof";

    private final JsonMapper objectMapper = new JsonMapper();
    private final Path outputDirectory;

    public ProofRecordBuilder(ZenodoProperties properties) {
        this.outputDirectory = properties.getManifestOutputDir();
    }

    public PublicationRecord.Artifact writeProofRecord(PublicationRecord record,
                                                       ZenodoPublicationResult reservationResult,
                                                       String sbomFormat) {
        try {
            Files.createDirectories(outputDirectory);
            String fileName = buildFileName(record);
            Path filePath = outputDirectory.resolve(fileName).toAbsolutePath().normalize();
            if (!filePath.startsWith(outputDirectory.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Invalid proof record path.");
            }

            ObjectNode root = objectMapper.createObjectNode();
            root.put("recordType", RECORD_TYPE);
            root.put("publicationMode", record.publicationMode().name());
            root.put("gitUrl", record.gitUrl());
            root.put("commitSha", record.commitSha());
            root.put("resolvedVersion", record.resolvedVersion());
            root.put("sbomSha256", record.sbom() == null ? null : record.sbom().sha256());
            root.put("generatedAt", record.generatedAt() == null ? null : record.generatedAt().toString());
            root.put("generatorName", record.sbomGenerator());
            root.put("generatorFormat", sbomFormat);
            root.put("swidTagId", record.swidTagId());
            root.put("doi", reservationResult == null ? null : reservationResult.doi());
            root.put("doiUrl", reservationResult == null ? null : reservationResult.doiUrl());
            root.put("zenodoRecordId", reservationResult == null ? null : reservationResult.zenodoRecordId());
            root.put("zenodoConceptRecid", reservationResult == null ? null : reservationResult.zenodoConceptRecid());
            root.put(
                    "statement",
                    "Full SBOM intentionally withheld from public publication; this record publishes the SBOM hash and provenance metadata only."
            );

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), root);
            return new PublicationRecord.Artifact(fileName, filePath, HashUtil.sha256Hex(filePath));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write proof record artifact.", e);
        }
    }

    private String buildFileName(PublicationRecord record) {
        RepositoryIdentity identity = RepositoryIdentity.fromGitUrl(record.gitUrl());
        String commit = StringUtils.hasText(record.commitShort()) ? record.commitShort() : "unknown";
        return identity.fileStem() + "." + commit + ".proof.json";
    }
}
