package com.sbom.publicationrecord.publication;

public enum PublicationStage {
    LOCAL_GENERATION("Generating SBOM/SWID..."),
    DOI_RESERVING("Reserving DOI..."),
    DOI_RESERVED("Reserving DOI..."),
    ARTIFACTS_UPLOADING("Uploading artifacts..."),
    ARTIFACTS_UPLOADED("Uploading artifacts..."),
    RECORD_PUBLISHING("Publishing record..."),
    RECORD_PUBLISHED("Publishing record..."),
    DOI_VERIFYING("Verifying DOI..."),
    DOI_VERIFIED("DOI verified."),
    FAILED("Publication failed.");

    private final String progressMessage;

    PublicationStage(String progressMessage) {
        this.progressMessage = progressMessage;
    }

    /**
     * Returns the progress message associated with this publication stage.
     *
     * @return The progress message associated with this publication stage.
     */
    public String progressMessage() {
        return progressMessage;
    }
}
