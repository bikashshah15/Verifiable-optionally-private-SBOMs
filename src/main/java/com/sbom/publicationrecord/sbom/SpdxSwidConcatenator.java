package com.sbom.publicationrecord.sbom;

import com.sbom.publicationrecord.publication.RepositoryIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SpdxSwidConcatenator {
    private static final String SECURITY_CATEGORY = "SECURITY";
    private static final String SWID_REFERENCE_TYPE = "swid";
    private static final String URL_REFERENCE_TYPE = "url";
    private static final String DESCRIBES = "DESCRIBES";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path sbomSwidOutputDirectory;

    public SpdxSwidConcatenator(@Value("${sbomSwid.outputDir:${user.dir}/output/sbom-swid}") Path sbomSwidOutputDirectory) {
        this.sbomSwidOutputDirectory = sbomSwidOutputDirectory;
    }

    public CombinedSbomResult concatenate(Path sbomSpdxJsonFile,
                                          String gitUrl,
                                          String commitSha,
                                          String resolvedVersion,
                                          String swidTagId,
                                          String canonicalPublicationUrl,
                                          String publicSwidUrl) {
        if (sbomSpdxJsonFile == null) {
            throw new IllegalArgumentException("SBOM SPDX JSON file cannot be null");
        }
        if (!StringUtils.hasText(gitUrl)) {
            throw new IllegalArgumentException("Git URL is required for combined SPDX/SWID generation.");
        }
        if (!StringUtils.hasText(swidTagId)) {
            throw new IllegalArgumentException("SWID tag ID cannot be null or empty");
        }
        validateOptionalAbsoluteHttpUrl(canonicalPublicationUrl);
        validateOptionalAbsoluteHttpUrl(publicSwidUrl);

        try {
            JsonNode jsonNode = objectMapper.readTree(sbomSpdxJsonFile.toFile());
            if (!(jsonNode instanceof ObjectNode root)) {
                throw new IllegalArgumentException("SBOM SPDX JSON file is not an object");
            }

            RepositoryIdentity repositoryIdentity = RepositoryIdentity.fromGitUrl(gitUrl);
            MainPackage mainPackage = resolveMainPackage(root);
            rewriteDocumentMetadata(root, repositoryIdentity, commitSha, canonicalPublicationUrl);
            rewriteMainPackage(mainPackage.packageNode(), repositoryIdentity, commitSha, resolvedVersion);
            rewriteRelationships(root, mainPackage.spdxId(), repositoryIdentity.stablePackageSpdxId());
            rewriteDocumentDescribes(root, mainPackage.spdxId(), repositoryIdentity.stablePackageSpdxId());
            rewritePublicationRefs(mainPackage.packageNode(), swidTagId, canonicalPublicationUrl, publicSwidUrl);

            String outputFileName = buildCombinedFileName(repositoryIdentity, commitSha);
            Path normalizedOutputDir = sbomSwidOutputDirectory.toAbsolutePath().normalize();
            Files.createDirectories(normalizedOutputDir);

            Path outputFile = normalizedOutputDir.resolve(outputFileName).normalize();
            if (!outputFile.startsWith(normalizedOutputDir)) {
                throw new IllegalArgumentException("Invalid output file name.");
            }

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), root);
            return new CombinedSbomResult(outputFileName, outputFile);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to concatenate SPDX and SWID.", e);
        }
    }

    private void rewriteDocumentMetadata(ObjectNode root,
                                         RepositoryIdentity repositoryIdentity,
                                         String commitSha,
                                         String canonicalPublicationUrl) {
        root.put("name", repositoryIdentity.displayName());
        root.put("documentNamespace", repositoryIdentity.documentNamespace(canonicalPublicationUrl, commitSha));
    }

    private void rewriteMainPackage(ObjectNode packageNode,
                                    RepositoryIdentity repositoryIdentity,
                                    String commitSha,
                                    String resolvedVersion) {
        packageNode.put("name", repositoryIdentity.displayName());
        packageNode.put("SPDXID", repositoryIdentity.stablePackageSpdxId());
        packageNode.put("versionInfo", StringUtils.hasText(resolvedVersion) ? resolvedVersion : "NOASSERTION");
        String supplier = repositoryIdentity.supplier();
        packageNode.put("supplier", supplier);
        packageNode.put("originator", supplier);
        packageNode.put("downloadLocation", repositoryIdentity.pinnedDownloadLocation(commitSha));
        packageNode.put("primaryPackagePurpose", "SOURCE");
    }

    private void rewritePublicationRefs(ObjectNode packageNode,
                                        String swidTagId,
                                        String canonicalPublicationUrl,
                                        String publicSwidUrl) {
        ArrayNode externalRefs = ensureArray(packageNode, "externalRefs");
        ArrayNode retainedRefs = objectMapper.createArrayNode();
        for (JsonNode referenceNode : externalRefs) {
            if (!(referenceNode instanceof ObjectNode reference)) {
                retainedRefs.add(referenceNode);
                continue;
            }

            String category = asText(reference.get("referenceCategory"));
            String type = asText(reference.get("referenceType"));
            if (SECURITY_CATEGORY.equals(category)
                    && (SWID_REFERENCE_TYPE.equals(type) || URL_REFERENCE_TYPE.equals(type))) {
                continue;
            }
            retainedRefs.add(reference);
        }

        externalRefs.removeAll();
        externalRefs.addAll(retainedRefs);
        addExternalRef(externalRefs, SWID_REFERENCE_TYPE, swidTagId);

        Set<String> urlRefs = new LinkedHashSet<>();
        if (StringUtils.hasText(canonicalPublicationUrl)) {
            urlRefs.add(canonicalPublicationUrl);
        }
        if (StringUtils.hasText(publicSwidUrl)) {
            urlRefs.add(publicSwidUrl);
        }
        for (String urlRef : urlRefs) {
            addExternalRef(externalRefs, URL_REFERENCE_TYPE, urlRef);
        }
    }

    private void addExternalRef(ArrayNode externalRefs, String referenceType, String referenceLocator) {
        if (!StringUtils.hasText(referenceLocator)) {
            return;
        }
        ObjectNode addedRef = externalRefs.addObject();
        addedRef.put("referenceCategory", SECURITY_CATEGORY);
        addedRef.put("referenceType", referenceType);
        addedRef.put("referenceLocator", referenceLocator);
    }

    private void rewriteRelationships(ObjectNode root, String originalSpdxId, String rewrittenSpdxId) {
        if (!StringUtils.hasText(originalSpdxId) || originalSpdxId.equals(rewrittenSpdxId)) {
            return;
        }
        JsonNode relationshipsNode = root.get("relationships");
        if (!(relationshipsNode instanceof ArrayNode relationships)) {
            return;
        }
        for (JsonNode relationshipNode : relationships) {
            if (!(relationshipNode instanceof ObjectNode relationship)) {
                continue;
            }
            if (originalSpdxId.equals(asText(relationship.get("spdxElementId")))) {
                relationship.put("spdxElementId", rewrittenSpdxId);
            }
            if (originalSpdxId.equals(asText(relationship.get("relatedSpdxElement")))) {
                relationship.put("relatedSpdxElement", rewrittenSpdxId);
            }
        }
    }

    private void rewriteDocumentDescribes(ObjectNode root, String originalSpdxId, String rewrittenSpdxId) {
        JsonNode describesNode = root.get("documentDescribes");
        if (!(describesNode instanceof ArrayNode describes)) {
            return;
        }
        for (int index = 0; index < describes.size(); index++) {
            JsonNode entry = describes.get(index);
            if (originalSpdxId.equals(asText(entry))) {
                describes.set(index, objectMapper.getNodeFactory().textNode(rewrittenSpdxId));
            }
        }
    }

    private String buildCombinedFileName(RepositoryIdentity repositoryIdentity, String commitSha) {
        String commitShort = resolveCommitShort(commitSha);
        if (!StringUtils.hasText(commitShort)) {
            commitShort = "unknown";
        }
        return repositoryIdentity.fileStem() + "." + commitShort + ".spdx-json+swid.json";
    }

    private String resolveCommitShort(String commitSha) {
        if (!StringUtils.hasText(commitSha)) {
            return null;
        }
        String sanitized = commitSha.trim().replaceAll("[^A-Za-z0-9]", "");
        if (!StringUtils.hasText(sanitized)) {
            return null;
        }
        return sanitized.length() > 7 ? sanitized.substring(0, 7) : sanitized;
    }

    private ArrayNode ensureArray(ObjectNode node, String fieldName) {
        JsonNode existingNode = node.get(fieldName);
        if (existingNode instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        return node.putArray(fieldName);
    }

    private MainPackage resolveMainPackage(ObjectNode root) {
        JsonNode packagesNode = root.get("packages");
        if (!(packagesNode instanceof ArrayNode packages) || packages.isEmpty()) {
            throw new IllegalArgumentException("SBOM SPDX JSON file does not contain packages");
        }

        String describedPackageId = resolveDescribedPackageId(root);
        if (StringUtils.hasText(describedPackageId)) {
            ObjectNode describedPackage = findPackageBySpdxId(packages, describedPackageId);
            if (describedPackage != null) {
                return new MainPackage(describedPackageId, describedPackage);
            }
        }
        for (JsonNode candidate : packages) {
            if (candidate instanceof ObjectNode packageNode) {
                return new MainPackage(asText(packageNode.get("SPDXID")), packageNode);
            }
        }
        throw new IllegalArgumentException("SBOM SPDX JSON file does not contain main package");
    }

    private ObjectNode findPackageBySpdxId(ArrayNode packages, String packageSpdxId) {
        for (JsonNode packageNode : packages) {
            if (!(packageNode instanceof ObjectNode packageObject)) {
                continue;
            }
            if (packageSpdxId.equals(asText(packageObject.get("SPDXID")))) {
                return packageObject;
            }
        }
        return null;
    }

    private String resolveDescribedPackageId(ObjectNode root) {
        JsonNode relationshipsNode = root.get("relationships");
        if (!(relationshipsNode instanceof ArrayNode relationships)) {
            return null;
        }
        for (JsonNode relationshipNode : relationships) {
            if (!(relationshipNode instanceof ObjectNode relationship)) {
                continue;
            }
            String spdxElementId = asText(relationship.get("spdxElementId"));
            String relationshipType = asText(relationship.get("relationshipType"));
            if ("SPDXRef-DOCUMENT".equals(spdxElementId) && DESCRIBES.equals(relationshipType)) {
                return asText(relationship.get("relatedSpdxElement"));
            }
        }
        return null;
    }

    private String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asString();
        return StringUtils.hasText(value) ? value : null;
    }

    private void validateOptionalAbsoluteHttpUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme();
        if (!uri.isAbsolute() || scheme == null) {
            throw new IllegalArgumentException("Publication URL must be an absolute HTTP URL");
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Publication URL must use http or https.");
        }
    }

    private record MainPackage(String spdxId, ObjectNode packageNode) {
    }
}
