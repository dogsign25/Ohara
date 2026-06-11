package com.ohara.model;

import java.util.List;

/**
 * 그래프 API에서 사용하는 요청/응답 DTO 묶음입니다.
 *
 * 사용 출처:
 * - GraphController는 이 record들을 HTTP 응답/요청 타입으로 사용합니다.
 * - GraphService는 Neo4j Record를 NodeDto/EdgeDto/ArticleDto/EdgeSourceDto로 변환합니다.
 * - 프론트 frontend/src/api/client.js는 이 응답을 받아 GraphPage.jsx, ArticlePanel.jsx 등에 전달합니다.
 *
 * 구조:
 * - Java record를 사용하므로 생성자, getter 성격의 accessor(name(), edges() 등), equals/hashCode가 자동 생성됩니다.
 */
public class GraphDto {

    /**
     * 그래프에 표시되는 엔티티 노드입니다.
     *
     * 생성 출처:
     * - GraphService.toNodeDto()
     * - GraphService.findPath() 내부 map 변환
     */
    public record NodeDto(
        String id,
        String name,
        String type,   // Country | Organization | Person
        int    degree  // 연결 수 (노드 크기 결정)
    ) {}

    /**
     * 그래프에 표시되는 엔티티 간 관계입니다.
     *
     * 생성 출처:
     * - GraphService.toEdgeDto()
     * - GraphService.findPath() 내부 map 변환
     *
     * 참고:
     * - toEdgeDto는 backend/src/main/java/com/ohara/service/GraphService.java 하단 private 메서드입니다.
     */
    public record EdgeDto(
        String source,
        String target,
        int    strength,      // 공동 등장 횟수
        int    articleCount,
        String lastMentioned
    ) {}

    /**
     * 노드 상세 패널에 표시되는 기사 정보입니다.
     *
     * 생성 출처:
     * - GraphService.toArticleDto()
     */
    public record ArticleDto(
        String title,
        String url,
        String source,
        String publishedAt
    ) {}

    /**
     * 관계선을 클릭했을 때 보여줄 출처 문서/기사 정보입니다.
     *
     * 생성 출처:
     * - GraphService.toEdgeSourceDto()
     */
    public record EdgeSourceDto(
        String title,
        String url,
        String source,
        String publishedAt,
        String kind
    ) {}

    /**
     * 그래프 화면이 한 번에 소비하는 노드/엣지 묶음입니다.
     *
     * 생성 출처:
     * - GraphService.getGraph()
     * - GraphService.getWorkspaceGraph()
     */
    public record GraphResponse(
        List<NodeDto> nodes,
        List<EdgeDto> edges,
        int           totalNodes,
        int           totalEdges
    ) {}

    /**
     * 특정 노드를 클릭했을 때 오른쪽 상세 패널에 표시되는 정보입니다.
     *
     * 생성 출처:
     * - GraphService.getNodeDetail()
     */
    public record NodeDetailDto(
        String           name,
        String           type,
        int              degree,
        List<NodeDto>    relatedNodes,
        List<ArticleDto> recentArticles
    ) {}

    /**
     * 두 엔티티 사이의 최단 경로 결과입니다.
     *
     * 생성 출처:
     * - GraphService.findPath()
     */
    public record PathResponse(
        List<NodeDto> nodes,
        List<EdgeDto> edges
    ) {}

    /**
     * 엔티티 이름/타입 수정 요청입니다.
     *
     * 수신 출처:
     * - GraphController.updateNode()
     * - GraphService.updateNode()
     */
    public record EntityUpdateRequest(
        String name,
        String type
    ) {}

    /**
     * 수동 관계 연결 요청입니다.
     *
     * 수신 출처:
     * - GraphController.createEdge()
     * - GraphService.createEdge()
     */
    public record EdgeCreateRequest(
        String source,
        String target,
        Integer strength,
        Long workspaceId
    ) {}
}
