package com.ticketbooking.performance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PerformanceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PerformanceServiceApplication.class, args);
    }
}
