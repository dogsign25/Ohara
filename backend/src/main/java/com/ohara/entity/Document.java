package com.ohara.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 워크스페이스에 추가된 문서 row입니다.
 * URL/PDF/NOTE 타입을 구분하고, AI 분석 진행 상태와 추출된 엔티티 수를 저장합니다.
 *
 * 사용 출처:
 * - WorkspaceService.addUrl()/addText()/addFile()이 생성합니다.
 * - DocumentAnalysisService.analyzeUrl()/analyzeText()가 status/title/entityCount를 갱신합니다.
 * - WorkspaceController.toDocDto()가 프론트 응답 Map으로 변환합니다.
 */
@Entity
@Table(name = "documents")
public class Document {

    /** 문서 입력 형식입니다. WorkspaceService.addUrl/addText/addFile에서 각각 URL/NOTE/PDF를 설정합니다. */
    public enum DocType { URL, PDF, NOTE }

    /** AI 분석 생명주기입니다. PENDING에서 시작해 DocumentAnalysisService에서 ANALYZING 이후 DONE 또는 ERROR로 끝납니다. */
    public enum Status  { PENDING, ANALYZING, DONE, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /** MySQL documents.id 기본키입니다. Neo4j Document 노드에는 docId 속성으로 함께 저장됩니다. */
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    /** 이 문서를 소유한 Workspace입니다. 소유권 검사는 WorkspaceService에서 workspaceId/userId로 수행합니다. */
    private Workspace workspace;

    @Column(nullable = false, length = 300)
    /** UI에 표시할 문서 제목입니다. URL 문서는 AI 분석 성공 후 실제 HTML title로 갱신될 수 있습니다. */
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    /** URL/PDF/NOTE 중 하나이며 DB에는 enum 이름 문자열로 저장됩니다. */
    private DocType type;

    @Column(name = "source_url", length = 2048)
    /** URL 문서의 원본 주소입니다. NOTE/PDF 문서는 null일 수 있습니다. */
    private String sourceUrl;

    /** 추출된 텍스트입니다. PDF/TXT/NOTE 분석 시 AI Engine의 NER 분석 대상이 됩니다. */
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    /** 분석 상태입니다. 프론트 문서 패널은 이 값을 보고 진행 상태를 표시합니다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private Status status = Status.PENDING;

    @Column(name = "entity_count")
    /** AI Engine이 문서에서 추출한 엔티티 수입니다. DocumentAnalysisService가 갱신합니다. */
    private Integer entityCount = 0;

    @Column(name = "uploaded_at")
    /** 업로드/추가 시각입니다. DocumentRepository.findByWorkspaceIdOrderByUploadedAtDesc() 정렬 기준입니다. */
    private LocalDateTime uploadedAt = LocalDateTime.now();

    // getters/setters: JPA 엔티티 필드 접근 및 Controller DTO 변환에서 사용됩니다.
    public Long getId()                           { return id; }
    public Workspace getWorkspace()               { return workspace; }
    public void setWorkspace(Workspace ws)        { this.workspace = ws; }
    public String getTitle()                      { return title; }
    public void setTitle(String title)            { this.title = title; }
    public DocType getType()                      { return type; }
    public void setType(DocType type)             { this.type = type; }
    public String getSourceUrl()                  { return sourceUrl; }
    public void setSourceUrl(String url)          { this.sourceUrl = url; }
    public String getContent()                    { return content; }
    public void setContent(String content)        { this.content = content; }
    public Status getStatus()                     { return status; }
    public void setStatus(Status status)          { this.status = status; }
    public Integer getEntityCount()               { return entityCount; }
    public void setEntityCount(Integer n)         { this.entityCount = n; }
    public LocalDateTime getUploadedAt()          { return uploadedAt; }
}
