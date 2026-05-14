package com.sbom.publicationrecord.swid;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public class HashUtil {
    private HashUtil() {}

    // Stream a file from a disk and returns its SHA-256 digest as a lowecase string
    public static String sha256Hex(Path file){
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256 for file: " + file, e);
        }
    }

    // Computes the SHA-256 digest of a string and returns it as a lowercase string
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256 for value.", e);
        }
    }
    // Converts a byte array (hashoutput) into a zero-padded lowercase hex string
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}
