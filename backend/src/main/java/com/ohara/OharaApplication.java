package com.ohara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class OharaApplication {
    public static void main(String[] args) {
        SpringApplication.run(OharaApplication.class, args);
    }
}
