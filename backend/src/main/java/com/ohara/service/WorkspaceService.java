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

/**
 * 워크스페이스와 문서 MySQL 데이터를 관리하는 서비스입니다.
 *
 * 호출 출처:
 * - WorkspaceController.list()          -> listWorkspaces()
 * - WorkspaceController.create()        -> createWorkspace()
 * - WorkspaceController.delete()        -> deleteWorkspace()
 * - WorkspaceController.rename()        -> renameWorkspace()
 * - WorkspaceController.listDocs()      -> listDocuments()
 * - WorkspaceController.addDoc()        -> addUrl()
 * - WorkspaceController.addTextDoc()    -> addText()
 * - WorkspaceController.addFileDoc()    -> addFile()
 * - WorkspaceController.deleteDoc()     -> deleteDocument()
 *
 * 협력 객체 출처:
 * - WorkspaceRepository/DocumentRepository/UserRepository는 repository 패키지의 Spring Data JPA 인터페이스입니다.
 * - AuthService는 token -> username 복원을 담당합니다.
 * - GraphService는 Neo4j Document 노드 삭제를 담당합니다.
 * - DocumentAnalysisService는 FastAPI AI Engine 비동기 분석 호출을 담당합니다.
 */
@Service
public class WorkspaceService {

    /** workspaces 테이블 접근 Repository입니다. 파일 위치: repository/WorkspaceRepository.java */
    private final WorkspaceRepository workspaceRepo;

    /** documents 테이블 접근 Repository입니다. 파일 위치: repository/DocumentRepository.java */
    private final DocumentRepository  documentRepo;

    /** users 테이블 접근 Repository입니다. 파일 위치: repository/UserRepository.java */
    private final UserRepository      userRepo;

    /** Bearer token을 username으로 복원하는 인증 서비스입니다. 파일 위치: service/AuthService.java */
    private final AuthService         authService;

    /** Neo4j 쪽 Document 노드 삭제를 맡는 그래프 서비스입니다. 파일 위치: service/GraphService.java */
    private final GraphService        graphService;

    /** URL/텍스트/PDF 분석을 AI Engine에 넘기는 비동기 서비스입니다. 파일 위치: service/DocumentAnalysisService.java */
    private final DocumentAnalysisService analysisService;

    /** Spring 생성자 주입입니다. 모든 의존성은 같은 백엔드 애플리케이션 컨텍스트의 Bean입니다. */
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
     *
     * 호출 출처:
     * - 이 클래스의 public 워크스페이스/문서 메서드 대부분이 첫 단계에서 호출합니다.
     *
     * 참조 출처:
     * - AuthService.getUsernameFromToken()은 token을 UserTokenRepository에서 조회하거나 fallbackTokenStore에서 찾습니다.
     */
    private User getUserByToken(String token) {
        String username = authService.getUsernameFromToken(token);
        if (username == null) throw new IllegalStateException("인증이 필요합니다.");
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("유저를 찾을 수 없습니다."));
    }

    /**
     * 로그인 사용자의 워크스페이스 목록을 최신 수정일순으로 조회합니다.
     *
     * 컨트롤러 출처: WorkspaceController.list()
     * Repository 출처: WorkspaceRepository.findByUserIdOrderByUpdatedAtDesc()
     */
    public List<Workspace> listWorkspaces(String token) {
        User user = getUserByToken(token);
        return workspaceRepo.findByUserIdOrderByUpdatedAtDesc(user.getId());
    }

    /**
     * 새 워크스페이스를 생성합니다.
     * JPA와 Neo4j 트랜잭션 매니저가 모두 존재하므로 MySQL 작업에는 transactionManager를 명시합니다.
     *
     * 컨트롤러 출처: WorkspaceController.create()
     * 엔티티 출처: Workspace는 entity/Workspace.java에 정의된 JPA 엔티티입니다.
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
     *
     * 컨트롤러 출처: WorkspaceController.delete()
     * 소유권 확인 출처: WorkspaceRepository.findByIdAndUserId()
     */
    @Transactional(transactionManager = "transactionManager")
    public void deleteWorkspace(String token, Long workspaceId) {
        User user = getUserByToken(token);
        Workspace ws = workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        for (Document document : ws.getDocuments()) {
            graphService.deleteWorkspaceDocument(workspaceId, document.getId());
        }
        workspaceRepo.delete(ws);
    }

    /**
     * 워크스페이스 제목을 변경하고 updatedAt을 현재 시각으로 갱신합니다.
     *
     * 컨트롤러 출처: WorkspaceController.rename()
     * updatedAt은 WorkspaceController.toDto()에서 프론트 응답 필드로 내려갑니다.
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
     *
     * 컨트롤러 출처: WorkspaceController.listDocs()
     * Repository 출처: DocumentRepository.findByWorkspaceIdOrderByUploadedAtDesc()
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
     *
     * 컨트롤러 출처: WorkspaceController.addDoc()
     * 분석 출처: DocumentAnalysisService.analyzeUrl()
     */
    @Transactional(transactionManager = "transactionManager")
    public Document addUrl(String token, Long workspaceId, String url) {
        User user = getUserByToken(token);
        Workspace ws = workspaceRepo.findByIdAndUserId(workspaceId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));

        // 1) Document 생성 (PENDING 상태). Document 엔티티는 entity/Document.java에 정의되어 있습니다.
        Document doc = new Document();
        doc.setWorkspace(ws);
        doc.setType(Document.DocType.URL);
        doc.setSourceUrl(url);
        doc.setTitle(url); // 일단 URL로 제목 설정, AI 분석 후 업데이트
        doc.setStatus(Document.Status.PENDING);
        Document saved = documentRepo.save(doc);

        ws.setUpdatedAt(LocalDateTime.now());
        workspaceRepo.save(ws);

        // 2) MySQL 트랜잭션 커밋 이후 AI 분석을 시작합니다.
        // 커밋 전 다른 @Async 스레드가 문서를 조회하면 아직 DB에 보이지 않을 수 있기 때문입니다.
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

    /**
     * 직접 입력한 텍스트 문서를 PENDING으로 저장하고 AI Engine 텍스트 분석을 예약합니다.
     *
     * 컨트롤러 출처: WorkspaceController.addTextDoc()
     * 내부 helper 출처:
     * - createPendingDocument(): 이 파일 하단 private 메서드
     * - scheduleTextAnalysis(): 이 파일 하단 private 메서드
     */
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

    /**
     * 업로드된 파일을 읽어 텍스트를 추출한 뒤 PENDING 문서로 저장하고 분석을 예약합니다.
     *
     * 컨트롤러 출처: WorkspaceController.addFileDoc()
     * PDF 처리 출처:
     * - Apache PDFBox Loader/PDFTextStripper를 사용합니다.
     * TXT/MD 처리:
     * - MultipartFile bytes를 UTF-8 문자열로 변환합니다.
     */
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
                // PDF는 PDFBox가 바이너리를 읽어 텍스트만 추출합니다.
                type = Document.DocType.PDF;
                try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
                    text = new PDFTextStripper().getText(pdf);
                }
            } else {
                // PDF가 아닌 파일은 텍스트 문서로 보고 UTF-8 문자열로 읽습니다.
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
     *
     * 컨트롤러 출처: WorkspaceController.deleteDoc()
     * Neo4j 삭제 출처: GraphService.deleteWorkspaceDocument()
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

    /**
     * URL/텍스트/파일 추가 흐름에서 공통으로 쓰는 Document 생성 helper입니다.
     *
     * 메서드 위치:
     * - backend/src/main/java/com/ohara/service/WorkspaceService.java 내부 private 메서드입니다.
     *
     * 호출 출처:
     * - addText()
     * - addFile()
     *
     * 반환 출처:
     * - Document 엔티티는 entity/Document.java에 정의되어 있고 documentRepo.save()로 MySQL에 저장됩니다.
     */
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

    /**
     * 텍스트 기반 분석을 트랜잭션 커밋 이후 실행하도록 예약합니다.
     *
     * 메서드 위치:
     * - backend/src/main/java/com/ohara/service/WorkspaceService.java 내부 private 메서드입니다.
     *
     * 호출 출처:
     * - addText()
     * - addFile()
     *
     * 실제 분석 출처:
     * - DocumentAnalysisService.analyzeText()가 FastAPI /analyze/text를 호출합니다.
     */
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
