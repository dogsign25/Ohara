package com.ohara.model;

import java.util.List;

public class Dto {

    public record NodeDto(
        String id,
        String name,
        String type,   // Country | Organization | Person
        int    degree  // 연결 수 (노드 크기 결정)
    ) {}

    public record EdgeDto(
        String source,
        String target,
        int    strength,      // 공동 등장 횟수
        int    articleCount,
        String lastMentioned
    ) {}

    public record ArticleDto(
        String title,
        String url,
        String source,
        String publishedAt
    ) {}

    public record GraphResponse(
        List<NodeDto> nodes,
        List<EdgeDto> edges,
        int           totalNodes,
        int           totalEdges
    ) {}

    public record NodeDetailDto(
        String           name,
        String           type,
        int              degree,
        List<NodeDto>    relatedNodes,
        List<ArticleDto> recentArticles
    ) {}
}
