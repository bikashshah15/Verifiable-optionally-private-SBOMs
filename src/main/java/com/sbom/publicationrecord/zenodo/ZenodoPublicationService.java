package com.sbom.publicationrecord.zenodo;

import com.sbom.publicationrecord.publication.PublicationRecord;
import com.sbom.publicationrecord.publication.ZenodoPublicationResult;
import org.springframework.stereotype.Service;

@Service
public class ZenodoPublicationService {
    private final ZenodoProperties properties;
    private final DoiPublisher doiPublisher;

    public ZenodoPublicationService(ZenodoProperties properties, DoiPublisher doiPublisher) {
        this.properties = properties;
        this.doiPublisher = doiPublisher;
    }

    public ZenodoPublicationResult reserveIfEnabled(PublicationRecord publicationRecord) {
        if (!properties.isEnabled()) {
            return ZenodoPublicationResult.disabled();
        }
        properties.validateIfEnabled();
        return doiPublisher.reserve(publicationRecord);
    }

    public ZenodoPublicationResult publishReservedIfEnabled(PublicationRecord publicationRecord,
                                                            ZenodoPublicationResult reservationResult) {
        if (!properties.isEnabled()) {
            return reservationResult == null ? ZenodoPublicationResult.disabled() : reservationResult;
        }
        properties.validateIfEnabled();
        return doiPublisher.publish(publicationRecord, reservationResult);
    }

    public String buildPublicArtifactUrl(String zenodoRecordId, String fileName) {
        if (!properties.isEnabled()) {
            return null;
        }
        return doiPublisher.publicArtifactUrl(zenodoRecordId, fileName);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }
}
