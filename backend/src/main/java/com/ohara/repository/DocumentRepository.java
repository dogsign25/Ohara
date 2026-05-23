package com.ohara.repository;

import com.ohara.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByWorkspaceIdOrderByUploadedAtDesc(Long workspaceId);
}
