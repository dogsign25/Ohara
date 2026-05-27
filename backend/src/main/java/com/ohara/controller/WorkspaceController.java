package com.ohara.controller;

import com.ohara.entity.Document;
import com.ohara.entity.Workspace;
import com.ohara.service.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
@CrossOrigin(origins = "http://localhost:3000")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * Authorization 헤더에서 Bearer 토큰만 잘라냅니다.
     * 워크스페이스 API는 사용자 소유권 검사가 필요하므로 토큰이 없으면 즉시 예외를 던집니다.
     */
    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new IllegalStateException("인증이 필요합니다.");
        return authHeader.substring(7);
    }

    /**
     * GET /api/workspaces
     * 로그인 사용자의 워크스페이스 목록을 반환합니다.
     * 실제 DB row가 아닌 시스템 Default 워크스페이스(id=0)를 항상 첫 항목으로 합성해 추가합니다.
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        try {
            List<Workspace> ws = workspaceService.listWorkspaces(extractToken(auth));
            List<Map<String, Object>> response = new ArrayList<>();
            response.add(defaultWorkspaceDto());
            response.addAll(ws.stream().map(this::toDto).toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * POST /api/workspaces
     * 로그인 사용자 소유의 새 워크스페이스를 생성합니다.
     */
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body) {
        try {
            Workspace ws = workspaceService.createWorkspace(
                    extractToken(auth),
                    body.get("title"),
                    body.get("description")
            );
            return ResponseEntity.ok(toDto(ws));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * DELETE /api/workspaces/{id}
     * 워크스페이스 소유권을 확인한 뒤 MySQL 워크스페이스와 하위 문서를 삭제합니다.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        try {
            workspaceService.deleteWorkspace(extractToken(auth), id);
            return ResponseEntity.ok(Map.of("message", "삭제 완료"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * PATCH /api/workspaces/{id}
     * 워크스페이스 소유권을 확인한 뒤 제목을 변경합니다.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<?> rename(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            Workspace ws = workspaceService.renameWorkspace(
                    extractToken(auth), id, body.get("title"));
            return ResponseEntity.ok(toDto(ws));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * GET /api/workspaces/{id}/documents
     * 워크스페이스 문서 목록을 조회합니다.
     * Default 워크스페이스(id=0)는 실제 문서 row를 갖지 않으므로 빈 배열을 반환합니다.
     */
    @GetMapping("/{id}/documents")
    public ResponseEntity<?> listDocs(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        try {
            if (id == 0L) {
                return ResponseEntity.ok(List.of());
            }
            List<Document> docs = workspaceService.listDocuments(extractToken(auth), id);
            return ResponseEntity.ok(docs.stream().map(this::toDocDto).toList());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * POST /api/workspaces/{id}/documents
     * URL 문서를 MySQL에 PENDING 상태로 저장하고 비동기 AI 분석을 시작합니다.
     */
    @PostMapping("/{id}/documents")
    public ResponseEntity<?> addDoc(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            Document doc = workspaceService.addUrl(
                    extractToken(auth), id, body.get("url"));
            return ResponseEntity.ok(toDocDto(doc));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * DELETE /api/workspaces/{wsId}/documents/{docId}
     * MySQL 문서와 Neo4j의 대응 Document 노드를 함께 삭제합니다.
     */
    @DeleteMapping("/{wsId}/documents/{docId}")
    public ResponseEntity<?> deleteDoc(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long wsId,
            @PathVariable Long docId) {
        try {
            workspaceService.deleteDocument(extractToken(auth), wsId, docId);
            return ResponseEntity.ok(Map.of("message", "삭제 완료"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Workspace 엔티티를 프론트엔드가 쓰는 단순 Map DTO로 변환합니다.
     */
    private Map<String, Object> toDto(Workspace ws) {
        return Map.of(
                "id",          ws.getId(),
                "title",       ws.getTitle(),
                "description", ws.getDescription() != null ? ws.getDescription() : "",
                "docCount",    ws.getDocuments().size(),
                "updatedAt",   ws.getUpdatedAt().toString()
        );
    }

    /**
     * 모든 사용자가 공유하는 시스템 워크스페이스 DTO를 만듭니다.
     * DB에 저장하지 않는 가상 워크스페이스이며, id=0으로 식별합니다.
     */
    private Map<String, Object> defaultWorkspaceDto() {
        return Map.of(
                "id",          0L,
                "title",       "Default",
                "description", "모든 사용자가 공유하는 기본 그래프",
                "docCount",    0,
                "defaultWorkspace", true,
                "updatedAt",   LocalDateTime.now().toString()
        );
    }

    /**
     * Document 엔티티를 문서 패널 표시용 DTO로 변환합니다.
     */
    private Map<String, Object> toDocDto(Document doc) {
        return Map.of(
                "id",          doc.getId(),
                "title",       doc.getTitle(),
                "type",        doc.getType().name(),
                "sourceUrl",   doc.getSourceUrl() != null ? doc.getSourceUrl() : "",
                "status",      doc.getStatus().name(),
                "entityCount", doc.getEntityCount(),
                "uploadedAt",  doc.getUploadedAt().toString()
        );
    }
}
