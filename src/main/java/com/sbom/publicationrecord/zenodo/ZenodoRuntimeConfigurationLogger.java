package com.sbom.publicationrecord.zenodo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ZenodoRuntimeConfigurationLogger implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(ZenodoRuntimeConfigurationLogger.class);
    private static final String PRODUCTION_BASE_URL = "https://zenodo.org";

    private final Environment environment;
    private final ZenodoProperties properties;

    public ZenodoRuntimeConfigurationLogger(Environment environment, ZenodoProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String baseUrl = properties.normalizedBaseUrl();
        logger.info(
                "Active Spring profile(s): {}; effective Spring profile(s): {}; Zenodo baseUrl: {}; Zenodo environment: {}; Zenodo autoPublish: {}",
                activeProfiles(),
                effectiveProfiles(),
                baseUrl,
                zenodoEnvironment(baseUrl),
                properties.isAutoPublish()
        );

        if (PRODUCTION_BASE_URL.equals(baseUrl) && properties.isAutoPublish()) {
            logger.warn("Production Zenodo creates real public records and real DOI metadata. zenodo.autoPublish is enabled.");
        }
    }

    private String activeProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return String.join(",", activeProfiles);
        }
        return "(none)";
    }

    private String effectiveProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return String.join(",", activeProfiles);
        }
        String[] defaultProfiles = environment.getDefaultProfiles();
        return defaultProfiles.length == 0 ? "default" : String.join(",", defaultProfiles);
    }

    private String zenodoEnvironment(String baseUrl) {
        if ("https://sandbox.zenodo.org".equals(baseUrl)) {
            return "Sandbox";
        }
        if (PRODUCTION_BASE_URL.equals(baseUrl)) {
            return "Production";
        }
        return "Custom";
    }
}
