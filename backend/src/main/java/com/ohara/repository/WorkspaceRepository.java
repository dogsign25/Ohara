package com.ohara.repository;

import com.ohara.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    /** 특정 유저의 워크스페이스 목록을 최신 수정순으로 조회합니다. */
    List<Workspace> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /** 워크스페이스 접근 전에 소유자를 함께 확인해 다른 유저 접근을 막습니다. */
    Optional<Workspace> findByIdAndUserId(Long id, Long userId);
}
