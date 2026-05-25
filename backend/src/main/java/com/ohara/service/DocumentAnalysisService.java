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
