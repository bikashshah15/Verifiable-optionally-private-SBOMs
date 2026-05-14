package com.sbom.publicationrecord.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;
import java.time.Instant;
@Component
public class RunReportWriter {
    private static final Logger logger = LoggerFactory.getLogger(RunReportWriter.class);
    // Legacy CSV header
    private static final String HEADER_V1 = "timestamp,repo,status,details,sbomFile";
    // Cureent CSV header including SBOM hash + SWIDS output
    private static final String HEADER_V2 = "timestamp,repo,status,details,sbomFile,sbomSha256,swidTagId,swidFileName";

    private final Path outputDir;
    private final ReentrantLock lock = new ReentrantLock();
    private String activeHeader;
    public RunReportWriter(@Value("${sbom.outputDir}") Path outputDir) {
        this.outputDir = outputDir;
        logger.info("Run report output directory configured as {}", outputDir.toAbsolutePath().normalize());
    }
    //records a minimal run entry: no SBOM hash/SWID field by delegating to full record
    public void record(Instant timestamp, String repo, String status, String details, String sbomFile) {
        record(timestamp, repo, status, details, sbomFile, "", "", "");
    }

    // Ensure the report file or header exists; builds the appropriate CSV file and appends a line
    public void record(Instant timestamp, String repo, String status, String details, String sbomFile,
                       String sbomSha256, String swidTagId, String swidFileName) {
        lock.lock();
        try {
            Files.createDirectories(outputDir);
            Path report = outputDir.resolve("run-report.csv");
            String header = ensureHeader(report);
            String line = buildLine(
                    header,
                    timestamp,
                    repo,
                    status,
                    details,
                    sbomFile,
                    sbomSha256,
                    swidTagId,
                    swidFileName
            );

            Files.writeString(report, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logger.info("Run report updated: {}", report.toAbsolutePath().normalize());
        } catch (Exception e) {
            logger.error("Failed to update run-report.csv in {}", outputDir.toAbsolutePath().normalize(), e);
        } finally {
            lock.unlock();
        }
    }
    // creater or reads the first line header of the report file and defaults to V2 if missing or blank
    private String ensureHeader(Path report) throws Exception {
        if (activeHeader != null) {
            return activeHeader;
        }
        if (Files.notExists(report)) {
            Files.writeString(report, HEADER_V2 + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            activeHeader = HEADER_V2;
            return activeHeader;
        }

        try (BufferedReader reader = Files.newBufferedReader(report, StandardCharsets.UTF_8)) {
            String firstLine = reader.readLine();
            if (firstLine == null || firstLine.isBlank()) {
                Files.writeString(report, HEADER_V2 + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                activeHeader = HEADER_V2;
                return activeHeader;
            }
            activeHeader = firstLine;
            if (!HEADER_V2.equals(activeHeader)) {
                logger.warn("run-report.csv header mismatch detected. Existing header='{}'.", activeHeader);
            }
            return activeHeader;
        }
    }
    // Formats a CSV row matching either V1 or V2 header scheman and returns newline
    private String buildLine(String header, Instant timestamp, String repo, String status, String details,
                             String sbomFile, String sbomSha256, String swidTagId, String swidFileName) {
        if (HEADER_V1.equals(header)) {
            return String.join(",",
                    escape(timestamp == null ? "" : timestamp.toString()),
                    escape(repo),
                    escape(status),
                    escape(details),
                    escape(sbomFile)
            ) + System.lineSeparator();
        }
        return String.join(",",
                escape(timestamp == null ? "" : timestamp.toString()),
                escape(repo),
                escape(status),
                escape(details),
                escape(sbomFile),
                escape(sbomSha256),
                escape(swidTagId),
                escape(swidFileName)
        ) + System.lineSeparator();
    }
    // CSV escaping
    private String escape(String value) {
        if (value == null) {
            return "\"\"";
        }
        String v = value.replace("\"", "\"\"")
                .replace("\r", " ")
                .replace("\n", " ");
        return "\"" + v + "\"";
    }
}
