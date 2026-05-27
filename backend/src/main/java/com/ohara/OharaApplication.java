package com.ohara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class OharaApplication {
    /**
     * Spring Boot 애플리케이션 진입점입니다.
     * @EnableAsync가 붙어 있어 DocumentAnalysisService의 @Async 분석 작업이
     * HTTP 요청 스레드와 분리되어 백그라운드에서 실행됩니다.
     */
    public static void main(String[] args) {
        SpringApplication.run(OharaApplication.class, args);
    }
}
