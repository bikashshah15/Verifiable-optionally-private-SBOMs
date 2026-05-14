package com.sbom.publicationrecord.zenodo;

import com.sbom.publicationrecord.publication.ArtifactPublicationPlan;
import com.sbom.publicationrecord.publication.PublicationMode;
import com.sbom.publicationrecord.publication.PublicationRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZenodoMetadataFactoryTest {

    @Test
    void metadataIncludesBothConfiguredCreatorsInOrderAndVOpsTitle() {
        ZenodoProperties properties = new ZenodoProperties();
        properties.setTitlePrefix("v-ops Verifiable SBOM Publication Record for");
        properties.setCreators(List.of(
                creator("Shah, Bikash", "University of North Carolina at Charlotte"),
                creator("Camp, L. Jean", "University of North Carolina at Charlotte")
        ));

        ObjectNode payload = new ZenodoMetadataFactory(properties)
                .buildCreateDepositionPayload(record(), true, List.of("repo.spdx-json.json"));
        JsonNode metadata = payload.path("metadata");
        JsonNode creators = metadata.path("creators");

        assertEquals("v-ops Verifiable SBOM Publication Record for example/repo @ 0123456", metadata.path("title").asText());
        assertEquals(2, creators.size());
        assertEquals("Shah, Bikash", creators.get(0).path("name").asText());
        assertEquals("University of North Carolina at Charlotte", creators.get(0).path("affiliation").asText());
        assertEquals("Camp, L. Jean", creators.get(1).path("name").asText());
        assertEquals("University of North Carolina at Charlotte", creators.get(1).path("affiliation").asText());
    }

    private static ZenodoProperties.Creator creator(String name, String affiliation) {
        ZenodoProperties.Creator creator = new ZenodoProperties.Creator();
        creator.setName(name);
        creator.setAffiliation(affiliation);
        return creator;
    }

    private static PublicationRecord record() {
        return new PublicationRecord(
                "https://github.com/example/repo",
                "0123456789abcdef0123456789abcdef01234567",
                "0123456",
                "v1.2.3",
                "syft",
                Instant.parse("2026-04-17T12:00:00Z"),
                PublicationMode.FULL_SBOM_PUBLIC,
                new PublicationRecord.Artifact("repo.spdx-json.json", Path.of("repo.spdx-json.json"), "abc123"),
                null,
                null,
                null,
                "tag:example",
                new ArtifactPublicationPlan(
                        PublicationMode.FULL_SBOM_PUBLIC,
                        true,
                        true,
                        true,
                        true,
                        false,
                        false,
                        true
                )
        );
    }
}
