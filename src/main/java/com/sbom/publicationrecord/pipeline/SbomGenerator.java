package com.sbom.publicationrecord.pipeline;

import java.nio.file.Path;

public interface SbomGenerator {
    Path generate(Path repoDir, Path outputFile);
}
