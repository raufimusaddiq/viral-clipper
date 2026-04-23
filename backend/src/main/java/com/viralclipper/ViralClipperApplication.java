package com.viralclipper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ViralClipperApplication {
    public static void main(String[] args) {
        SpringApplication.run(ViralClipperApplication.class, args);
    }
}
