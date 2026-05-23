package com.ohara.repository;

import com.ohara.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    // 특정 유저의 워크스페이스 목록 (최신순)
    List<Workspace> findByUserIdOrderByUpdatedAtDesc(Long userId);
    // 소유자 확인 (다른 유저 접근 방지)
    Optional<Workspace> findByIdAndUserId(Long id, Long userId);
}
