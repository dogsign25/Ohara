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

/**
 * 그래프 관련 HTTP API 컨트롤러입니다.
 *
 * 호출 출처:
 * - frontend/src/api/client.js의 api.getGraph(), api.getNode(), api.search(),
 *   api.getWorkspaceGraph(), api.findPath(), api.getEdgeSources(), api.createEdge(),
 *   api.updateNode(), api.deleteNode()가 이 컨트롤러의 엔드포인트를 호출합니다.
 *
 * 서비스 출처:
 * - 실제 Neo4j 조회/수정 로직은 backend/src/main/java/com/ohara/service/GraphService.java에 있습니다.
 *
 * DTO 출처:
 * - NodeDto, EdgeDto, GraphResponse 등은 backend/src/main/java/com/ohara/model/GraphDto.java의 record입니다.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
@Validated
public class GraphController {

    /** Neo4j 그래프 비즈니스 로직을 담당하는 서비스입니다. */
    private final GraphService graphService;

    /** Spring 생성자 주입입니다. GraphService Bean은 같은 백엔드 패키지의 service 계층에 있습니다. */
    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    /**
     * GET /api/graph?limit=100&minStrength=1
     * Neo4j의 전체 엔티티 그래프를 조회합니다.
     * limit은 노드 수를 제한하고 minStrength는 약한 RELATED_TO 관계를 필터링합니다.
     *
     * 연결 흐름:
     * frontend api.getGraph() -> GraphController.getGraph() -> GraphService.getGraph()
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
     * GET /api/node?name=...
     * 특정 엔티티 노드의 타입, 연결 수, 관련 노드, 최근 기사를 조회합니다.
     * 이름에 slash 같은 문자가 포함되어도 안전하도록 query parameter로 받습니다.
     *
     * 호출 출처:
     * - frontend/src/api/client.js의 api.getNode()
     */
    @GetMapping("/node")
    public ResponseEntity<NodeDetailDto> getNodeByQuery(@RequestParam String name) {
        return graphService.getNodeDetail(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/search?q=NATO
     * 검색창 자동완성을 위한 엔티티 이름 부분 검색입니다.
     *
     * limit에는 @Max(50)을 걸어 자동완성 요청이 과도하게 큰 결과를 요구하지 못하게 합니다.
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
     *
     * 실제 분기 로직은 GraphService.getWorkspaceGraph()에 있습니다.
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

    /**
     * GET /api/path?from=A&to=B&maxDepth=5&workspaceId=1
     * 두 엔티티 사이의 RELATED_TO 최단 경로를 조회합니다.
     *
     * 출처:
     * - 프론트 GraphPage.jsx의 경로 찾기 UI가 frontend/src/api/client.js의 api.findPath()를 통해 호출합니다.
     * - 결과 DTO PathResponse는 GraphDto.java에 정의되어 있습니다.
     */
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

    /**
     * GET /api/edge/sources?source=A&target=B
     * 그래프 관계선 하나가 어떤 기사/문서 때문에 만들어졌는지 출처 목록을 반환합니다.
     *
     * 전체 그래프면 Article 출처를, 워크스페이스 그래프면 Document 출처를 GraphService.getEdgeSources()가 구분합니다.
     */
    @GetMapping("/edge/sources")
    public List<EdgeSourceDto> getEdgeSources(
            @RequestParam String source,
            @RequestParam String target,
            @RequestParam(required = false) Long workspaceId
    ) {
        return graphService.getEdgeSources(source, target, workspaceId);
    }

    /**
     * POST /api/edge
     * 사용자가 직접 두 엔티티 사이 RELATED_TO 관계를 만들거나 기존 관계 강도를 증가시킵니다.
     *
     * 요청 DTO:
     * - EdgeCreateRequest는 GraphDto.java에 정의되어 있습니다.
     *
     * 에러 처리:
     * - GraphService.createEdge()가 검증 실패 시 예외를 던지면 프론트 axios가 읽을 수 있도록 {message: "..."} 형태로 내려줍니다.
     */
    @PostMapping("/edge")
    public ResponseEntity<?> createEdge(@RequestBody EdgeCreateRequest request) {
        try {
            return graphService.createEdge(request)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * PATCH /api/node?name=...
     * 엔티티의 이름 또는 타입(Country/Organization/Person)을 수정합니다.
     *
     * 호출 출처:
     * - frontend/src/api/client.js의 api.updateNode()
     */
    @PatchMapping("/node")
    public ResponseEntity<?> updateNodeByQuery(
            @RequestParam String name,
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
     * DELETE /api/node?name=...
     * Neo4j의 엔티티 노드를 삭제합니다. 이름은 query parameter로 받습니다.
     *
     * 호출 출처:
     * - frontend/src/api/client.js의 api.deleteNode()
     */
    @DeleteMapping("/node")
    public ResponseEntity<Map<String, String>> deleteNodeByQuery(@RequestParam String name) {
        if (!graphService.deleteNode(name)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "삭제 완료"));
    }
}
