package com.sbom.publicationrecord.publication;

public enum PublicationMode {
    FULL_SBOM_PUBLIC,
    HASH_ONLY_PUBLIC;

    public static PublicationMode defaultMode() {
        return FULL_SBOM_PUBLIC;
    }
}
