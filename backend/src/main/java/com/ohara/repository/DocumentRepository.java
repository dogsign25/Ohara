package com.ohara.repository;

import com.ohara.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    /**
     * 워크스페이스 상세 패널에서 문서를 최신 업로드순으로 보여주기 위한 조회입니다.
     */
    List<Document> findByWorkspaceIdOrderByUploadedAtDesc(Long workspaceId);
}
