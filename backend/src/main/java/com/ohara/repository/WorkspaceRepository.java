package com.ohara.repository;

import com.ohara.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * workspaces 테이블 접근용 Spring Data JPA Repository입니다.
 *
 * 사용 출처:
 * - WorkspaceService의 워크스페이스 CRUD와 문서 소유권 검사
 *
 * 엔티티 출처:
 * - Workspace는 entity/Workspace.java에 정의되어 있습니다.
 */
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    /**
     * 특정 유저의 워크스페이스 목록을 최신 수정순으로 조회합니다.
     * 호출 출처: WorkspaceService.listWorkspaces()
     */
    List<Workspace> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * 워크스페이스 접근 전에 소유자를 함께 확인해 다른 유저 접근을 막습니다.
     * 호출 출처: WorkspaceService의 delete/rename/listDocuments/addUrl/addText/addFile/deleteDocument()
     */
    Optional<Workspace> findByIdAndUserId(Long id, Long userId);
}
