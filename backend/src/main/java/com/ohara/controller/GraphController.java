package com.ohara.controller;

import com.ohara.model.GraphDto.*;
import com.ohara.service.GraphService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
@Validated
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    /**
     * GET /api/graph?limit=100&minStrength=1
     * Neo4j의 전체 엔티티 그래프를 조회합니다.
     * limit은 노드 수를 제한하고 minStrength는 약한 RELATED_TO 관계를 필터링합니다.
     */
    @GetMapping("/graph")
    public GraphResponse getGraph(
        @RequestParam(defaultValue = "100") @Min(10) @Max(500) int limit,
        @RequestParam(defaultValue = "1")   @Min(1)            int minStrength,
        @RequestParam(required = false) @Min(1) @Max(3650) Integer days
    ) {
        return graphService.getGraph(limit, minStrength, days);
    }

    /**
     * GET /api/node/{name}
     * 특정 엔티티 노드의 타입, 연결 수, 관련 노드, 최근 기사를 조회합니다.
     */
    @GetMapping("/node/{name}")
    public ResponseEntity<NodeDetailDto> getNode(@PathVariable String name) {
        return graphService.getNodeDetail(name)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/node/{name}/articles
     * 노드 상세 응답 중 recentArticles만 별도 API 형태로 제공합니다.
     */
    @GetMapping("/node/{name}/articles")
    public ResponseEntity<List<ArticleDto>> getArticles(@PathVariable String name) {
        return graphService.getNodeDetail(name)
            .map(d -> ResponseEntity.ok(d.recentArticles()))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/search?q=NATO
     * 검색창 자동완성을 위한 엔티티 이름 부분 검색입니다.
     */
    @GetMapping("/search")
    public List<NodeDto> search(
        @RequestParam String q,
        @RequestParam(defaultValue = "10") @Max(50) int limit
    ) {
        return graphService.search(q, limit);
    }

    /**
     * GET /api/graph/workspace/{workspaceId}
     * 특정 워크스페이스 문서에 언급된 엔티티 그래프를 조회합니다.
     * workspaceId가 0이면 모든 사용자가 공유하는 Default 그래프로 보고 전체 그래프를 반환합니다.
     */
    @GetMapping("/graph/workspace/{workspaceId}")
    public GraphResponse getWorkspaceGraph(
            @PathVariable Long workspaceId,
            @RequestParam(defaultValue = "100") @Min(10) @Max(500) int limit,
            @RequestParam(defaultValue = "1")   @Min(1)            int minStrength,
            @RequestParam(required = false) @Min(1) @Max(3650) Integer days
    ) {
        return graphService.getWorkspaceGraph(workspaceId, limit, minStrength, days);
    }

    @GetMapping("/path")
    public ResponseEntity<PathResponse> findPath(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "5") @Min(1) @Max(8) int maxDepth,
            @RequestParam(required = false) Long workspaceId
    ) {
        return graphService.findPath(from, to, maxDepth, workspaceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/edge/sources")
    public List<EdgeSourceDto> getEdgeSources(
            @RequestParam String source,
            @RequestParam String target,
            @RequestParam(required = false) Long workspaceId
    ) {
        return graphService.getEdgeSources(source, target, workspaceId);
    }

    @PatchMapping("/node/{name}")
    public ResponseEntity<?> updateNode(
            @PathVariable String name,
            @RequestBody EntityUpdateRequest request
    ) {
        try {
            return graphService.updateNode(name, request)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * DELETE /api/node/{name}
     * Neo4j의 엔티티 노드를 삭제합니다. DETACH DELETE를 사용하므로 연결 관계도 함께 제거됩니다.
     */
    @DeleteMapping("/node/{name}")
    public ResponseEntity<Map<String, String>> deleteNode(@PathVariable String name) {
        if (!graphService.deleteNode(name)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "삭제 완료"));
    }
}
