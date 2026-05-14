package com.sbom.publicationrecord.zenodo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Component
public class ZenodoClient {
    private static final Logger logger = LoggerFactory.getLogger(ZenodoClient.class);

    private final ZenodoProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired
    public ZenodoClient(ZenodoProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
    }

    ZenodoClient(ZenodoProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public ZenodoDepositResponse createDeposition(ObjectNode payload) {
        String url = properties.normalizedBaseUrl() + "/api/deposit/depositions";
        JsonNode responseNode = sendJson("POST", url, payload);
        return ZenodoDepositResponse.fromJson(responseNode);
    }

    public ZenodoDepositResponse publishDeposition(String depositionId) {
        String url = properties.normalizedBaseUrl() + "/api/deposit/depositions/" + depositionId + "/actions/publish";
        JsonNode responseNode = sendJson("POST", url, mapper.createObjectNode());
        return ZenodoDepositResponse.fromJson(responseNode);
    }

    public ZenodoDepositResponse getDeposition(String depositionId) {
        String url = properties.normalizedBaseUrl() + "/api/deposit/depositions/" + depositionId;
        JsonNode responseNode = send("GET", url, null, "application/json");
        return ZenodoDepositResponse.fromJson(responseNode);
    }

    public void uploadFile(String bucketUrl, String fileName, Path filePath) {
        if (!StringUtils.hasText(bucketUrl)) {
            throw new IllegalStateException("Zenodo bucket URL is missing.");
        }
        try {
            String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            String url = bucketUrl.endsWith("/") ? bucketUrl + encodedName : bucketUrl + "/" + encodedName;
            byte[] content = Files.readAllBytes(filePath);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + properties.getAccessToken().trim())
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new ZenodoClientException("Zenodo file upload failed for " + fileName, status, response.body());
            }
            logger.info("Uploaded '{}' to Zenodo bucket.", fileName);
        } catch (ZenodoClientException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload file to Zenodo: " + fileName, e);
        }
    }

    private JsonNode sendJson(String method, String url, ObjectNode payload) {
        String body;
        try {
            body = mapper.writeValueAsString(payload == null ? mapper.createObjectNode() : payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Zenodo payload.", e);
        }
        return send(method, url, body, "application/json");
    }

    private JsonNode send(String method, String url, String body, String contentType) {
        try {
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + properties.getAccessToken().trim())
                    .header("Accept", "application/json")
                    .header("Content-Type", contentType)
                    .method(method, publisher)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String responseBody = response.body();
            if (status < 200 || status >= 300) {
                throw new ZenodoClientException("Zenodo request failed: " + method + " " + url, status, responseBody);
            }
            if (!StringUtils.hasText(responseBody)) {
                return mapper.createObjectNode();
            }
            return mapper.readTree(responseBody);
        } catch (ZenodoClientException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Zenodo request failed: " + method + " " + url, e);
        }
    }

}
