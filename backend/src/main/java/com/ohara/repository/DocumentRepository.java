package com.ohara.repository;

import com.ohara.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * documents 테이블 접근용 Spring Data JPA Repository입니다.
 *
 * 사용 출처:
 * - WorkspaceService는 문서 생성/조회/삭제에 사용합니다.
 * - DocumentAnalysisService는 분석 상태와 entityCount 갱신에 사용합니다.
 *
 * 엔티티 출처:
 * - Document는 entity/Document.java에 정의되어 있습니다.
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {
    /**
     * 워크스페이스 상세 패널에서 문서를 최신 업로드순으로 보여주기 위한 조회입니다.
     *
     * 호출 출처:
     * - WorkspaceService.listDocuments()
     *
     * Spring Data JPA가 메서드 이름을 해석해
     * WHERE workspace.id = ? ORDER BY uploadedAt DESC 쿼리를 자동 생성합니다.
     */
    List<Document> findByWorkspaceIdOrderByUploadedAtDesc(Long workspaceId);
}
