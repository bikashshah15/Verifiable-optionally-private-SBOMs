package com.sbom.publicationrecord.publication;

import com.sbom.publicationrecord.zenodo.ZenodoProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactPublicationPlannerTest {

    @Test
    void fullSbomPublicPublishesConfiguredPublicArtifacts() {
        ZenodoProperties properties = new ZenodoProperties();
        properties.setUploadRawSbom(true);
        properties.setUploadSwid(true);
        properties.setUploadCombinedSbom(true);
        properties.setUploadPublicationManifest(true);

        ArtifactPublicationPlan plan = new ArtifactPublicationPlanner(properties).plan(PublicationMode.FULL_SBOM_PUBLIC);

        assertTrue(plan.createCombinedArtifact());
        assertTrue(plan.publishRawSbom());
        assertTrue(plan.publishSwid());
        assertTrue(plan.publishCombinedSbom());
        assertTrue(plan.publishPublicationManifest());
        assertFalse(plan.createProofArtifact());
        assertFalse(plan.publishProofArtifact());
    }

    @Test
    void hashOnlyPublicNeverPublishesRawSbomEvenWhenGlobalUploadIsEnabled() {
        ZenodoProperties properties = new ZenodoProperties();
        properties.setUploadRawSbom(true);
        properties.setUploadSwid(true);
        properties.setUploadCombinedSbom(true);
        properties.setUploadPublicationManifest(true);

        ArtifactPublicationPlan plan = new ArtifactPublicationPlanner(properties).plan(PublicationMode.HASH_ONLY_PUBLIC);

        assertFalse(plan.createCombinedArtifact());
        assertFalse(plan.publishRawSbom());
        assertFalse(plan.publishSwid());
        assertFalse(plan.publishCombinedSbom());
        assertTrue(plan.createProofArtifact());
        assertTrue(plan.publishProofArtifact());
        assertTrue(plan.publishPublicationManifest());
    }
}
