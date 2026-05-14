package com.sbom.publicationrecord.zenodo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "zenodo")
public class ZenodoProperties {
    private boolean enabled = false;
    private String baseUrl = "https://sandbox.zenodo.org";
    private String accessToken;
    private boolean autoPublish = false;
    private boolean reserveDoi = true;
    private boolean uploadRawSbom = true;
    private boolean uploadSwid = true;
    private boolean uploadCombinedSbom = true;
    private boolean uploadPublicationManifest = true;
    private String titlePrefix = "v-ops Verifiable SBOM Publication Record for";
    private String accessRight = "open";
    private String license = "cc-by-4.0";
    private Path manifestOutputDir = Path.of(System.getProperty("user.dir"), "output", "publication");
    private List<Creator> creators = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public boolean isAutoPublish() {
        return autoPublish;
    }

    public void setAutoPublish(boolean autoPublish) {
        this.autoPublish = autoPublish;
    }

    public boolean isReserveDoi() {
        return reserveDoi;
    }

    public void setReserveDoi(boolean reserveDoi) {
        this.reserveDoi = reserveDoi;
    }

    public boolean isUploadRawSbom() {
        return uploadRawSbom;
    }

    public void setUploadRawSbom(boolean uploadRawSbom) {
        this.uploadRawSbom = uploadRawSbom;
    }

    public boolean isUploadSwid() {
        return uploadSwid;
    }

    public void setUploadSwid(boolean uploadSwid) {
        this.uploadSwid = uploadSwid;
    }

    public boolean isUploadCombinedSbom() {
        return uploadCombinedSbom;
    }

    public void setUploadCombinedSbom(boolean uploadCombinedSbom) {
        this.uploadCombinedSbom = uploadCombinedSbom;
    }

    public boolean isUploadPublicationManifest() {
        return uploadPublicationManifest;
    }

    public void setUploadPublicationManifest(boolean uploadPublicationManifest) {
        this.uploadPublicationManifest = uploadPublicationManifest;
    }

    public String getTitlePrefix() {
        return titlePrefix;
    }

    public void setTitlePrefix(String titlePrefix) {
        this.titlePrefix = titlePrefix;
    }

    public String getAccessRight() {
        return accessRight;
    }

    public void setAccessRight(String accessRight) {
        this.accessRight = accessRight;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public Path getManifestOutputDir() {
        return manifestOutputDir;
    }

    public void setManifestOutputDir(Path manifestOutputDir) {
        this.manifestOutputDir = manifestOutputDir;
    }

    public List<Creator> getCreators() {
        return creators;
    }

    public void setCreators(List<Creator> creators) {
        this.creators = creators == null ? new ArrayList<>() : creators;
    }

    public String normalizedBaseUrl() {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("zenodo.baseUrl is required when Zenodo is enabled.");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public void validateIfEnabled() {
        if (!enabled) {
            return;
        }
        normalizedBaseUrl();
        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("zenodo.accessToken (or ZENODO_ACCESS_TOKEN) is required when zenodo.enabled=true.");
        }
    }

    public static class Creator {
        private String name;
        private String affiliation;
        private String orcid;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAffiliation() {
            return affiliation;
        }

        public void setAffiliation(String affiliation) {
            this.affiliation = affiliation;
        }

        public String getOrcid() {
            return orcid;
        }

        public void setOrcid(String orcid) {
            this.orcid = orcid;
        }
    }

}
