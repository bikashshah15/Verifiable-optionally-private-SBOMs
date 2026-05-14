package com.sbom.publicationrecord.zenodo;

public class ZenodoClientException extends RuntimeException{
    private final int statusCode;
    private final String responseBody;

    public ZenodoClientException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
