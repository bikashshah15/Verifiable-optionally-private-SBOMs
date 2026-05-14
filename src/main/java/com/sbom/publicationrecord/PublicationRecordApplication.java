package com.sbom.publicationrecord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PublicationRecordApplication {

    public static void main(String[] args) {
        SpringApplication.run(PublicationRecordApplication.class, args);
    }


}
