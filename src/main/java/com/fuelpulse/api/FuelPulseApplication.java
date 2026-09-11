package com.fuelpulse.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FuelPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(FuelPulseApplication.class, args);
    }
}