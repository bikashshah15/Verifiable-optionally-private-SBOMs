package com.sbom.publicationrecord.web;

public record ErrorResponse(String status,
                            String message,
                            String publicationStage,
                            String publicationStatus,
                            String progressMessage) {
    public ErrorResponse(String status, String message) {
        this(status, message, null, null, null);
    }
}
