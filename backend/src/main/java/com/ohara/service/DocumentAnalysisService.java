package com.ohara.service;

import com.ohara.entity.Document;
import com.ohara.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Spring Boot와 Python AI Engine(FastAPI) 사이를 연결하는 서비스입니다.
 *
 * 호출 출처:
 * - WorkspaceService.addUrl()  -> analyzeUrl()
 * - WorkspaceService.addText() -> scheduleTextAnalysis() -> analyzeText()
 * - WorkspaceService.addFile() -> scheduleTextAnalysis() -> analyzeText()
 *
 * 외부 API 출처:
 * - ai-engine/api.py의 POST /analyze/url
 * - ai-engine/api.py의 POST /analyze/text
 *
 * DB 출처:
 * - DocumentRepository를 통해 documents 테이블의 status/title/entityCount를 갱신합니다.
 */
@Service
public class DocumentAnalysisService {

    /** documents 테이블 접근 Repository입니다. 파일 위치: repository/DocumentRepository.java */
    private final DocumentRepository documentRepo;

    /** FastAPI AI Engine에 HTTP POST를 보내는 Spring HTTP 클라이언트입니다. */
    private final RestTemplate restTemplate = new RestTemplate();

    /** AI Engine base URL입니다. application 설정이 없으면 http://localhost:8001을 사용합니다. */
    private final String aiUrl;

    /** 생성자 주입입니다. @Value는 application.properties의 ohara.ai.url 값을 읽습니다. */
    public DocumentAnalysisService(DocumentRepository documentRepo,
                                   @Value("${ohara.ai.url:http://localhost:8001}") String aiUrl) {
        this.documentRepo = documentRepo;
        this.aiUrl = aiUrl;
    }

    /**
     * URL 문서를 백그라운드에서 분석합니다.
     * 문서 상태를 ANALYZING으로 바꾼 뒤 Python AI Engine에 URL, workspaceId, docId를 전달합니다.
     * 최대 3회 시도하며 성공하면 제목/엔티티 수를 저장하고 DONE, 모두 실패하면 ERROR로 마칩니다.
     *
     * 호출 출처: WorkspaceService.addUrl()의 TransactionSynchronization.afterCommit()
     * 비동기 출처: OharaApplication.java의 @EnableAsync가 있어 @Async가 실제 별도 스레드에서 실행됩니다.
     */
    @Async
    @Transactional(transactionManager = "transactionManager")
    public void analyzeUrl(Long docId, Long workspaceId, String url) {
        Document doc = documentRepo.findById(docId).orElse(null);
        if (doc == null) return;

        // 프론트는 WorkspaceController.toDocDto()로 내려간 status 값을 보고 분석 진행 상태를 표시합니다.
        doc.setStatus(Document.Status.ANALYZING);
        documentRepo.save(doc);

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                // ai-engine/api.py의 AnalyzeUrlRequest 필드명에 맞춰 snake_case key를 보냅니다.
                @SuppressWarnings("unchecked")
                Map<String, Object> result = restTemplate.postForObject(
                        aiUrl + "/analyze/url",
                        Map.of("url", url, "workspace_id", workspaceId, "doc_id", docId),
                        Map.class
                );

                if (result != null) {
                    if (result.containsKey("title"))
                        doc.setTitle((String) result.get("title"));
                    if (result.containsKey("entity_count"))
                        doc.setEntityCount((Integer) result.get("entity_count"));
                }
                doc.setStatus(Document.Status.DONE);
                documentRepo.save(doc);
                return;
            } catch (Exception e) {
                if (attempt == 3) {
                    doc.setStatus(Document.Status.ERROR);
                    documentRepo.save(doc);
                }
            }
        }
    }

    /**
     * 직접 입력 텍스트 또는 파일에서 추출한 텍스트를 백그라운드에서 분석합니다.
     *
     * 호출 출처:
     * - WorkspaceService.scheduleTextAnalysis()
     *
     * 외부 API:
     * - ai-engine/api.py의 POST /analyze/text를 호출합니다.
     */
    @Async
    @Transactional(transactionManager = "transactionManager")
    public void analyzeText(Long docId, Long workspaceId, String title, String text) {
        Document doc = documentRepo.findById(docId).orElse(null);
        if (doc == null) return;

        // URL 분석과 동일하게 상태를 ANALYZING -> DONE/ERROR로 갱신합니다.
        doc.setStatus(Document.Status.ANALYZING);
        documentRepo.save(doc);

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                // title/text/workspace_id/doc_id는 ai-engine/api.py의 AnalyzeTextRequest와 맞춘 payload입니다.
                @SuppressWarnings("unchecked")
                Map<String, Object> result = restTemplate.postForObject(
                        aiUrl + "/analyze/text",
                        Map.of(
                                "title", title,
                                "text", text,
                                "workspace_id", workspaceId,
                                "doc_id", docId
                        ),
                        Map.class
                );

                if (result != null) {
                    if (result.containsKey("title"))
                        doc.setTitle((String) result.get("title"));
                    if (result.containsKey("entity_count"))
                        doc.setEntityCount((Integer) result.get("entity_count"));
                }
                doc.setStatus(Document.Status.DONE);
                documentRepo.save(doc);
                return;
            } catch (Exception e) {
                if (attempt == 3) {
                    doc.setStatus(Document.Status.ERROR);
                    documentRepo.save(doc);
                }
            }
        }
    }
}
