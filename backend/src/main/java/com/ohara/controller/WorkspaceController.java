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

    // 토큰 추출 헬퍼
    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new IllegalStateException("인증이 필요합니다.");
        return authHeader.substring(7);
    }

    // ── GET /api/workspaces → 내 워크스페이스 목록
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

    // ── POST /api/workspaces → 워크스페이스 생성
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

    // ── DELETE /api/workspaces/{id} → 삭제
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

    // ── PATCH /api/workspaces/{id} → 이름 변경
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

    // ── GET /api/workspaces/{id}/documents → 문서 목록
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

    // ── POST /api/workspaces/{id}/documents → URL 추가
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

    // ── DELETE /api/workspaces/{wsId}/documents/{docId} → 문서 삭제
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

    // ── DTO 변환 ─────────────────────────────────────────────────
    private Map<String, Object> toDto(Workspace ws) {
        return Map.of(
                "id",          ws.getId(),
                "title",       ws.getTitle(),
                "description", ws.getDescription() != null ? ws.getDescription() : "",
                "docCount",    ws.getDocuments().size(),
                "updatedAt",   ws.getUpdatedAt().toString()
        );
    }

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
