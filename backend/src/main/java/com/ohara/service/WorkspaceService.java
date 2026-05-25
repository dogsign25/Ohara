package com.ohara.service;

import com.ohara.entity.Document;
import com.ohara.entity.User;
import com.ohara.entity.Workspace;
import com.ohara.repository.DocumentRepository;
import com.ohara.repository.UserRepository;
import com.ohara.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepo;
    private final DocumentRepository  documentRepo;
    private final UserRepository      userRepo;
    private final AuthService         authService;
    private final GraphService        graphService;
    private final DocumentAnalysisService analysisService;

    public WorkspaceService(WorkspaceRepository workspaceRepo,
                            DocumentRepository documentRepo,
                            UserRepository userRepo,
                            AuthService authService,
                            GraphService graphService,
                            DocumentAnalysisService analysisService) {
        this.workspaceRepo = workspaceRepo;
        this.documentRepo  = documentRepo;
        this.userRepo      = userRepo;
        this.authService   = authService;
        this.graphService  = graphService;
        this.analysisService = analysisService;
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
    @Transactional(transactionManager = "transactionManager")
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
    @Transactional(transactionManager = "transactionManager")
    public void deleteWorkspace(String token, Long workspaceId) {
        User user = getUserByToken(token);
        Workspace ws = workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        workspaceRepo.delete(ws);
    }

    // ── 워크스페이스 이름 변경 ─────────────────────────────────────
    @Transactional(transactionManager = "transactionManager")
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
    @Transactional(transactionManager = "transactionManager")
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

        ws.setUpdatedAt(LocalDateTime.now());
        workspaceRepo.save(ws);

        Long docId = saved.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    analysisService.analyzeUrl(docId, workspaceId, url);
                }
            });
        } else {
            analysisService.analyzeUrl(docId, workspaceId, url);
        }

        return documentRepo.save(saved);
    }

    // ── 문서 삭제 ─────────────────────────────────────────────────
    @Transactional(transactionManager = "transactionManager")
    public void deleteDocument(String token, Long workspaceId, Long docId) {
        User user = getUserByToken(token);
        workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        Document doc = documentRepo.findById(docId)
                .filter(d -> d.getWorkspace().getId().equals(workspaceId))
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다."));
        graphService.deleteWorkspaceDocument(workspaceId, docId);
        documentRepo.delete(doc);
    }
}
