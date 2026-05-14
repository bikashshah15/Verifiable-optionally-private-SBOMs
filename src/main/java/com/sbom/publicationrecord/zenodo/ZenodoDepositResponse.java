package com.sbom.publicationrecord.zenodo;

import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

public record ZenodoDepositResponse(String id,
                                    String conceptRecid,
                                    String doi,
                                    String doiUrl,
                                    String bucketUrl,
                                    String htmlUrl,
                                    boolean submitted,
                                    String state) {
    public static ZenodoDepositResponse fromJson(JsonNode root) {
        if (root == null || root.isNull()) {
            throw new IllegalArgumentException("Zenodo response payload is empty.");
        }

        JsonNode metadata = root.get("metadata");
        JsonNode links = root.get("links");
        JsonNode prereserveDoi = metadata == null ? null : metadata.get("prereserve_doi");

        String doi = firstNonBlank(
                asText(root.get("doi")),
                asText(root.get("metadata") == null ? null : root.get("metadata").get("doi")),
                asText(prereserveDoi == null ? null : prereserveDoi.get("doi"))
        );
        String doiUrl = asText(root.get("doi_url"));

        return new ZenodoDepositResponse(
                asText(root.get("id")),
                asText(root.get("conceptrecid")),
                doi,
                doiUrl,
                asText(links == null ? null : links.get("bucket")),
                asText(links == null ? null : links.get("html")),
                asBoolean(root.get("submitted")),
                asText(root.get("state"))
        );
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asString();
        return StringUtils.hasText(value) ? value : null;
    }

    private static boolean asBoolean(JsonNode node) {
        return node != null && !node.isNull() && node.asBoolean();
    }

}
