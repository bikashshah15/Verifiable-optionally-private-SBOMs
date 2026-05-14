package com.sbom.publicationrecord.zenodo;

import com.sbom.publicationrecord.publication.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.node.ObjectNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ZenodoDoiPublisher implements DoiPublisher {
    private static final Logger logger = LoggerFactory.getLogger(ZenodoDoiPublisher.class);

    private final ZenodoProperties properties;
    private final ZenodoClient client;
    private final ZenodoMetadataFactory metadataFactory;
    private final PublicationManifestBuilder manifestBuilder;

    public ZenodoDoiPublisher(ZenodoProperties properties,
                              ZenodoClient client,
                              ZenodoMetadataFactory metadataFactory,
                              PublicationManifestBuilder manifestBuilder) {
        this.properties = properties;
        this.client = client;
        this.metadataFactory = metadataFactory;
        this.manifestBuilder = manifestBuilder;
    }

    @Override
    public ZenodoPublicationResult reserve(PublicationRecord publicationRecord) {
        PublicationStage stage = PublicationStage.DOI_RESERVING;
        ZenodoPublicationResult result = new ZenodoPublicationResult(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                stage,
                PublicationStatusView.Draft,
                stage.progressMessage(),
                null,
                null,
                null
        );

        try {
            ObjectNode payload = metadataFactory.buildCreateDepositionPayload(
                    publicationRecord,
                    properties.isReserveDoi(),
                    resolveArtifactNames(publicationRecord)
            );

            logger.info("Zenodo stage={} for repository {}", stage.name(), publicationRecord.gitUrl());
            ZenodoDepositResponse draft = client.createDeposition(payload);
            String canonicalDoi = resolveCanonicalPublishedDoi(draft);
            String canonicalDoiUrl = resolveCanonicalPublishedDoiUrl(canonicalDoi, draft);
            stage = PublicationStage.DOI_RESERVED;
            return new ZenodoPublicationResult(
                    canonicalDoi,
                    canonicalDoiUrl,
                    draft.id(),
                    draft.conceptRecid(),
                    draft.htmlUrl(),
                    draft.htmlUrl(),
                    resolvePublicArtifactUrl(draft.id(), publicationRecord.publicationPlan().publishRawSbom(), publicationRecord.sbom()),
                    resolvePublicArtifactUrl(draft.id(), publicationRecord.publicationPlan().publishSwid(), publicationRecord.swid()),
                    resolvePublicArtifactUrl(draft.id(), publicationRecord.publicationPlan().publishCombinedSbom(), publicationRecord.combinedSbom()),
                    resolvePublicArtifactUrl(draft.id(), publicationRecord.publicationPlan().publishProofArtifact(), publicationRecord.proofRecord()),
                    null,
                    Instant.now(),
                    null,
                    false,
                    stage,
                    PublicationStatusView.Draft,
                    stage.progressMessage(),
                    null,
                    null,
                    null
            );
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            ZenodoPublicationResult failed = result.withFailure(stage, message);
            logger.error("Zenodo reservation failed at stage={}.", stage.name(), e);
            throw new ZenodoPublicationException(
                    "Zenodo publication failed at stage " + stage.name() + ": " + message,
                    stage,
                    failed,
                    e
            );
        }
    }

    @Override
    public ZenodoPublicationResult publish(PublicationRecord publicationRecord, ZenodoPublicationResult reservationResult) {
        if (reservationResult == null) {
            throw new IllegalArgumentException("Reservation result is required before publishing to Zenodo.");
        }

        PublicationStage stage = reservationResult.publicationStage() == null
                ? PublicationStage.DOI_RESERVED
                : reservationResult.publicationStage();
        ZenodoPublicationResult result = reservationResult.withArtifactUrls(
                resolvePublicArtifactUrl(reservationResult.zenodoRecordId(), publicationRecord.publicationPlan().publishRawSbom(), publicationRecord.sbom()),
                resolvePublicArtifactUrl(reservationResult.zenodoRecordId(), publicationRecord.publicationPlan().publishSwid(), publicationRecord.swid()),
                resolvePublicArtifactUrl(reservationResult.zenodoRecordId(), publicationRecord.publicationPlan().publishCombinedSbom(), publicationRecord.combinedSbom()),
                resolvePublicArtifactUrl(reservationResult.zenodoRecordId(), publicationRecord.publicationPlan().publishProofArtifact(), publicationRecord.proofRecord()),
                null
        );

        try {
            PublicationManifestBuilder.ManifestResult manifest = manifestBuilder.writeManifest(publicationRecord, result);
            result = result
                    .withManifest(manifest.fileName(), manifest.filePath())
                    .withArtifactUrls(
                            result.publicSbomUrl(),
                            result.publicSwidUrl(),
                            result.publicCombinedSbomUrl(),
                            result.publicProofRecordUrl(),
                            publicArtifactUrl(result.zenodoRecordId(), manifest.fileName())
                    );
            manifestBuilder.writeManifest(publicationRecord, result);

            List<UploadItem> uploadItems = resolveUploadItems(publicationRecord, manifest);
            if (!uploadItems.isEmpty()) {
                stage = PublicationStage.ARTIFACTS_UPLOADING;
                result = result.withStage(stage, PublicationStatusView.Draft);
                logger.info("Zenodo stage={} for deposition {}", stage.name(), result.zenodoRecordId());

                ZenodoDepositResponse draft = client.getDeposition(result.zenodoRecordId());
                for (UploadItem item : uploadItems) {
                    client.uploadFile(requireBucketUrl(draft), item.fileName(), item.path());
                }

                stage = PublicationStage.ARTIFACTS_UPLOADED;
                result = result.withStage(stage, PublicationStatusView.Draft);
                manifestBuilder.writeManifest(publicationRecord, result);
            }

            if (!properties.isAutoPublish()) {
                logger.info("Zenodo auto-publish disabled. Returning draft deposition {}.", result.zenodoRecordId());
                return result;
            }

            stage = PublicationStage.RECORD_PUBLISHING;
            result = result.withStage(stage, PublicationStatusView.Publishing);
            logger.info("Zenodo stage={} for deposition {}", stage.name(), result.zenodoRecordId());

            ZenodoDepositResponse published = client.publishDeposition(result.zenodoRecordId());
            stage = PublicationStage.RECORD_PUBLISHED;
            result = new ZenodoPublicationResult(
                    result.doi(),
                    result.doiUrl(),
                    firstNonBlank(published.id(), result.zenodoRecordId()),
                    firstNonBlank(published.conceptRecid(), result.zenodoConceptRecid()),
                    firstNonBlank(published.htmlUrl(), result.zenodoRecordUrl()),
                    result.zenodoDraftUrl(),
                    result.publicSbomUrl(),
                    result.publicSwidUrl(),
                    result.publicCombinedSbomUrl(),
                    result.publicProofRecordUrl(),
                    result.publicPublicationManifestUrl(),
                    result.doiReservedAt(),
                    null,
                    false,
                    stage,
                    PublicationStatusView.Publishing,
                    stage.progressMessage(),
                    result.publicationManifestFileName(),
                    result.publicationManifestPath(),
                    null
            );

            stage = PublicationStage.DOI_VERIFYING;
            result = result.withStage(stage, PublicationStatusView.Publishing);
            logger.info("Zenodo stage={} for deposition {}", stage.name(), result.zenodoRecordId());

            ZenodoDepositResponse verified = client.getDeposition(result.zenodoRecordId());
            String verifiedDoi = firstNonBlank(verified.doi(), result.doi());
            String verifiedDoiUrl = firstNonBlank(resolveDoiUrl(verified), result.doiUrl());

            if (!StringUtils.hasText(verifiedDoi)) {
                throw new IllegalStateException("DOI verification failed: Zenodo record does not expose a DOI.");
            }
            if (!StringUtils.hasText(verifiedDoiUrl)) {
                verifiedDoiUrl = "https://doi.org/" + verifiedDoi;
            }
            if (StringUtils.hasText(result.doi()) && !verifiedDoi.equals(result.doi())) {
                throw new IllegalStateException(
                        "DOI verification failed: expected " + result.doi() + " but Zenodo published " + verifiedDoi + "."
                );
            }

            stage = PublicationStage.DOI_VERIFIED;
            result = new ZenodoPublicationResult(
                    verifiedDoi,
                    verifiedDoiUrl,
                    firstNonBlank(verified.id(), result.zenodoRecordId()),
                    firstNonBlank(verified.conceptRecid(), result.zenodoConceptRecid()),
                    firstNonBlank(verified.htmlUrl(), result.zenodoRecordUrl()),
                    result.zenodoDraftUrl(),
                    result.publicSbomUrl(),
                    result.publicSwidUrl(),
                    result.publicCombinedSbomUrl(),
                    result.publicProofRecordUrl(),
                    result.publicPublicationManifestUrl(),
                    result.doiReservedAt(),
                    Instant.now(),
                    true,
                    stage,
                    PublicationStatusView.Published,
                    stage.progressMessage(),
                    result.publicationManifestFileName(),
                    result.publicationManifestPath(),
                    null
            );

            manifestBuilder.writeManifest(publicationRecord, result);
            logger.info("Zenodo DOI verified for deposition {} and DOI {}", result.zenodoRecordId(), result.doi());
            return result;
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            ZenodoPublicationResult failed = result.withFailure(stage, message);
            try {
                PublicationManifestBuilder.ManifestResult manifest = manifestBuilder.writeManifest(publicationRecord, failed);
                failed = failed
                        .withManifest(manifest.fileName(), manifest.filePath())
                        .withArtifactUrls(
                                failed.publicSbomUrl(),
                                failed.publicSwidUrl(),
                                failed.publicCombinedSbomUrl(),
                                failed.publicProofRecordUrl(),
                                publicArtifactUrl(failed.zenodoRecordId(), manifest.fileName())
                        );
            } catch (Exception ignored) {
                logger.warn("Failed to persist publication manifest for failed Zenodo run.");
            }
            logger.error("Zenodo publication failed at stage={}.", stage.name(), e);
            throw new ZenodoPublicationException(
                    "Zenodo publication failed at stage " + stage.name() + ": " + message,
                    stage,
                    failed,
                    e
            );
        }
    }

    @Override
    public String publicArtifactUrl(String zenodoRecordId, String fileName) {
        if (!StringUtils.hasText(zenodoRecordId) || !StringUtils.hasText(fileName)) {
            return null;
        }
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return properties.normalizedBaseUrl() + "/records/" + zenodoRecordId.trim() + "/files/" + encodedName;
    }

    private List<String> resolveArtifactNames(PublicationRecord publicationRecord) {
        List<String> names = new ArrayList<>();
        if (publicationRecord.publicationPlan().publishRawSbom()
                && publicationRecord.sbom() != null
                && StringUtils.hasText(publicationRecord.sbom().fileName())) {
            names.add(publicationRecord.sbom().fileName());
        }
        if (publicationRecord.publicationPlan().publishSwid()
                && publicationRecord.swid() != null
                && StringUtils.hasText(publicationRecord.swid().fileName())) {
            names.add(publicationRecord.swid().fileName());
        }
        if (publicationRecord.publicationPlan().publishCombinedSbom()
                && publicationRecord.combinedSbom() != null
                && StringUtils.hasText(publicationRecord.combinedSbom().fileName())) {
            names.add(publicationRecord.combinedSbom().fileName());
        }
        if (publicationRecord.publicationPlan().publishProofArtifact()
                && publicationRecord.proofRecord() != null
                && StringUtils.hasText(publicationRecord.proofRecord().fileName())) {
            names.add(publicationRecord.proofRecord().fileName());
        }
        if (publicationRecord.publicationPlan().publishPublicationManifest()) {
            names.add("publication-record.json");
        }
        return names;
    }

    private List<UploadItem> resolveUploadItems(PublicationRecord publicationRecord,
                                                PublicationManifestBuilder.ManifestResult manifest) {
        List<UploadItem> items = new ArrayList<>();
        ArtifactPublicationPlan plan = publicationRecord.publicationPlan();
        if (plan.publishRawSbom() && publicationRecord.sbom() != null) {
            items.add(new UploadItem(publicationRecord.sbom().fileName(), publicationRecord.sbom().filePath()));
        }
        if (plan.publishSwid() && publicationRecord.swid() != null) {
            items.add(new UploadItem(publicationRecord.swid().fileName(), publicationRecord.swid().filePath()));
        }
        if (plan.publishCombinedSbom() && publicationRecord.combinedSbom() != null) {
            items.add(new UploadItem(publicationRecord.combinedSbom().fileName(), publicationRecord.combinedSbom().filePath()));
        }
        if (plan.publishProofArtifact() && publicationRecord.proofRecord() != null) {
            items.add(new UploadItem(publicationRecord.proofRecord().fileName(), publicationRecord.proofRecord().filePath()));
        }
        if (plan.publishPublicationManifest() && manifest != null) {
            items.add(new UploadItem(manifest.fileName(), manifest.filePath()));
        }
        return items;
    }

    private String resolvePublicArtifactUrl(String zenodoRecordId,
                                            boolean publishArtifact,
                                            PublicationRecord.Artifact artifact) {
        if (!publishArtifact || artifact == null) {
            return null;
        }
        return publicArtifactUrl(zenodoRecordId, artifact.fileName());
    }

    private String resolveCanonicalPublishedDoi(ZenodoDepositResponse response) {
        if (response == null) {
            return null;
        }
        if (isSandbox() && StringUtils.hasText(response.id())) {
            return "10.5072/zenodo." + response.id().trim();
        }
        return firstNonBlank(response.doi(), null);
    }

    private String resolveCanonicalPublishedDoiUrl(String canonicalDoi, ZenodoDepositResponse response) {
        if (StringUtils.hasText(canonicalDoi)) {
            return "https://doi.org/" + canonicalDoi;
        }
        return resolveDoiUrl(response);
    }

    private String requireBucketUrl(ZenodoDepositResponse response) {
        if (!StringUtils.hasText(response.bucketUrl())) {
            throw new IllegalStateException("Zenodo deposition response does not include links.bucket.");
        }
        return response.bucketUrl();
    }

    private String resolveDoiUrl(ZenodoDepositResponse response) {
        if (response == null) {
            return null;
        }
        if (StringUtils.hasText(response.doiUrl())) {
            return response.doiUrl();
        }
        if (StringUtils.hasText(response.doi())) {
            return "https://doi.org/" + response.doi();
        }
        return null;
    }

    private boolean isSandbox() {
        return properties.normalizedBaseUrl().contains("sandbox.zenodo.org");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record UploadItem(String fileName, Path path) {
    }
}
