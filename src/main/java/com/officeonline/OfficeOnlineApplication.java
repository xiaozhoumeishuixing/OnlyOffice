package com.officeonline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OfficeOnlineApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfficeOnlineApplication.class, args);
    }
}
