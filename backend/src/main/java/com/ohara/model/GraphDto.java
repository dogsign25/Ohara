package com.ohara.model;

import java.util.List;

public class GraphDto {

    /** 그래프에 표시되는 엔티티 노드입니다. */
    public record NodeDto(
        String id,
        String name,
        String type,   // Country | Organization | Person
        int    degree  // 연결 수 (노드 크기 결정)
    ) {}

    /** 그래프에 표시되는 엔티티 간 관계입니다. */
    public record EdgeDto(
        String source,
        String target,
        int    strength,      // 공동 등장 횟수
        int    articleCount,
        String lastMentioned
    ) {}

    /** 노드 상세 패널에 표시되는 기사 정보입니다. */
    public record ArticleDto(
        String title,
        String url,
        String source,
        String publishedAt
    ) {}

    /** 그래프 화면이 한 번에 소비하는 노드/엣지 묶음입니다. */
    public record GraphResponse(
        List<NodeDto> nodes,
        List<EdgeDto> edges,
        int           totalNodes,
        int           totalEdges
    ) {}

    /** 특정 노드를 클릭했을 때 오른쪽 상세 패널에 표시되는 정보입니다. */
    public record NodeDetailDto(
        String           name,
        String           type,
        int              degree,
        List<NodeDto>    relatedNodes,
        List<ArticleDto> recentArticles
    ) {}
}
