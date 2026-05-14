package com.sbom.publicationrecord.zenodo;

import com.sbom.publicationrecord.publication.PublicationRecord;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

@Component
public class ZenodoMetadataFactory {
    private final ZenodoProperties properties;
    private final JsonMapper mapper = new JsonMapper();

    public ZenodoMetadataFactory(ZenodoProperties properties) {
        this.properties = properties;
    }
    public ObjectNode buildCreateDepositionPayload(PublicationRecord record,
                                                   boolean reserveDoi,
                                                   List<String> artifactNames) {
        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("upload_type", "software");
        metadata.put("title", buildTitle(record));
        metadata.put("version", record.resolvedVersion());
        metadata.put("description", buildDescription(record, artifactNames));
        metadata.put("access_right", properties.getAccessRight());
        if (StringUtils.hasText(properties.getLicense())) {
            metadata.put("license", properties.getLicense());
        }

        ArrayNode creators = metadata.putArray("creators");
        for (ZenodoProperties.Creator creator : properties.getCreators()) {
            if (!StringUtils.hasText(creator.getName())) {
                continue;
            }
            ObjectNode creatorNode = creators.addObject();
            creatorNode.put("name", creator.getName().trim());
            if (StringUtils.hasText(creator.getAffiliation())) {
                creatorNode.put("affiliation", creator.getAffiliation().trim());
            }
            if (StringUtils.hasText(creator.getOrcid())) {
                creatorNode.put("orcid", creator.getOrcid().trim());
            }
        }

        ArrayNode relatedIdentifiers = metadata.putArray("related_identifiers");
        ObjectNode sourceIdentifier = relatedIdentifiers.addObject();
        sourceIdentifier.put("identifier", record.gitUrl());
        sourceIdentifier.put("relation", "isDerivedFrom");
        sourceIdentifier.put("scheme", "url");

        if (reserveDoi) {
            metadata.put("prereserve_doi", true);
        }

        ObjectNode root = mapper.createObjectNode();
        root.set("metadata", metadata);
        return root;
    }

    private String buildTitle(PublicationRecord record) {
        String prefix = StringUtils.hasText(properties.getTitlePrefix())
                ? properties.getTitlePrefix().trim()
                : "v-ops Verifiable SBOM Publication Record for";
        return prefix + " " + repositoryName(record.gitUrl()) + " @ " + record.commitShort();
    }

    private String buildDescription(PublicationRecord record, List<String> artifactNames) {
        String artifactSummary = artifactNames == null || artifactNames.isEmpty()
                ? "none"
                : String.join(", ", artifactNames);
        return "Automated SBOM publication record. "
                + "Source: " + record.gitUrl() + ". "
                + "Commit: " + record.commitSha() + ". "
                + "SBOM generator: " + record.sbomGenerator() + ". "
                + "Artifacts: " + artifactSummary + ".";
    }

    private String repositoryName(String gitUrl) {
        if (!StringUtils.hasText(gitUrl)) {
            return "repository";
        }
        String normalized = gitUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int slash = normalized.lastIndexOf('/');
        if (slash < 0 || slash == normalized.length() - 1) {
            return normalized;
        }
        String repo = normalized.substring(slash + 1);
        if (repo.endsWith(".git")) {
            repo = repo.substring(0, repo.length() - 4);
        }
        int ownerSlash = normalized.lastIndexOf('/', slash - 1);
        if (ownerSlash >= 0 && ownerSlash < slash - 1) {
            String owner = normalized.substring(ownerSlash + 1, slash);
            return owner + "/" + repo;
        }
        return repo;
    }

}
