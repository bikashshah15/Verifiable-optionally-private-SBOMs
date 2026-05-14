package com.sbom.publicationrecord.publication;

import org.springframework.util.StringUtils;

import java.net.URI;

public record RepositoryIdentity(String owner,
                                 String repository,
                                 String displayName,
                                 String canonicalGitUrl) {
    public static RepositoryIdentity fromGitUrl(String gitUrl) {
        String fallback = StringUtils.hasText(gitUrl) ? gitUrl.trim() : "repository";
        try {
            URI uri = URI.create(fallback);
            String path = uri.getPath();
            if (!StringUtils.hasText(path)) {
                return fallbackIdentity(fallback);
            }

            String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            String[] segments = normalizedPath.split("/");
            String repo = null;
            String owner = null;
            for (String segment : segments) {
                if (!StringUtils.hasText(segment)) {
                    continue;
                }
                if (owner == null) {
                    owner = segment;
                } else {
                    repo = segment;
                }
            }

            if (!StringUtils.hasText(repo)) {
                return fallbackIdentity(fallback);
            }

            String normalizedRepo = stripGitSuffix(repo);
            String displayName = StringUtils.hasText(owner) ? owner + "/" + normalizedRepo : normalizedRepo;
            String canonicalGitUrl = normalizeGitUrl(fallback);
            return new RepositoryIdentity(owner, normalizedRepo, displayName, canonicalGitUrl);
        } catch (Exception e) {
            return fallbackIdentity(fallback);
        }
    }

    public String stablePackageSpdxId() {
        return "SPDXRef-Package-" + sanitizeForSpdxId(displayName.replace("/", "-"));
    }

    public String supplier() {
        if (!StringUtils.hasText(owner)) {
            return "NOASSERTION";
        }
        return "Organization: " + owner;
    }

    public String pinnedDownloadLocation(String commitSha) {
        if (!StringUtils.hasText(commitSha)) {
            return canonicalGitUrl;
        }
        return "git+" + ensureDotGit(canonicalGitUrl) + "@" + commitSha.trim();
    }

    public String documentNamespace(String canonicalPublicationUrl, String commitSha) {
        String suffix = StringUtils.hasText(commitSha) ? sanitizeForFileName(commitSha) : "unknown";
        if (StringUtils.hasText(canonicalPublicationUrl)) {
            return trimTrailingSlash(canonicalPublicationUrl.trim()) + "#spdx-" + suffix;
        }
        return trimTrailingSlash(canonicalHttpUrl()) + "/tree/" + suffix + "#spdx";
    }

    public String fileStem() {
        return sanitizeForFileName(displayName.replace("/", "_"));
    }

    private String canonicalHttpUrl() {
        return stripGitSuffix(trimTrailingSlash(canonicalGitUrl));
    }

    private static RepositoryIdentity fallbackIdentity(String value) {
        String sanitized = sanitizeForFileName(value);
        return new RepositoryIdentity(null, sanitized, sanitized, "https://example.invalid/" + sanitized);
    }

    private static String normalizeGitUrl(String gitUrl) {
        String normalized = trimTrailingSlash(gitUrl.trim());
        if (!normalized.endsWith(".git")) {
            return normalized;
        }
        return normalized.substring(0, normalized.length() - 4) + ".git";
    }

    private static String stripGitSuffix(String value) {
        if (value != null && value.endsWith(".git")) {
            return value.substring(0, value.length() - 4);
        }
        return value;
    }

    private static String ensureDotGit(String value) {
        return value.endsWith(".git") ? value : value + ".git";
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String sanitizeForSpdxId(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[^A-Za-z0-9.-]", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-");
        sanitized = sanitized.replaceAll("^-+", "").replaceAll("-+$", "");
        return StringUtils.hasText(sanitized) ? sanitized : "repository";
    }

    private static String sanitizeForFileName(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_+", "").replaceAll("_+$", "");
        return StringUtils.hasText(sanitized) ? sanitized : "repository";
    }
}
