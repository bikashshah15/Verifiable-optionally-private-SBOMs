package com.sbom.publicationrecord.zenodo;

import com.sbom.publicationrecord.publication.PublicationRecord;
import com.sbom.publicationrecord.publication.ZenodoPublicationResult;

public interface DoiPublisher {
    ZenodoPublicationResult reserve(PublicationRecord publicationRecord);

    ZenodoPublicationResult publish(PublicationRecord publicationRecord, ZenodoPublicationResult reservationResult);

    String publicArtifactUrl(String zenodoRecordId, String fileName);
}
