package com.ohara.controller;

import com.ohara.entity.Document;
import com.ohara.entity.Workspace;
import com.ohara.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 워크스페이스와 문서 관리 HTTP API 컨트롤러입니다.
 *
 * 호출 출처:
 * - frontend/src/api/workspace.js의 workspaceApi.list/create/delete/rename/listDocuments/addUrl/addText/addFile/deleteDocument
 *
 * 서비스 출처:
 * - 실제 소유권 검사, MySQL 저장, AI 분석 예약은 service/WorkspaceService.java에서 처리합니다.
 *
 * 응답 DTO 출처:
 * - 별도 record를 쓰지 않고 이 파일 하단 private 메서드 toDto(), defaultWorkspaceDto(), toDocDto()가 Map DTO를 만듭니다.
 */
@RestController
@RequestMapping("/api/workspaces")
@CrossOrigin(origins = "http://localhost:3000")
public class WorkspaceController {

    /** 워크스페이스 비즈니스 로직 서비스입니다. 파일 위치: service/WorkspaceService.java */
    private final WorkspaceService workspaceService;

    /** Spring 생성자 주입입니다. */
    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * Authorization 헤더에서 Bearer 토큰만 잘라냅니다.
     * 워크스페이스 API는 사용자 소유권 검사가 필요하므로 토큰이 없으면 즉시 예외를 던집니다.
     *
     * 호출 출처:
     * - 이 컨트롤러의 모든 인증 필요 엔드포인트가 서비스 호출 전에 사용합니다.
     *
     * 프론트 출처:
     * - frontend/src/api/workspace.js의 authHeaders()/bearerHeaders()가 Authorization: Bearer {token}을 붙입니다.
     */
    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new IllegalStateException("인증이 필요합니다.");
        return authHeader.substring(7);
    }

    /**
     * 인증 예외는 401, 입력·소유권 등 비즈니스 예외는 400 응답으로 변환합니다.
     */
    private ResponseEntity<?> errorResponse(Exception e) {
        HttpStatus status = e instanceof IllegalStateException
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
    }

    /**
     * GET /api/workspaces
     * 로그인 사용자의 워크스페이스 목록을 반환합니다.
     * 실제 DB row가 아닌 시스템 Default 워크스페이스(id=0)를 항상 첫 항목으로 합성해 추가합니다.
     *
     * 연결 흐름:
     * workspaceApi.list() -> WorkspaceController.list() -> WorkspaceService.listWorkspaces()
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
            return errorResponse(e);
        }
    }

    /**
     * POST /api/workspaces
     * 로그인 사용자 소유의 새 워크스페이스를 생성합니다.
     *
     * 요청 body:
     * - frontend/src/api/workspace.js의 create(title, description)가 {title, description}을 보냅니다.
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
            return errorResponse(e);
        }
    }

    /**
     * DELETE /api/workspaces/{id}
     * 워크스페이스 소유권을 확인한 뒤 MySQL 워크스페이스와 하위 문서를 삭제합니다.
     *
     * 서비스 출처:
     * - WorkspaceService.deleteWorkspace()가 findByIdAndUserId()로 소유권을 확인합니다.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        try {
            workspaceService.deleteWorkspace(extractToken(auth), id);
            return ResponseEntity.ok(Map.of("message", "삭제 완료"));
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * PATCH /api/workspaces/{id}
     * 워크스페이스 소유권을 확인한 뒤 제목을 변경합니다.
     *
     * 요청 body:
     * - {title: "..."} 형태이며 WorkspaceService.renameWorkspace()로 전달합니다.
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
            return errorResponse(e);
        }
    }

    /**
     * GET /api/workspaces/{id}/documents
     * 워크스페이스 문서 목록을 조회합니다.
     * Default 워크스페이스(id=0)는 실제 문서 row를 갖지 않으므로 빈 배열을 반환합니다.
     *
     * 응답 변환:
     * - Document 엔티티는 이 파일 하단 toDocDto()에서 프론트용 Map으로 변환됩니다.
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
            return errorResponse(e);
        }
    }

    /**
     * POST /api/workspaces/{id}/documents
     * URL 문서를 MySQL에 PENDING 상태로 저장하고 비동기 AI 분석을 시작합니다.
     *
     * 연결 흐름:
     * workspaceApi.addUrl() -> WorkspaceController.addDoc() -> WorkspaceService.addUrl()
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
            return errorResponse(e);
        }
    }

    /**
     * POST /api/workspaces/{id}/documents/text
     * 직접 입력한 텍스트를 문서로 저장하고 AI 텍스트 분석을 예약합니다.
     *
     * 호출 출처:
     * - frontend/src/api/workspace.js의 workspaceApi.addText()
     */
    @PostMapping("/{id}/documents/text")
    public ResponseEntity<?> addTextDoc(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            Document doc = workspaceService.addText(
                    extractToken(auth), id, body.get("title"), body.get("text"));
            return ResponseEntity.ok(toDocDto(doc));
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * POST /api/workspaces/{id}/documents/file
     * PDF/TXT/MD 파일을 multipart/form-data로 받아 문서로 저장하고 분석을 예약합니다.
     *
     * 호출 출처:
     * - frontend/src/api/workspace.js의 workspaceApi.addFile()
     *
     * 서비스 출처:
     * - WorkspaceService.addFile()이 PDFBox 또는 UTF-8 텍스트 변환을 수행합니다.
     */
    @PostMapping(value = "/{id}/documents/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addFileDoc(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            Document doc = workspaceService.addFile(extractToken(auth), id, file);
            return ResponseEntity.ok(toDocDto(doc));
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * DELETE /api/workspaces/{wsId}/documents/{docId}
     * MySQL 문서와 Neo4j의 대응 Document 노드를 함께 삭제합니다.
     *
     * 연결 흐름:
     * workspaceApi.deleteDocument() -> WorkspaceController.deleteDoc() -> WorkspaceService.deleteDocument()
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
            return errorResponse(e);
        }
    }

    /**
     * Workspace 엔티티를 프론트엔드가 쓰는 단순 Map DTO로 변환합니다.
     *
     * 메서드 위치:
     * - backend/src/main/java/com/ohara/controller/WorkspaceController.java 내부 private 메서드입니다.
     *
     * 호출 출처:
     * - list()
     * - create()
     * - rename()
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
     *
     * 호출 출처:
     * - list()가 실제 워크스페이스 목록 앞에 이 DTO를 추가합니다.
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
     *
     * 메서드 위치:
     * - backend/src/main/java/com/ohara/controller/WorkspaceController.java 내부 private 메서드입니다.
     *
     * 호출 출처:
     * - listDocs()
     * - addDoc()
     * - addTextDoc()
     * - addFileDoc()
     *
     * 엔티티 출처:
     * - Document는 entity/Document.java의 JPA 엔티티입니다.
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
