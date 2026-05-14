package com.sbom.publicationrecord.pipeline;

import com.sbom.publicationrecord.report.RunReportWriter;
import com.sbom.publicationrecord.sbom.SpdxSwidConcatenator;
import com.sbom.publicationrecord.swid.HashUtil;
import com.sbom.publicationrecord.swid.SwidGenerationResult;
import com.sbom.publicationrecord.swid.SwidService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Component
public class BatchSbomRunner {
    private static final Logger logger = LoggerFactory.getLogger(BatchSbomRunner.class);

    private final GitWorkspace git;
    private final SbomGenerator sbomGenerator;
    private final SbomValidator sbomValidator;
    private final RunReportWriter reportWriter;
    private final SwidService swidService;
    private final SpdxSwidConcatenator spdxSwidConcatenator;

    private final Path outputDir;
    private final String format;

    public BatchSbomRunner(GitWorkspace git, SbomGenerator sbomGenerator, SbomValidator sbomValidator, RunReportWriter reportWriter, SwidService swidService,
                           SpdxSwidConcatenator spdxSwidConcatenator,
                           @Value("${sbom.outputDir}") Path outputDir, @Value("${sbom.format}") String format) {
        this.git = git;
        this.sbomGenerator = sbomGenerator;
        this.sbomValidator = sbomValidator;
        this.reportWriter = reportWriter;
        this.swidService = swidService;
        this.spdxSwidConcatenator = spdxSwidConcatenator;
        this.outputDir = outputDir;
        this.format = format;
    }

    // for each repo in repos.txt clones/pulls it generates+validates SBOM, hashes it, derives commit/version , generate SWID, record results and cleans up the workplace.
    public void run() throws Exception {
        Files.createDirectories(outputDir);

        List<String> repos = loadRepos();
        for (String repo : repos) {
            Instant ts = Instant.now();
            Path repoDir = null;
            try {
                logger.info("Processing repository: {}", repo);
                // Clone or Pull Repo
                repoDir = git.prepareRepo(repo);
                logger.info("Repository prepared: {}", repoDir);

                // Generate SBOM filename
                String fileName = repoDir.getFileName()+"." + format + ".json";
                Path out = outputDir.resolve(fileName);
                logger.info("Generating SBOM for: {}", repo);

                // Generate SBOM for the repo
                sbomGenerator.generate(repoDir, out);
                logger.info("SBOM generated: {}", out);
                sbomValidator.validateOrThrow(out, format);
                logger.info("SBOM validated: {}", out);

                String sbomSha256 = HashUtil.sha256Hex(out);
                String commitSha = git.getHeadCommitSha(repoDir);
                String version = git.describeVersion(repoDir);
                Instant generatedAt = Instant.now();
                SwidGenerationResult swidResult = swidService.generateForSbom(
                        repo,
                        commitSha,
                        version,
                        fileName,
                        sbomSha256,
                        generatedAt
                );
                if (StringUtils.hasText(swidResult.swidTagId()) && StringUtils.hasText(swidResult.swidFileName())) {
                    spdxSwidConcatenator.concatenate(
                            out,
                            repo,
                            commitSha,
                            version,
                            swidResult.swidTagId(),
                            null,
                            null
                    );
                }

                reportWriter.record(
                        ts,
                        repo,
                        "OK",
                        out.toString(),
                        out.getFileName().toString(),
                        sbomSha256,
                        swidResult.swidTagId(),
                        swidResult.swidFileName()
                );
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                reportWriter.record(ts, repo, "FAILED", msg, "", "", "", "");
            } finally {
                if (repoDir != null) {
                    git.deleteRepo(repoDir);
                }
            }
        }
    }
    // loads the repos urls from resources/repos.txt

    private List<String> loadRepos() throws Exception {
        ClassPathResource r = new ClassPathResource("repos.txt");
        return Files.readAllLines(r.getFile().toPath()).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .toList();
    }

}
