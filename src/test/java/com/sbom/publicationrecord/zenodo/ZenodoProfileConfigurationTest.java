package com.sbom.publicationrecord.zenodo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZenodoProfileConfigurationTest {

    @Test
    void sandboxProfileLoadsSandboxBaseUrl() throws Exception {
        ZenodoProperties properties = loadZenodoProperties("sandbox");

        assertEquals("https://sandbox.zenodo.org", properties.normalizedBaseUrl());
    }

    @Test
    void sandboxProfilePreservesAutoPublishTrue() throws Exception {
        ZenodoProperties properties = loadZenodoProperties("sandbox");

        assertTrue(properties.isAutoPublish());
        assertTrue(StringUtils.hasText(properties.getAccessToken()));
    }

    @Test
    void productionProfileLoadsProductionBaseUrl() throws Exception {
        ZenodoProperties properties = loadZenodoProperties("prod");

        assertEquals("https://zenodo.org", properties.normalizedBaseUrl());
    }

    @Test
    void productionProfileDefaultsAutoPublishFalseWhenEnvironmentVariableIsUnset() throws Exception {
        ZenodoProperties properties = loadZenodoProperties("prod");

        assertFalse(properties.isAutoPublish());
    }

    private ZenodoProperties loadZenodoProperties(String profile) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        environment.setProperty("user.dir", System.getProperty("user.dir"));
        environment.setProperty("ZENODO_ACCESS_TOKEN", "test-token");
        environment.setProperty("ZENODO_SANDBOX_ACCESS_TOKEN", "test-sandbox-token");
        addYamlPropertySources(environment, "application.yml");
        addYamlPropertySources(environment, "application-" + profile + ".yml");
        return Binder.get(environment)
                .bind("zenodo", ZenodoProperties.class)
                .orElseThrow(() -> new IllegalStateException("Failed to bind zenodo properties for " + profile + " profile."));
    }

    private void addYamlPropertySources(MockEnvironment environment, String resourceName) throws IOException {
        Map<String, Object> properties = new LinkedHashMap<>();
        try (InputStream inputStream = new ClassPathResource(resourceName).getInputStream()) {
            Object yaml = new Yaml().load(inputStream);
            flatten(null, yaml, properties);
        }
        environment.getPropertySources().addFirst(new MapPropertySource(resourceName, properties));
    }

    private void flatten(String prefix, Object value, Map<String, Object> properties) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, childValue) -> {
                String childPrefix = prefix == null ? key.toString() : prefix + "." + key;
                flatten(childPrefix, childValue, properties);
            });
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                flatten(prefix + "[" + i + "]", list.get(i), properties);
            }
            return;
        }
        if (prefix != null && value != null) {
            properties.put(prefix, value);
        }
    }
}
