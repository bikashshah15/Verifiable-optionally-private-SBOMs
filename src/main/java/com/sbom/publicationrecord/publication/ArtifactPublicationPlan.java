package com.sbom.publicationrecord.publication;

public record ArtifactPublicationPlan(PublicationMode publicationMode,
                                      boolean createCombinedArtifact,
                                      boolean publishRawSbom,
                                      boolean publishSwid,
                                      boolean publishCombinedSbom,
                                      boolean createProofArtifact,
                                      boolean publishProofArtifact,
                                      boolean publishPublicationManifest) {
}
