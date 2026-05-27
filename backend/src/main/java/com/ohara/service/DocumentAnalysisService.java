package com.ohara.service;

import com.ohara.entity.Document;
import com.ohara.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class DocumentAnalysisService {

    private final DocumentRepository documentRepo;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String aiUrl;

    public DocumentAnalysisService(DocumentRepository documentRepo,
                                   @Value("${ohara.ai.url:http://localhost:8001}") String aiUrl) {
        this.documentRepo = documentRepo;
        this.aiUrl = aiUrl;
    }

    /**
     * URL 문서를 백그라운드에서 분석합니다.
     * 문서 상태를 ANALYZING으로 바꾼 뒤 Python AI Engine에 URL, workspaceId, docId를 전달합니다.
     * 최대 3회 시도하며 성공하면 제목/엔티티 수를 저장하고 DONE, 모두 실패하면 ERROR로 마칩니다.
     */
    @Async
    @Transactional(transactionManager = "transactionManager")
    public void analyzeUrl(Long docId, Long workspaceId, String url) {
        Document doc = documentRepo.findById(docId).orElse(null);
        if (doc == null) return;

        doc.setStatus(Document.Status.ANALYZING);
        documentRepo.save(doc);

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
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
}
