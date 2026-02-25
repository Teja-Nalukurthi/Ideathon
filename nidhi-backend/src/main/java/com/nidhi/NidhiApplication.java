package com.nidhi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NidhiApplication {
    public static void main(String[] args) {
        SpringApplication.run(NidhiApplication.class, args);
    }
}
