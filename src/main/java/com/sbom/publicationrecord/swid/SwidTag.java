package com.sbom.publicationrecord.swid;

import java.time.Instant;

public record SwidTag(String softwareName,
                      String version,
                      String tagId,
                      int tagVersion,
                      String versionScheme,
                      String xmlLang,
                      boolean corpus,
                      String gitUrl,
                      String commitSha,
                      String sbomFileName,
                      String sbomHashSha256,
                      Instant generatedAt,
                      String entityName,
                      String regId) {
}
