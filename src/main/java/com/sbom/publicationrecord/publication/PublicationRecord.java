package com.sbom.publicationrecord.publication;

import java.nio.file.Path;
import java.time.Instant;

public record PublicationRecord(String gitUrl,
                                String commitSha,
                                String commitShort,
                                String resolvedVersion,
                                String sbomGenerator,
                                Instant generatedAt,
                                PublicationMode publicationMode,
                                Artifact sbom,
                                Artifact swid,
                                Artifact combinedSbom,
                                Artifact proofRecord,
                                String swidTagId,
                                ArtifactPublicationPlan publicationPlan) {
    public record Artifact(String fileName, Path filePath, String sha256) {
    }

    public PublicationRecord withCombinedSbom(Artifact combinedArtifact) {
        return new PublicationRecord(
                gitUrl,
                commitSha,
                commitShort,
                resolvedVersion,
                sbomGenerator,
                generatedAt,
                publicationMode,
                sbom,
                swid,
                combinedArtifact,
                proofRecord,
                swidTagId,
                publicationPlan
        );
    }

    public PublicationRecord withProofRecord(Artifact proofArtifact) {
        return new PublicationRecord(
                gitUrl,
                commitSha,
                commitShort,
                resolvedVersion,
                sbomGenerator,
                generatedAt,
                publicationMode,
                sbom,
                swid,
                combinedSbom,
                proofArtifact,
                swidTagId,
                publicationPlan
        );
    }
}
