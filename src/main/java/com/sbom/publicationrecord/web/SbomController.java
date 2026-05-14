package com.sbom.publicationrecord.web;

import com.sbom.publicationrecord.service.SbomPublicationException;
import com.sbom.publicationrecord.service.SbomResult;
import com.sbom.publicationrecord.service.SbomService;
import com.sbom.publicationrecord.service.OneTimeSbomDownloadService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/sbom")
public class SbomController {
    private final SbomService sbomService;
    private final OneTimeSbomDownloadService oneTimeSbomDownloadService;
    private final String format;

    public SbomController(SbomService sbomService,
                          OneTimeSbomDownloadService oneTimeSbomDownloadService,
                          @Value("${sbom.format}") String format) {
        this.sbomService = sbomService;
        this.oneTimeSbomDownloadService = oneTimeSbomDownloadService;
        this.format = format;
    }

    @PostMapping
    public ResponseEntity<SbomResponse> generate(@Valid @RequestBody SbomRequest request) throws Exception {
        try {
            SbomResult result = sbomService.generateForRepo(request.gitUrl(), request.effectivePublicationMode());
            SbomResponse response = toResponse("OK", "SBOM publication workflow completed.", result);
            return ResponseEntity.ok(response);
        } catch (SbomPublicationException ex) {
            SbomResult partial = ex.getPartialResult();
            if (partial == null) {
                throw ex;
            }
            SbomResponse response = toResponse(
                    "FAILED",
                    "SBOM generation finished, but publication failed: " + ex.getMessage(),
                    partial
            );
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
        }
    }

    @GetMapping("/private-download/{token}")
    public ResponseEntity<?> downloadPrivateSbom(@PathVariable String token) {
        OneTimeSbomDownloadService.DownloadClaim claim = null;
        try {
            claim = oneTimeSbomDownloadService.consume(token);
            OneTimeSbomDownloadService.DownloadClaim downloadClaim = claim;
            Resource resource = new InputStreamResource(new DeleteOnCloseInputStream(
                    Files.newInputStream(downloadClaim.sbomFilePath()),
                    () -> oneTimeSbomDownloadService.completeDownload(downloadClaim)
            ));

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + claim.downloadFileName() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(resource);
        } catch (IOException ex) {
            if (claim != null) {
                oneTimeSbomDownloadService.completeDownload(claim);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(new ErrorResponse("ERROR", "Private SBOM file is no longer available."));
        } catch (OneTimeSbomDownloadService.DownloadUnavailableException ex) {
            HttpStatus status = switch (ex.reason()) {
                case EXPIRED, USED -> HttpStatus.GONE;
                case NOT_FOUND, FILE_MISSING -> HttpStatus.NOT_FOUND;
            };
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(new ErrorResponse("ERROR", ex.getMessage()));
        }
    }

    private static final class DeleteOnCloseInputStream extends FilterInputStream {
        private final Runnable closeAction;
        private boolean closed;

        private DeleteOnCloseInputStream(InputStream delegate, Runnable closeAction) {
            super(delegate);
            this.closeAction = closeAction;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                super.close();
            } finally {
                closeAction.run();
            }
        }
    }

    private SbomResponse toResponse(String status, String message, SbomResult result) {
        return new SbomResponse(
                status,
                message,
                result.fileName(),
                format,
                result.generatedAt() == null ? null : result.generatedAt().toString(),
                result.publicationMode() == null ? null : result.publicationMode().name(),
                result.sbomSha256(),
                result.commitSha(),
                result.resolvedVersion(),
                result.swidTagId(),
                result.swidFileName(),
                result.combinedSbomFileName(),
                result.proofRecordFileName(),
                result.doi(),
                result.doiUrl(),
                result.zenodoRecordId(),
                result.zenodoConceptRecid(),
                result.zenodoRecordUrl(),
                result.doiReservedAt() == null ? null : result.doiReservedAt().toString(),
                result.doiRegisteredObservedAt() == null ? null : result.doiRegisteredObservedAt().toString(),
                result.publicationManifestFileName(),
                result.zenodoPublished(),
                result.zenodoDraftUrl(),
                result.publicationStage() == null ? null : result.publicationStage().name(),
                result.publicationStatus() == null ? null : result.publicationStatus().name(),
                result.progressMessage(),
                result.publicSbomUrl(),
                result.publicSwidUrl(),
                result.publicCombinedSbomUrl(),
                result.proofRecordUrl(),
                result.publicPublicationManifestUrl(),
                result.rawSbomPublished(),
                result.combinedArtifactPublished(),
                result.privateSbomDownloadUrl(),
                result.privateSbomDownloadExpiresAt() == null ? null : result.privateSbomDownloadExpiresAt().toString(),
                result.privateSbomDownloadAvailable() ? Boolean.TRUE : null,
                result.privateSbomDownloadMessage()
        );
    }


}
