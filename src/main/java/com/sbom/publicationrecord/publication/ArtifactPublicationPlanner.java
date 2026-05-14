package com.sbom.publicationrecord.publication;

import com.sbom.publicationrecord.zenodo.ZenodoProperties;
import org.springframework.stereotype.Component;

@Component
public class ArtifactPublicationPlanner {
    private final ZenodoProperties properties;

    public ArtifactPublicationPlanner(ZenodoProperties properties) {
        this.properties = properties;
    }

    public ArtifactPublicationPlan plan(PublicationMode requestedMode) {
        PublicationMode mode = requestedMode == null ? PublicationMode.defaultMode() : requestedMode;
        if (mode == PublicationMode.HASH_ONLY_PUBLIC) {
            // Hash-only mode still generates local SBOM/SWID provenance, but only the proof record is public.
            return new ArtifactPublicationPlan(
                    mode,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    properties.isUploadPublicationManifest()
            );
        }

        return new ArtifactPublicationPlan(
                mode,
                true,
                properties.isUploadRawSbom(),
                properties.isUploadSwid(),
                properties.isUploadCombinedSbom(),
                false,
                false,
                properties.isUploadPublicationManifest()
        );
    }
}
