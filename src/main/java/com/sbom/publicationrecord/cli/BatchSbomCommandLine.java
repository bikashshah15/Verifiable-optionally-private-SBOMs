package com.sbom.publicationrecord.cli;

import com.sbom.publicationrecord.pipeline.BatchSbomRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.batch.enabled", havingValue = "true")
public class BatchSbomCommandLine implements CommandLineRunner {
    private final BatchSbomRunner runner;

    public BatchSbomCommandLine(BatchSbomRunner runner) {
        this.runner = runner;
    }

    @Override
    public void run(String... args) throws Exception {
        runner.run();
    }
}
