package com.ohara.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자가 만든 워크스페이스입니다.
 * 각 워크스페이스는 한 사용자에게 속하고 여러 Document를 가질 수 있습니다.
 *
 * 사용 출처:
 * - WorkspaceService.createWorkspace()가 생성합니다.
 * - WorkspaceService.listWorkspaces()/renameWorkspace()/deleteWorkspace()가 조회/수정/삭제합니다.
 * - WorkspaceController.toDto()가 프론트 응답 Map으로 변환합니다.
 */
@Entity
@Table(name = "workspaces")
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    /** MySQL workspaces.id 기본키입니다. 프론트는 이 값을 workspaceId로 API에 넘깁니다. */
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    /** 워크스페이스 소유 사용자입니다. findByIdAndUserId() 소유권 검사에 쓰입니다. */
    private User user;

    @Column(nullable = false, length = 200)
    /** 워크스페이스 이름입니다. WorkspaceService.renameWorkspace()에서 수정됩니다. */
    private String title;

    @Column(length = 500)
    /** 선택 설명입니다. 생성 시 비어 있으면 빈 문자열로 저장합니다. */
    private String description;

    @Column(name = "created_at")
    /** 생성 시각입니다. */
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    /** 최근 수정 시각입니다. 문서 추가/이름 변경 시 갱신되어 목록 정렬 기준이 됩니다. */
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** 워크스페이스 내 문서 목록입니다. cascade/orphanRemoval로 워크스페이스 삭제 시 문서도 함께 삭제됩니다. */
    @OneToMany(mappedBy = "workspace", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Document> documents = new ArrayList<>();

    // getters/setters: WorkspaceService와 WorkspaceController.toDto()에서 사용합니다.
    public Long getId()                          { return id; }
    public User getUser()                        { return user; }
    public void setUser(User user)               { this.user = user; }
    public String getTitle()                     { return title; }
    public void setTitle(String title)           { this.title = title; }
    public String getDescription()               { return description; }
    public void setDescription(String desc)      { this.description = desc; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public LocalDateTime getUpdatedAt()          { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t)    { this.updatedAt = t; }
    public List<Document> getDocuments()         { return documents; }
}
