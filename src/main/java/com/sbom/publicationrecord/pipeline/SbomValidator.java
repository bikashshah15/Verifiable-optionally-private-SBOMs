package com.sbom.publicationrecord.pipeline;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class SbomValidator {
    private final ObjectMapper om = new ObjectMapper();

    public void validateOrThrow(Path sbomFile, String expectedFormat) {
        try{
            if(!Files.exists(sbomFile) || Files.size(sbomFile) < 50){
                throw new IllegalStateException("SBOM file does not exist or is too small: " + sbomFile);
            }
            JsonNode root = om.readTree(sbomFile.toFile());

            if("spdx-json".equalsIgnoreCase(expectedFormat)){
                require(root, "spdxVersion");
                require(root, "SPDXID");
                require(root, "documentNamespace");
            }
        }catch (Exception e){
            throw new IllegalStateException("Invalid SBOM output: " + sbomFile + " (" + e.getMessage() + ")", e);
        }
    }
    private void require(JsonNode root, String field) {
        if(!root.hasNonNull(field)){
            throw new IllegalStateException("SBOM is missing required field: " + field);
        }
    }
}
