package com.sbom.publicationrecord.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OneTimeSbomDownloadService {
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration TOMBSTONE_TTL = Duration.ofMinutes(15);
    private static final int TOKEN_BYTES = 32;
    private static final String PRIVATE_DOWNLOAD_MESSAGE =
            "The full SBOM was generated and hashed, but it was not published. "
                    + "You may download it once for your own records. "
                    + "This private download link expires and is deleted after use.";

    private final ConcurrentHashMap<String, DownloadTicket> tickets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenTombstone> tombstones = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Path allowedSbomOutputDir;
    private final Clock clock;
    private final Duration defaultTtl;

    @Autowired
    public OneTimeSbomDownloadService(@Value("${sbom.outputDir}") Path allowedSbomOutputDir) {
        this(allowedSbomOutputDir, Clock.systemUTC(), DEFAULT_TOKEN_TTL);
    }

    OneTimeSbomDownloadService(Path allowedSbomOutputDir, Clock clock, Duration defaultTtl) {
        this.allowedSbomOutputDir = allowedSbomOutputDir.toAbsolutePath().normalize();
        this.clock = clock;
        this.defaultTtl = defaultTtl == null ? DEFAULT_TOKEN_TTL : defaultTtl;
    }

    public DownloadRegistration createDownload(Path sbomFilePath, String originalFileName, String sha256) {
        return createDownload(sbomFilePath, originalFileName, sha256, defaultTtl);
    }

    public DownloadRegistration createDownload(Path sbomFilePath,
                                               String originalFileName,
                                               String sha256,
                                               Duration ttl) {
        Path normalizedPath = validateSbomPath(sbomFilePath);
        String downloadFileName = safeDownloadFileName(originalFileName);
        Instant expiresAt = clock.instant().plus(ttl == null ? defaultTtl : ttl);
        String token = generateToken();

        tickets.put(
                token,
                new DownloadTicket(
                        normalizedPath,
                        downloadFileName,
                        sha256,
                        expiresAt,
                        new AtomicBoolean(false)
                )
        );

        cleanupExpired();
        return new DownloadRegistration(
                "/api/sbom/private-download/" + token,
                expiresAt,
                true,
                PRIVATE_DOWNLOAD_MESSAGE
        );
    }

    public DownloadClaim consume(String token) {
        if (!StringUtils.hasText(token)) {
            throw unavailable(DownloadUnavailableReason.NOT_FOUND);
        }

        cleanupExpired();
        DownloadTicket ticket = tickets.get(token);
        if (ticket == null) {
            TokenTombstone tombstone = tombstones.get(token);
            if (tombstone != null && tombstone.removeAt().isAfter(clock.instant())) {
                throw unavailable(tombstone.reason());
            }
            throw unavailable(DownloadUnavailableReason.NOT_FOUND);
        }

        if (!ticket.expiresAt().isAfter(clock.instant())) {
            expireToken(token, ticket);
            throw unavailable(DownloadUnavailableReason.EXPIRED);
        }
        if (!ticket.used().compareAndSet(false, true)) {
            throw unavailable(DownloadUnavailableReason.USED);
        }
        if (!Files.isRegularFile(ticket.sbomFilePath())) {
            tickets.remove(token, ticket);
            tombstones.put(token, new TokenTombstone(DownloadUnavailableReason.FILE_MISSING, tombstoneRemoveAt()));
            throw unavailable(DownloadUnavailableReason.FILE_MISSING);
        }

        return new DownloadClaim(
                token,
                ticket.sbomFilePath(),
                ticket.downloadFileName(),
                ticket.sha256()
        );
    }

    public void completeDownload(DownloadClaim claim) {
        if (claim == null) {
            return;
        }
        try {
            Files.deleteIfExists(claim.sbomFilePath());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete private SBOM after download.", e);
        } finally {
            tickets.remove(claim.token());
            tombstones.put(claim.token(), new TokenTombstone(DownloadUnavailableReason.USED, tombstoneRemoveAt()));
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanupExpired() {
        Instant now = clock.instant();
        tickets.forEach((token, ticket) -> {
            if (!ticket.expiresAt().isAfter(now) && tickets.remove(token, ticket)) {
                deleteQuietly(ticket.sbomFilePath());
                tombstones.put(token, new TokenTombstone(DownloadUnavailableReason.EXPIRED, tombstoneRemoveAt()));
            }
        });
        tombstones.entrySet().removeIf(entry -> !entry.getValue().removeAt().isAfter(now));
    }

    private Path validateSbomPath(Path sbomFilePath) {
        if (sbomFilePath == null) {
            throw new IllegalArgumentException("Private SBOM path is required.");
        }
        Path normalizedPath = sbomFilePath.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(allowedSbomOutputDir)) {
            throw new IllegalArgumentException("Private SBOM path is outside the configured SBOM output directory.");
        }
        if (!Files.isRegularFile(normalizedPath)) {
            throw new IllegalArgumentException("Private SBOM file is not available.");
        }
        return normalizedPath;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String safeDownloadFileName(String originalFileName) {
        String source = StringUtils.hasText(originalFileName) ? originalFileName : "private-sbom";
        String safe = source.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!StringUtils.hasText(safe)) {
            safe = "private-sbom";
        }
        if (safe.endsWith(".spdx-json.json")) {
            return safe;
        }
        if (safe.endsWith(".json")) {
            safe = safe.substring(0, safe.length() - ".json".length());
        }
        return safe + ".spdx-json.json";
    }

    private void expireToken(String token, DownloadTicket ticket) {
        if (tickets.remove(token, ticket)) {
            deleteQuietly(ticket.sbomFilePath());
            tombstones.put(token, new TokenTombstone(DownloadUnavailableReason.EXPIRED, tombstoneRemoveAt()));
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Best-effort expiry cleanup; direct download cleanup reports deletion failures.
        }
    }

    private Instant tombstoneRemoveAt() {
        return clock.instant().plus(TOMBSTONE_TTL);
    }

    private DownloadUnavailableException unavailable(DownloadUnavailableReason reason) {
        return new DownloadUnavailableException(reason, switch (reason) {
            case EXPIRED -> "Private SBOM download link has expired.";
            case USED -> "Private SBOM download link has already been used.";
            case FILE_MISSING -> "Private SBOM file is no longer available.";
            case NOT_FOUND -> "Private SBOM download link was not found.";
        });
    }

    private record DownloadTicket(Path sbomFilePath,
                                  String downloadFileName,
                                  String sha256,
                                  Instant expiresAt,
                                  AtomicBoolean used) {
    }

    private record TokenTombstone(DownloadUnavailableReason reason, Instant removeAt) {
    }

    public record DownloadRegistration(String downloadUrl,
                                       Instant expiresAt,
                                       boolean available,
                                       String message) {
    }

    public record DownloadClaim(String token,
                                Path sbomFilePath,
                                String downloadFileName,
                                String sha256) {
    }

    public enum DownloadUnavailableReason {
        NOT_FOUND,
        EXPIRED,
        USED,
        FILE_MISSING
    }

    public static class DownloadUnavailableException extends RuntimeException {
        private final DownloadUnavailableReason reason;

        public DownloadUnavailableException(DownloadUnavailableReason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public DownloadUnavailableReason reason() {
            return reason;
        }
    }
}
