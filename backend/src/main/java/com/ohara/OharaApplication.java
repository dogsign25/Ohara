package com.ohara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * OHARA 백엔드 애플리케이션의 시작 클래스입니다.
 *
 * 사용 출처:
 * - Gradle bootRun 또는 IDE 실행 시 main()이 호출됩니다.
 * - @SpringBootApplication은 com.ohara 하위의 controller/service/repository/config Bean을 스캔합니다.
 * - @EnableAsync는 DocumentAnalysisService.analyzeUrl()/analyzeText()의 @Async 실행을 활성화합니다.
 */
@SpringBootApplication
@EnableAsync
public class OharaApplication {
    /**
     * Spring Boot 애플리케이션 진입점입니다.
     * @EnableAsync가 붙어 있어 DocumentAnalysisService의 @Async 분석 작업이
     * HTTP 요청 스레드와 분리되어 백그라운드에서 실행됩니다.
     */
    /**
     * Spring Boot 애플리케이션을 시작하고 컴포넌트 스캔과 자동 설정을 실행합니다.
     */
    public static void main(String[] args) {
        SpringApplication.run(OharaApplication.class, args);
    }
}
