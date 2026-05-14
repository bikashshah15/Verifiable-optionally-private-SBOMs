package com.sbom.publicationrecord.publication;

public enum PublicationStatusView {
    Draft,
    Publishing,
    Published,
    Failed;


    public static PublicationStatusView fromStage(PublicationStage stage, boolean autoPublish) {
        if (stage == null) {
            return autoPublish ? Publishing : Draft;
        }
        return switch (stage) {
            case DOI_VERIFIED -> Published;
            case FAILED -> Failed;
            case RECORD_PUBLISHING, RECORD_PUBLISHED, DOI_VERIFYING -> Publishing;
            default -> autoPublish ? Publishing : Draft;
        };
    }
}
