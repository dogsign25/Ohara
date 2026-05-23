package com.ohara.service;

import com.ohara.entity.Document;
import com.ohara.entity.User;
import com.ohara.entity.Workspace;
import com.ohara.repository.DocumentRepository;
import com.ohara.repository.UserRepository;
import com.ohara.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepo;
    private final DocumentRepository  documentRepo;
    private final UserRepository      userRepo;
    private final AuthService         authService;
    // Python AI Engine URL (나중에 application.yml로 옮겨도 됨)
    private static final String AI_URL = "http://localhost:8001";
    private final RestTemplate restTemplate = new RestTemplate();

    public WorkspaceService(WorkspaceRepository workspaceRepo,
                            DocumentRepository documentRepo,
                            UserRepository userRepo,
                            AuthService authService) {
        this.workspaceRepo = workspaceRepo;
        this.documentRepo  = documentRepo;
        this.userRepo      = userRepo;
        this.authService   = authService;
    }

    // ── 유저 조회 헬퍼 ────────────────────────────────────────────
    private User getUserByToken(String token) {
        String username = authService.getUsernameFromToken(token);
        if (username == null) throw new IllegalStateException("인증이 필요합니다.");
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("유저를 찾을 수 없습니다."));
    }

    // ── 워크스페이스 목록 ─────────────────────────────────────────
    public List<Workspace> listWorkspaces(String token) {
        User user = getUserByToken(token);
        return workspaceRepo.findByUserIdOrderByUpdatedAtDesc(user.getId());
    }

    // ── 워크스페이스 생성 ─────────────────────────────────────────
    @Transactional
    public Workspace createWorkspace(String token, String title, String description) {
        User user = getUserByToken(token);
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("제목을 입력해주세요.");

        Workspace ws = new Workspace();
        ws.setUser(user);
        ws.setTitle(title.trim());
        ws.setDescription(description != null ? description.trim() : "");
        return workspaceRepo.save(ws);
    }

    // ── 워크스페이스 삭제 ─────────────────────────────────────────
    @Transactional
    public void deleteWorkspace(String token, Long workspaceId) {
        User user = getUserByToken(token);
        Workspace ws = workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        workspaceRepo.delete(ws);
    }

    // ── 워크스페이스 이름 변경 ─────────────────────────────────────
    @Transactional
    public Workspace renameWorkspace(String token, Long workspaceId, String newTitle) {
        User user = getUserByToken(token);
        Workspace ws = workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        ws.setTitle(newTitle.trim());
        ws.setUpdatedAt(LocalDateTime.now());
        return workspaceRepo.save(ws);
    }

    // ── 문서 목록 ─────────────────────────────────────────────────
    public List<Document> listDocuments(String token, Long workspaceId) {
        User user = getUserByToken(token);
        // 소유권 확인
        workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        return documentRepo.findByWorkspaceIdOrderByUploadedAtDesc(workspaceId);
    }

    // ── URL 문서 추가 + 분석 요청 ─────────────────────────────────
    @Transactional
    public Document addUrl(String token, Long workspaceId, String url) {
        User user = getUserByToken(token);
        Workspace ws = workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));

        // 1) Document 생성 (PENDING 상태)
        Document doc = new Document();
        doc.setWorkspace(ws);
        doc.setType(Document.DocType.URL);
        doc.setSourceUrl(url);
        doc.setTitle(url); // 일단 URL로 제목 설정, AI 분석 후 업데이트
        doc.setStatus(Document.Status.PENDING);
        Document saved = documentRepo.save(doc);

        // 2) Python AI Engine에 분석 비동기 요청
        //    지금은 간단히 동기 호출 (나중에 @Async로 교체)
        try {
            saved.setStatus(Document.Status.ANALYZING);
            documentRepo.save(saved);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    AI_URL + "/analyze/url",
                    Map.of("url", url, "workspace_id", workspaceId, "doc_id", saved.getId()),
                    Map.class
            );

            if (result != null) {
                if (result.containsKey("title"))
                    saved.setTitle((String) result.get("title"));
                if (result.containsKey("entity_count"))
                    saved.setEntityCount((Integer) result.get("entity_count"));
                saved.setStatus(Document.Status.DONE);
            }
        } catch (Exception e) {
            // AI Engine 미실행 시에도 문서는 저장됨
            saved.setStatus(Document.Status.ERROR);
        }

        ws.setUpdatedAt(LocalDateTime.now());
        workspaceRepo.save(ws);

        return documentRepo.save(saved);
    }

    // ── 문서 삭제 ─────────────────────────────────────────────────
    @Transactional
    public void deleteDocument(String token, Long workspaceId, Long docId) {
        User user = getUserByToken(token);
        workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        documentRepo.deleteById(docId);
    }
}