package com.sbom.publicationrecord.swid;

public record SwidGenerationResult(String swidTagId,
                                   String swidFileName) {
    //Convienience factory that returns a "SWID generation disable/not produced" result by setting both fields to null
    public static SwidGenerationResult disabled() {
        return new SwidGenerationResult(null, null);
    }
}
