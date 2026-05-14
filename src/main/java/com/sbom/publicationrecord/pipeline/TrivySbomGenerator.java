package com.sbom.publicationrecord.pipeline;

import com.sbom.publicationrecord.pipeline.util.Cmd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(name = "sbom.generator", havingValue = "trivy")
public class TrivySbomGenerator implements SbomGenerator {
    private static final Logger log = LoggerFactory.getLogger(TrivySbomGenerator.class);
    private final String trivyPath;
    private final String format;

    public TrivySbomGenerator(@Value("${sbom.trivy.path}") String trivyPath,
                              @Value("${sbom.format}") String format){
        this.trivyPath = trivyPath;
        this.format = format;
        log.info("SBOM generator initialized: {} (generator=trivy, trivyPath={}, format={})",
                getClass().getSimpleName(), trivyPath, format);
    }

    @Override
    public Path generate(Path repoDir, Path outputFile) {
        log.info("Generating SBOM using TRIVY: repoDir={}, outputFile={}, format={}",
                repoDir, outputFile, format);
        Cmd.Result r = Cmd.run(
                List.of(
                        trivyPath, "fs", "--format", format, "--output", outputFile.toString(), repoDir.toString()
                ),
                Duration.ofMinutes(10)
        );
        if(r.exitCode() != 0){
            throw new IllegalStateException(("Failed to generate SBOM via Trivy for " + repoDir + ": " + r.stderr()));
        }
        return outputFile;
    }
}
