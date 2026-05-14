package com.sbom.publicationrecord.publication;

import java.nio.file.Path;
import java.time.Instant;

public record ZenodoPublicationResult(String doi,
                                      String doiUrl,
                                      String zenodoRecordId,
                                      String zenodoConceptRecid,
                                      String zenodoRecordUrl,
                                      String zenodoDraftUrl,
                                      String publicSbomUrl,
                                      String publicSwidUrl,
                                      String publicCombinedSbomUrl,
                                      String publicProofRecordUrl,
                                      String publicPublicationManifestUrl,
                                      Instant doiReservedAt,
                                      Instant doiRegisteredObservedAt,
                                      boolean zenodoPublished,
                                      PublicationStage publicationStage,
                                      PublicationStatusView publicationStatus,
                                      String progressMessage,
                                      String publicationManifestFileName,
                                      Path publicationManifestPath,
                                      String failureMessage) {

    public static ZenodoPublicationResult disabled() {
        PublicationStage stage = PublicationStage.LOCAL_GENERATION;
        return new ZenodoPublicationResult(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                stage,
                PublicationStatusView.Draft,
                stage.progressMessage(),
                null,
                null,
                null
        );
    }

    public ZenodoPublicationResult withStage(PublicationStage stage, PublicationStatusView status) {
        return new ZenodoPublicationResult(
                doi,
                doiUrl,
                zenodoRecordId,
                zenodoConceptRecid,
                zenodoRecordUrl,
                zenodoDraftUrl,
                publicSbomUrl,
                publicSwidUrl,
                publicCombinedSbomUrl,
                publicProofRecordUrl,
                publicPublicationManifestUrl,
                doiReservedAt,
                doiRegisteredObservedAt,
                zenodoPublished,
                stage,
                status,
                stage == null ? progressMessage : stage.progressMessage(),
                publicationManifestFileName,
                publicationManifestPath,
                failureMessage
        );
    }


    public ZenodoPublicationResult withManifest(String manifestFileName, Path manifestPath) {
        return new ZenodoPublicationResult(
                doi,
                doiUrl,
                zenodoRecordId,
                zenodoConceptRecid,
                zenodoRecordUrl,
                zenodoDraftUrl,
                publicSbomUrl,
                publicSwidUrl,
                publicCombinedSbomUrl,
                publicProofRecordUrl,
                publicPublicationManifestUrl,
                doiReservedAt,
                doiRegisteredObservedAt,
                zenodoPublished,
                publicationStage,
                publicationStatus,
                progressMessage,
                manifestFileName,
                manifestPath,
                failureMessage
        );
    }

    public ZenodoPublicationResult withArtifactUrls(String publicSbomUrl,
                                                    String publicSwidUrl,
                                                    String publicCombinedSbomUrl,
                                                    String publicProofRecordUrl,
                                                    String publicPublicationManifestUrl) {
        return new ZenodoPublicationResult(
                doi,
                doiUrl,
                zenodoRecordId,
                zenodoConceptRecid,
                zenodoRecordUrl,
                zenodoDraftUrl,
                publicSbomUrl,
                publicSwidUrl,
                publicCombinedSbomUrl,
                publicProofRecordUrl,
                publicPublicationManifestUrl,
                doiReservedAt,
                doiRegisteredObservedAt,
                zenodoPublished,
                publicationStage,
                publicationStatus,
                progressMessage,
                publicationManifestFileName,
                publicationManifestPath,
                failureMessage
        );
    }


    public ZenodoPublicationResult withFailure(PublicationStage stage, String errorMessage) {
        return new ZenodoPublicationResult(
                doi,
                doiUrl,
                zenodoRecordId,
                zenodoConceptRecid,
                zenodoRecordUrl,
                zenodoDraftUrl,
                publicSbomUrl,
                publicSwidUrl,
                publicCombinedSbomUrl,
                publicProofRecordUrl,
                publicPublicationManifestUrl,
                doiReservedAt,
                doiRegisteredObservedAt,
                zenodoPublished,
                PublicationStage.FAILED,
                PublicationStatusView.Failed,
                stage == null ? PublicationStage.FAILED.progressMessage() : stage.progressMessage(),
                publicationManifestFileName,
                publicationManifestPath,
                errorMessage
        );
    }
}
