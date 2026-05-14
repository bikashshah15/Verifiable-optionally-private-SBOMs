package com.sbom.publicationrecord.web;

import com.sbom.publicationrecord.publication.PublicationMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SbomRequest(@NotBlank(message = "Git URL is required.")
                          @Pattern(regexp = "^https://\\S+$", message = "Git URL must start with https:// and contain no spaces.")
                          String gitUrl,
                          PublicationMode publicationMode) {
    public PublicationMode effectivePublicationMode() {
        return publicationMode == null ? PublicationMode.defaultMode() : publicationMode;
    }

}
