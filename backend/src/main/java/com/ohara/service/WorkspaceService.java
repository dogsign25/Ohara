package com.ohara.service;

import com.ohara.entity.Document;
import com.ohara.entity.User;
import com.ohara.entity.Workspace;
import com.ohara.repository.DocumentRepository;
import com.ohara.repository.UserRepository;
import com.ohara.repository.WorkspaceRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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

    /**
     * 토큰에서 현재 사용자를 복원합니다.
     * 모든 워크스페이스 작업은 이 메서드를 통해 사용자 소유권 검사의 기준 User를 얻습니다.
     */
    private User getUserByToken(String token) {
        String username = authService.getUsernameFromToken(token);
        if (username == null) throw new IllegalStateException("인증이 필요합니다.");
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("유저를 찾을 수 없습니다."));
    }

    /**
     * 로그인 사용자의 워크스페이스 목록을 최신 수정일순으로 조회합니다.
     */
    public List<Workspace> listWorkspaces(String token) {
        User user = getUserByToken(token);
        return workspaceRepo.findByUserIdOrderByUpdatedAtDesc(user.getId());
    }

    /**
     * 새 워크스페이스를 생성합니다.
     * JPA와 Neo4j 트랜잭션 매니저가 모두 존재하므로 MySQL 작업에는 transactionManager를 명시합니다.
     */
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

    /**
     * 워크스페이스 소유권을 확인한 뒤 삭제합니다.
     * Workspace.documents에 orphanRemoval이 설정되어 있어 MySQL 문서 row도 함께 삭제됩니다.
     */
    @Transactional(transactionManager = "transactionManager")
    public void deleteWorkspace(String token, Long workspaceId) {
        User user = getUserByToken(token);
        Workspace ws = workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        workspaceRepo.delete(ws);
    }

    /**
     * 워크스페이스 제목을 변경하고 updatedAt을 현재 시각으로 갱신합니다.
     */
    @Transactional(transactionManager = "transactionManager")
    public Workspace renameWorkspace(String token, Long workspaceId, String newTitle) {
        User user = getUserByToken(token);
        Workspace ws = workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        ws.setTitle(newTitle.trim());
        ws.setUpdatedAt(LocalDateTime.now());
        return workspaceRepo.save(ws);
    }

    /**
     * 워크스페이스 소유권을 확인한 뒤 문서 목록을 업로드 최신순으로 반환합니다.
     */
    public List<Document> listDocuments(String token, Long workspaceId) {
        User user = getUserByToken(token);
        // 소유권 확인
        workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        return documentRepo.findByWorkspaceIdOrderByUploadedAtDesc(workspaceId);
    }

    /**
     * URL 문서를 추가하고 분석 작업을 예약합니다.
     * 문서 row가 커밋된 뒤 AI Engine을 호출해야 비동기 작업이 문서를 안정적으로 다시 조회할 수 있으므로
     * TransactionSynchronization.afterCommit에서 analyzeUrl을 호출합니다.
     */
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

    @Transactional(transactionManager = "transactionManager")
    public Document addText(String token, Long workspaceId, String title, String text) {
        User user = getUserByToken(token);
        Workspace ws = workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("분석할 텍스트를 입력해주세요.");
        }

        String cleanTitle = title != null && !title.isBlank() ? title.trim() : "Untitled note";
        Document doc = createPendingDocument(ws, Document.DocType.NOTE, cleanTitle, null, text.trim());
        scheduleTextAnalysis(doc.getId(), workspaceId, cleanTitle, text.trim());
        return documentRepo.save(doc);
    }

    @Transactional(transactionManager = "transactionManager")
    public Document addFile(String token, Long workspaceId, MultipartFile file) {
        User user = getUserByToken(token);
        Workspace ws = workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일을 선택해주세요.");
        }

        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded document";
            String lower = filename.toLowerCase();
            String text;
            Document.DocType type;
            if (lower.endsWith(".pdf")) {
                type = Document.DocType.PDF;
                try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
                    text = new PDFTextStripper().getText(pdf);
                }
            } else {
                type = Document.DocType.NOTE;
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
            }
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("파일에서 분석할 텍스트를 찾지 못했습니다.");
            }

            Document doc = createPendingDocument(ws, type, filename, null, text.trim());
            scheduleTextAnalysis(doc.getId(), workspaceId, filename, text.trim());
            return documentRepo.save(doc);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("파일을 읽지 못했습니다: " + e.getMessage());
        }
    }

    /**
     * 문서 소유권을 확인한 뒤 MySQL 문서와 Neo4j Document 노드를 함께 삭제합니다.
     */
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

    private Document createPendingDocument(Workspace ws, Document.DocType type, String title, String sourceUrl, String content) {
        Document doc = new Document();
        doc.setWorkspace(ws);
        doc.setType(type);
        doc.setSourceUrl(sourceUrl);
        doc.setTitle(title);
        doc.setContent(content);
        doc.setStatus(Document.Status.PENDING);
        Document saved = documentRepo.save(doc);

        ws.setUpdatedAt(LocalDateTime.now());
        workspaceRepo.save(ws);
        return saved;
    }

    private void scheduleTextAnalysis(Long docId, Long workspaceId, String title, String text) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    analysisService.analyzeText(docId, workspaceId, title, text);
                }
            });
        } else {
            analysisService.analyzeText(docId, workspaceId, title, text);
        }
    }
}
