package com.ohara.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
/**
 * 워크스페이스에 추가된 문서 row입니다.
 * URL/PDF/NOTE 타입을 구분하고, AI 분석 진행 상태와 추출된 엔티티 수를 저장합니다.
 */
public class Document {

    /** 문서 입력 형식입니다. 현재 UI는 URL 추가를 중심으로 사용합니다. */
    public enum DocType { URL, PDF, NOTE }

    /** AI 분석 생명주기입니다. PENDING에서 시작해 ANALYZING 이후 DONE 또는 ERROR로 끝납니다. */
    public enum Status  { PENDING, ANALYZING, DONE, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DocType type;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    // 추출된 텍스트 (NER 분석 대상)
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    // 분석 상태 (PENDING → ANALYZING → DONE / ERROR)
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private Status status = Status.PENDING;

    @Column(name = "entity_count")
    private Integer entityCount = 0;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();

    // ── getters/setters ──────────────────────────────────────────
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
