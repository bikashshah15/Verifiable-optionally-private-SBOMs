package com.sbom.publicationrecord.service;

public class SbomPublicationException extends RuntimeException{
    private final SbomResult partialResult;

    public SbomPublicationException(String message, SbomResult partialResult, Throwable cause) {
        super(message, cause);
        this.partialResult = partialResult;
    }

    public SbomResult getPartialResult() {
        return partialResult;
    }
}
