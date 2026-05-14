package com.sbom.publicationrecord.swid;

import com.sbom.publicationrecord.publication.RepositoryIdentity;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

@Service
public class SwidService {
    private static final int DEFAULT_TAG_VERSION = 1;
    private static final String DEFAULT_VERSION_SCHEME = "alphanumeric";

    private final boolean enabled;
    private final String entityName;
    private final String regId;
    private final String xmlLang;
    private final String sbomFormat;
    private final Path swidOutputDir;
    private final SwidXmlGenerator xmlGenerator;

    public SwidService(@Value("${swid.enabled:true}") boolean enabled,
                       @Value("${swid.entityName:Public SBOM Publication Record Service}") String entityName,
                       @Value("${swid.regid:example.org}") String regId,
                       @Value("${swid.lang:en}") String xmlLang,
                       @Value("${sbom.format:spdx-json}") String sbomFormat,
                       @Value("${swid.outputDir:${sbom.outputDir}}") Path swidOutputDir,
                       SwidXmlGenerator xmlGenerator) {
        this.enabled = enabled;
        this.entityName = entityName;
        this.regId = regId;
        this.xmlLang = xmlLang;
        this.sbomFormat = sbomFormat;
        this.swidOutputDir = swidOutputDir;
        this.xmlGenerator = xmlGenerator;
    }
    // If enabled it derives a deterministic SWID tagID + SWID filename and builds a SWID tag, writes the SWID XML to the output directory and returns identifier
    public SwidGenerationResult generateForSbom(String gitUrl,
                                                String commitSha,
                                                String version,
                                                String sbomFileName,
                                                String sbomSha256,
                                                Instant generatedAt) {
        if (!enabled) {
            return SwidGenerationResult.disabled();
        }

        RepositoryIdentity repositoryIdentity = RepositoryIdentity.fromGitUrl(gitUrl);
        String softwareName = repositoryIdentity.displayName();
        String tagId = SwidIdUtil.deterministicTagId(gitUrl, commitSha);
        String swidFileName = toSwidFileName(sbomFileName);

        SwidTag tag = new SwidTag(
                softwareName,
                StringUtils.hasText(version) ? version : "unknown",
                tagId,
                DEFAULT_TAG_VERSION,
                DEFAULT_VERSION_SCHEME,
                xmlLang,
                true,
                gitUrl,
                commitSha,
                sbomFileName,
                sbomSha256,
                generatedAt,
                entityName,
                regId
        );

        xmlGenerator.writeTag(tag, swidOutputDir.resolve(swidFileName));
        return new SwidGenerationResult(tagId, swidFileName);
    }
    // Converts an SBOM JSON filename to corresponding SWID filename
    private String toSwidFileName(String sbomFileName){
        String suffix = "." + sbomFormat + ".json";
        String base = sbomFileName;
        if (sbomFileName.endsWith(suffix)) {
            base = sbomFileName.substring(0, sbomFileName.length() - suffix.length());
        } else if (sbomFileName.endsWith(".json")) {
            base = sbomFileName.substring(0, sbomFileName.length() - ".json".length());
        }
        return base + ".swid.xml";
    }
}
