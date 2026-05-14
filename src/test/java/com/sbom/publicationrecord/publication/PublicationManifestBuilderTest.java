package com.sbom.publicationrecord.publication;
import com.sbom.publicationrecord.swid.HashUtil;
import com.sbom.publicationrecord.zenodo.ZenodoProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublicationManifestBuilderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void recordsPublicationModeAndActualPublicArtifacts() throws Exception {
        ZenodoProperties properties = new ZenodoProperties();
        properties.setManifestOutputDir(tempDir);
        PublicationManifestBuilder builder = new PublicationManifestBuilder(properties);

        PublicationRecord.Artifact sbom = writeArtifact("repo.spdx-json.json", "{\"sensitive\":true}");
        PublicationRecord.Artifact swid = writeArtifact("repo.swid.xml", "<swid/>");
        PublicationRecord.Artifact proof = writeArtifact("repo.proof.json", "{\"recordType\":\"sbom-publication-proof\"}");
        ArtifactPublicationPlan plan = new ArtifactPublicationPlan(
                PublicationMode.HASH_ONLY_PUBLIC,
                false,
                false,
                false,
                false,
                true,
                true,
                true
        );
        PublicationRecord record = new PublicationRecord(
                "https://github.com/example/repo",
                "0123456789abcdef0123456789abcdef01234567",
                "0123456",
                "v1.2.3",
                "syft",
                Instant.parse("2026-04-17T12:00:00Z"),
                PublicationMode.HASH_ONLY_PUBLIC,
                sbom,
                swid,
                null,
                proof,
                "tag:example",
                plan
        );
        ZenodoPublicationResult result = new ZenodoPublicationResult(
                "10.5072/zenodo.22222",
                "https://doi.org/10.5072/zenodo.22222",
                "22222",
                "22000",
                "https://sandbox.zenodo.org/records/22222",
                "https://sandbox.zenodo.org/deposit/22222",
                null,
                null,
                null,
                "https://sandbox.zenodo.org/records/22222/files/repo.proof.json",
                "https://sandbox.zenodo.org/records/22222/files/repo.publication-record.json",
                Instant.parse("2026-04-17T12:01:00Z"),
                Instant.parse("2026-04-17T12:02:00Z"),
                true,
                PublicationStage.DOI_VERIFIED,
                PublicationStatusView.Published,
                "DOI verified.",
                "repo.publication-record.json",
                tempDir.resolve("repo.publication-record.json"),
                null
        );

        PublicationManifestBuilder.ManifestResult manifest = builder.writeManifest(record, result);

        JsonNode manifestJson = objectMapper.readTree(manifest.filePath().toFile());
        assertEquals("HASH_ONLY_PUBLIC", manifestJson.path("publicationMode").asText());
        assertFalse(manifestJson.path("rawSbomPublished").asBoolean());
        assertTrue(manifestJson.path("proofRecordPublished").asBoolean());
        assertEquals(List.of("repo.proof.json", manifest.fileName()), fileNames(manifestJson.path("publishedPublicArtifacts")));
        assertEquals(List.of("repo.proof.json", manifest.fileName()), fileNames(manifestJson.path("requestedPublicArtifacts")));

        Path indexFile = tempDir.resolve("zenodo-publication-index.jsonl");
        List<String> lines = Files.readAllLines(indexFile);
        JsonNode indexEntry = objectMapper.readTree(lines.get(lines.size() - 1));
        assertEquals("HASH_ONLY_PUBLIC", indexEntry.path("publicationMode").asText());
        assertFalse(indexEntry.path("rawSbomPublished").asBoolean());
        assertTrue(indexEntry.path("proofRecordPublished").asBoolean());
    }

    private PublicationRecord.Artifact writeArtifact(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return new PublicationRecord.Artifact(fileName, file, HashUtil.sha256Hex(file));
    }

    private List<String> fileNames(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            values.add(node.asText());
        }
        return values;
    }
}
