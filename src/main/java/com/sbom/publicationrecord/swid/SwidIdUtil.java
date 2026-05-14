package com.sbom.publicationrecord.swid;

public class SwidIdUtil {

    private SwidIdUtil() {}

    // Builds a stable SWID tag identifier by SHA-256 hashing the gitUrl and commitSha and prefixing with "swid:sha256:" so same repo revision always yields the same tag
    public static String deterministicTagId(String gitUrl, String commitSha) {
        return "swid:sha256:" + HashUtil.sha256Hex(gitUrl + "@" + commitSha);
    }

}
