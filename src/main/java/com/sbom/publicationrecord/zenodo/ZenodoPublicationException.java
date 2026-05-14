package com.sbom.publicationrecord.zenodo;

import com.sbom.publicationrecord.publication.PublicationStage;
import com.sbom.publicationrecord.publication.ZenodoPublicationResult;

public class ZenodoPublicationException extends RuntimeException{
    private final PublicationStage failedStage;
    private final ZenodoPublicationResult partialResult;

    public ZenodoPublicationException(String message,
                                      PublicationStage failedStage,
                                      ZenodoPublicationResult partialResult,
                                      Throwable cause) {
        super(message, cause);
        this.failedStage = failedStage;
        this.partialResult = partialResult;
    }

    public ZenodoPublicationException(String message,
                                      PublicationStage failedStage,
                                      ZenodoPublicationResult partialResult) {
        this(message, failedStage, partialResult, null);
    }

    public PublicationStage getFailedStage() {
        return failedStage;
    }

    public ZenodoPublicationResult getPartialResult() {
        return partialResult;
    }
}

