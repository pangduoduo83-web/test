package com.example.ioedunew;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class IoeduNewApplication {

    public static void main(String[] args) {
        SpringApplication.run(IoeduNewApplication.class, args);
    }

}
