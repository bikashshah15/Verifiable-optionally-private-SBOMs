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
@ConditionalOnProperty(name = "sbom.generator", havingValue = "syft", matchIfMissing = true)
public class SyftSbomGenerator implements SbomGenerator {
    private static final Logger log = LoggerFactory.getLogger(SyftSbomGenerator.class);
    private final String syftPath;
    private final String format;

    public SyftSbomGenerator(@Value("${sbom.syft.path}") String syftPath,
                             @Value("${sbom.format}") String format) {
        this.syftPath = syftPath;
        this.format = format;
        log.info("SBOM generator initialized: {} (generator=syft, syftPath={}, format={})",
                getClass().getSimpleName(), syftPath, format);
    }

    @Override
    public Path generate(Path repoDir, Path outputFile) {
        log.info("Generating SBOM using SYFT: repoDir={}, outputFile={}, format={}",
                repoDir, outputFile, format);

        Cmd.Result r = Cmd.run(
                List.of(syftPath, "dir:" + repoDir.toString(), "-o", format + "=" + outputFile.toString()),
                Duration.ofMinutes(10)
        );
        if(r.exitCode() != 0){
            throw new IllegalStateException(("Failed to generate SBOM via Syft for " + repoDir + ": " + r.stderr()));
        }
        return outputFile;
    }
}
