package com.ohara.service;

import com.ohara.model.GraphDto.*;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Neo4j 그래프 데이터 전담 서비스입니다.
 *
 * 호출 출처:
 * - GraphController.getGraph()            -> getGraph()
 * - GraphController.getWorkspaceGraph()   -> getWorkspaceGraph()
 * - GraphController.getNode()             -> getNodeDetail()
 * - GraphController.search()              -> search()
 * - GraphController.findPath()            -> findPath()
 * - GraphController.getEdgeSources()      -> getEdgeSources()
 * - GraphController.createEdge()          -> createEdge()
 * - GraphController.updateNode()          -> updateNode()
 * - GraphController.deleteNode()          -> deleteNode()
 * - WorkspaceService.deleteDocument()     -> deleteWorkspaceDocument()
 *
 * 반환 DTO 출처:
 * - NodeDto, EdgeDto, GraphResponse 등은 backend/src/main/java/com/ohara/model/GraphDto.java에
 *   record로 선언되어 있고, 이 클래스의 private mapper(toNodeDto/toEdgeDto 등)가 Neo4j Record를 DTO로 바꿉니다.
 */
@Service
public class GraphService {

    /**
     * 전체 그래프에서 엔티티로 인정할 Neo4j 라벨 조건입니다.
     * Country/Organization/Person 외 Article, Document 같은 보조 노드는 그래프 중심 노드에서 제외합니다.
     */
    private static final String ENTITY_LABEL_FILTER = "e:Country OR e:Organization OR e:Person ";

    /**
     * 전체 그래프에서 degree를 계산할 때 쓰는 Cypher 조각입니다.
     * sourceKeys가 비어 있는 RELATED_TO는 출처를 추적할 수 없으므로 유효 관계 수에서 제외합니다.
     */
    private static final String VALID_REL_COUNT =
            "count(CASE WHEN size(coalesce(rel.sourceKeys, [])) > 0 THEN rel END) ";

    /**
     * 워크스페이스 그래프에서 degree를 계산할 때 쓰는 Cypher 조각입니다.
     * 특정 workspaceId가 관계의 workspaceIds 배열에 들어 있는 관계만 세어 개인 문서 그래프를 분리합니다.
     */
    private static final String WORKSPACE_REL_COUNT =
            "count(CASE WHEN $wsId IN coalesce(rel.workspaceIds, []) THEN rel END) ";

    /** Neo4j Java Driver Bean입니다. Spring 설정이 주입하며 모든 Cypher 실행은 이 driver.session()에서 시작합니다. */
    private final Driver driver;

    /**
     * 생성자 주입입니다.
     * 출처: Spring 컨테이너가 org.neo4j.driver.Driver Bean을 찾아 GraphService 생성 시 전달합니다.
     */
    public GraphService(Driver driver) {
        this.driver = driver;
    }

    /**
     * 전체 Neo4j 그래프를 조회하는 핵심 메서드입니다.
     *
     * 호출 출처:
     * - GraphController.getGraph()
     * - getWorkspaceGraph()에서 workspaceId가 0(Default)일 때도 이 메서드로 위임됩니다.
     *
     * 처리 전략:
     * 1. Country/Organization/Person 엔티티 중 유효한 RELATED_TO 관계가 많은 노드를 limit개 고릅니다.
     * 2. 선택된 노드 이름 목록을 기준으로 그 노드들 사이의 관계만 다시 조회합니다.
     * 3. minStrength, sourceKeys 존재 여부, days 시간 필터를 적용합니다.
     * 4. GraphDto.GraphResponse로 묶어 프론트 GraphPage.jsx에 반환합니다.
     *
     * 관련 private mapper:
     * - toNodeDto(): 이 파일 하단에 있으며 Neo4j Record -> GraphDto.NodeDto로 변환합니다.
     * - toEdgeDto(): 이 파일 하단에 있으며 Neo4j Record -> GraphDto.EdgeDto로 변환합니다.
     */
    public GraphResponse getGraph(int limit, int minStrength, Integer days) {
        // Neo4j Session은 쿼리 실행 단위입니다. try-with-resources로 감싸 세션 누수를 막습니다.
        try (Session s = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("limit", (long) limit);
            params.put("min", (long) minStrength);

            // days가 null이면 전체 기간, 값이 있으면 since(days) 이후의 관계만 degree/edge 계산에 포함합니다.
            String relTimeFilter = "";
            if (days != null) {
                params.put("since", since(days));
                relTimeFilter = "WHERE coalesce(rel.lastMentioned, '') >= $since ";
            }

            // 1단계: 전체 엔티티 중 연결 수(degree)가 높은 노드를 먼저 고릅니다.
            // e.type 속성을 직접 읽으므로 labels()/CASE WHEN 없이 Country/Organization/Person 타입을 내려줄 수 있습니다.
            List<NodeDto> nodes = s.run(
                    "MATCH (e) " +
                            "WHERE " + ENTITY_LABEL_FILTER +
                            "OPTIONAL MATCH (e)-[rel:RELATED_TO]-() " +
                            relTimeFilter +
                            "WITH e, " + VALID_REL_COUNT + "AS degree " +
                            "WHERE degree > 0 " +
                            "ORDER BY degree DESC " +
                            "LIMIT $limit " +
                            "RETURN e.name AS name, " +
                            "       coalesce(e.type, 'Unknown') AS type, " +
                            "       degree",
                    params
            ).list(this::toNodeDto);

            // 보여줄 노드가 없으면 엣지 조회도 의미가 없으므로 빈 GraphResponse로 조기 종료합니다.
            if (nodes.isEmpty())
                return new GraphResponse(List.of(), List.of(), 0, 0);

            // 2단계 엣지 조회에서 "선택된 노드끼리의 관계"만 남기기 위해 이름 Set을 만듭니다.
            Set<String> names = nodeNames(nodes);

            // 2단계: 앞에서 선택한 노드들 사이의 RELATED_TO 관계만 조회합니다.
            // id(a) < id(b)는 무방향 패턴 MATCH가 같은 관계를 양방향으로 중복 반환하는 것을 막습니다.
            List<EdgeDto> edges = s.run(
                    "MATCH (a)-[r:RELATED_TO]-(b) " +
                            "WHERE a.name IN $names " +
                            "  AND b.name IN $names " +
                            "  AND r.strength >= $min " +
                            "  AND size(coalesce(r.sourceKeys, [])) > 0 " +
                            timeFilter("r", days) +
                            "  AND id(a) < id(b) " +
                            "RETURN a.name AS source, " +
                            "       b.name AS target, " +
                            "       r.strength AS strength, " +
                            "       r.articleCount AS articleCount, " +
                            "       coalesce(r.lastMentioned, '') AS lastMentioned " +
                            "ORDER BY r.strength DESC",
                    withNames(params, names)
            ).list(this::toEdgeDto);

            // GraphResponse record는 GraphDto.java에 정의되어 있으며, 프론트 api.getGraph()의 최종 응답입니다.
            return new GraphResponse(nodes, edges, nodes.size(), edges.size ());
        }
    }

    /**
     * 엔티티 이름으로 상세 정보를 조회합니다.
     * 관련 노드는 RELATED_TO 강도순으로, 기사는 MENTIONED_IN 관계 기준 최신순으로 반환합니다.
     */
    public Optional<NodeDetailDto> getNodeDetail(String name) {
        try (Session s = driver.session()) {

            // 클릭된 엔티티 자체의 타입과 전체 연결 수를 먼저 가져옵니다.
            var row = s.run(
                    "MATCH (e {name: $name}) " +
                            "WHERE " + ENTITY_LABEL_FILTER +
                            "OPTIONAL MATCH (e)-[rel:RELATED_TO]-() " +
                            "WITH e, " + VALID_REL_COUNT + "AS degree " +
                            "RETURN coalesce(e.type, 'Unknown') AS type, degree",
                    Map.of("name", name));

            if (!row.hasNext()) return Optional.empty();
            var nr = row.next();

            // 오른쪽 상세 패널에 보여줄 관련 노드입니다. RELATED_TO strength가 높은 순서로 20개만 반환합니다.
            List<NodeDto> related = s.run(
                    "MATCH (e {name: $name})-[r:RELATED_TO]-(o) " +
                            "WHERE o:Country OR o:Organization OR o:Person " +
                            "  AND size(coalesce(r.sourceKeys, [])) > 0 " +
                            "OPTIONAL MATCH (o)-[other:RELATED_TO]-() " +
                            "WITH o, r, count(CASE WHEN size(coalesce(other.sourceKeys, [])) > 0 THEN other END) AS degree " +
                            "RETURN o.name AS name, " +
                            "       coalesce(o.type, 'Unknown') AS type, degree " +
                            "ORDER BY r.strength DESC LIMIT 20",
                    Map.of("name", name)
            ).list(this::toNodeDto);

            // 해당 엔티티가 언급된 Article 노드를 최신순으로 가져옵니다.
            // ArticleDto mapper(toArticleDto)는 이 파일 하단에 있고 DTO record는 GraphDto.java에 있습니다.
            List<ArticleDto> articles = s.run(
                    "MATCH (e {name: $name})-[:MENTIONED_IN]->(a:Article) " +
                            "RETURN a.title AS title, " +
                            "       a.url AS url, " +
                            "       coalesce(a.source, '') AS source, " +
                            "       coalesce(a.publishedAt, '') AS publishedAt " +
                            "ORDER BY a.publishedAt DESC LIMIT 10",
                    Map.of("name", name)
            ).list(this::toArticleDto);

            return Optional.of(new NodeDetailDto(
                    name,
                    nr.get("type").asString("Unknown"),
                    nr.get("degree").asInt(0),
                    related,
                    articles
            ));
        }
    }

    /**
     * 검색어가 포함된 엔티티를 찾아 자동완성 목록으로 반환합니다.
     * 연결 수가 많은 엔티티를 우선 보여주도록 degree 내림차순으로 정렬합니다.
     */
    public List<NodeDto> search(String query, int limit) {
        try (Session s = driver.session()) {
            // 프론트 SearchBar.jsx 자동완성에서 호출됩니다. 이름 부분 검색 후 degree가 높은 엔티티를 우선 노출합니다.
            return s.run(
                    "MATCH (e) " +
                            "WHERE (" + ENTITY_LABEL_FILTER + ") " +
                            "  AND toLower(e.name) CONTAINS toLower($q) " +
                            "OPTIONAL MATCH (e)-[rel:RELATED_TO]-() " +
                            "WITH e, " + VALID_REL_COUNT + "AS degree " +
                            "ORDER BY degree DESC LIMIT $limit " +
                            "RETURN e.name AS name, " +
                            "       coalesce(e.type, 'Unknown') AS type, degree",
                    Map.of("q", query, "limit", (long) limit)
            ).list(this::toNodeDto);
        }
    }

    /**
     * 워크스페이스 전용 그래프를 조회합니다.
     * id=0은 가상 Default 워크스페이스로 전체 그래프를 그대로 반환합니다.
     * 일반 워크스페이스는 해당 workspaceId의 문서에 언급된 엔티티만 포함합니다.
     */
    public GraphResponse getWorkspaceGraph(Long workspaceId, int limit, int minStrength, Integer days) {
        if (workspaceId != null && workspaceId == 0L) {
            // id=0은 DB에 없는 가상 Default 워크스페이스입니다. 전체 그래프 조회와 동일하게 처리합니다.
            return getGraph(limit, minStrength, days);
        }

        try (Session s = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("wsId", workspaceId);
            params.put("limit", (long) limit);
            params.put("min", (long) minStrength);
            params.put("docPrefix", workspaceId + ":");
            String relTimeFilter = "";
            if (days != null) {
                params.put("since", since(days));
                relTimeFilter = "WHERE coalesce(rel.lastMentioned, '') >= $since ";
            }

            // 1단계: 이 워크스페이스의 Document에 MENTIONED_IN_WORKSPACE로 연결된 엔티티만 노드 후보로 삼습니다.
            // WORKSPACE_REL_COUNT는 workspaceIds 배열에 현재 wsId가 들어 있는 RELATED_TO만 degree로 계산합니다.
            List<NodeDto> nodes = s.run(
                    "MATCH (e)-[:MENTIONED_IN_WORKSPACE {workspaceId: $wsId}]->(d:Document) " +
                            "WHERE " + ENTITY_LABEL_FILTER +
                            "WITH e, count(d) AS docCount " +
                            "OPTIONAL MATCH (e)-[rel:RELATED_TO]-() " +
                            relTimeFilter +
                            "WITH e, docCount, " + WORKSPACE_REL_COUNT + "AS degree " +
                            "ORDER BY degree DESC LIMIT $limit " +
                            "RETURN e.name AS name, " +
                            "       coalesce(e.type, 'Unknown') AS type, " +
                            "       degree",
                    params
            ).list(this::toNodeDto);

            if (nodes.isEmpty())
                return new GraphResponse(List.of(), List.of(), 0, 0);

            Set<String> names = nodeNames(nodes);

            // 2단계: 선택된 워크스페이스 엔티티 사이의 관계만 조회합니다.
            // workspaceDocKeys는 "workspaceId:doc:docId" 형태라 docPrefix로 현재 워크스페이스 출처 수를 계산합니다.
            List<EdgeDto> edges = s.run(
                    "MATCH (a)-[r:RELATED_TO]-(b) " +
                            "WHERE a.name IN $names AND b.name IN $names " +
                            "  AND id(a) < id(b) " +
                            "  AND $wsId IN coalesce(r.workspaceIds, []) " +
                            timeFilter("r", days) +
                            "WITH a, b, r, " +
                            "     size([docKey IN coalesce(r.workspaceDocKeys, []) " +
                            "           WHERE docKey STARTS WITH $docPrefix]) AS workspaceArticleCount " +
                            "WHERE workspaceArticleCount >= $min " +
                            "RETURN a.name AS source, b.name AS target, " +
                            "       workspaceArticleCount AS strength, " +
                            "       workspaceArticleCount AS articleCount, " +
                            "       coalesce(r.lastMentioned, '') AS lastMentioned " +
                            "ORDER BY workspaceArticleCount DESC, r.strength DESC",
                    withNames(params, names)
            ).list(this::toEdgeDto);

            return new GraphResponse(nodes, edges, nodes.size(), edges.size());
        }
    }

    /**
     * MySQL Document 삭제와 함께 Neo4j Document 노드를 제거할 때 사용합니다.
     * DETACH DELETE로 문서에 연결된 MENTIONED_IN_WORKSPACE 관계도 같이 삭제합니다.
     */
    public void deleteWorkspaceDocument(Long workspaceId, Long docId) {
        try (Session s = driver.session()) {
            String workspaceDocKey = workspaceId + ":doc:" + docId;
            String sourceKey = "workspace:" + workspaceDocKey;
            s.run(
                    "MATCH ()-[r:RELATED_TO]->() " +
                            "WHERE $docKey IN coalesce(r.workspaceDocKeys, []) " +
                            "SET r.workspaceDocKeys = [key IN coalesce(r.workspaceDocKeys, []) WHERE key <> $docKey], " +
                            "    r.sourceKeys = [key IN coalesce(r.sourceKeys, []) WHERE key <> $sourceKey], " +
                            "    r.strength = CASE WHEN coalesce(r.strength, 0) > 0 THEN r.strength - 1 ELSE 0 END, " +
                            "    r.articleCount = CASE WHEN coalesce(r.articleCount, 0) > 0 THEN r.articleCount - 1 ELSE 0 END " +
                            "WITH r " +
                            "SET r.workspaceIds = CASE " +
                            "  WHEN any(key IN coalesce(r.workspaceDocKeys, []) WHERE key STARTS WITH $workspacePrefix) " +
                            "  THEN coalesce(r.workspaceIds, []) " +
                            "  ELSE [id IN coalesce(r.workspaceIds, []) WHERE id <> $wsId] END " +
                            "WITH r WHERE size(coalesce(r.sourceKeys, [])) = 0 " +
                            "DELETE r",
                    Map.of(
                            "docKey", workspaceDocKey,
                            "sourceKey", sourceKey,
                            "workspacePrefix", workspaceId + ":",
                            "wsId", workspaceId
                    )
            );
            s.run(
                    "MATCH (d:Document {docId: $docId, workspaceId: $wsId}) " +
                            "DETACH DELETE d",
                    Map.of("docId", docId, "wsId", workspaceId)
            );
        }
    }

    /**
     * 엔티티 노드를 이름으로 삭제합니다.
     * Country/Organization/Person 라벨만 대상으로 하며, 연결 관계도 함께 제거합니다.
     */
    public boolean deleteNode(String name) {
        try (Session s = driver.session()) {
            var result = s.run(
                    "MATCH (e {name: $name}) " +
                            "WHERE e:Country OR e:Organization OR e:Person " +
                            "WITH collect(e)[0..1] AS nodes " +
                            "FOREACH (node IN nodes | DETACH DELETE node) " +
                            "RETURN size(nodes) AS deleted",
                    Map.of("name", name)
            );
            return result.hasNext() && result.next().get("deleted").asInt(0) > 0;
        }
    }

    /**
     * 두 엔티티 사이의 가장 짧은 RELATED_TO 관계 경로를 조회합니다.
     * Neo4j의 {@code shortestPath()}를 사용하며, 경로 길이는 {@code maxDepth} 이하로 제한합니다.
     * 워크스페이스가 지정되면 해당 워크스페이스에서 만들어진 관계만 경로 탐색에 사용합니다.
     *
     * @param from 출발 엔티티 이름
     * @param to 도착 엔티티 이름
     * @param maxDepth 탐색을 허용할 최대 관계 단계 수
     * @param workspaceId 워크스페이스 ID. null 또는 0이면 전체 그래프를 탐색합니다.
     * @return 경로를 구성하는 노드와 관계. 경로가 없으면 {@link Optional#empty()}
     */
    public Optional<PathResponse> findPath(String from, String to, int maxDepth, Long workspaceId) {
        try (Session s = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("from", from);
            params.put("to", to);
            params.put("maxDepth", (long) maxDepth);
            String workspaceWhere = "";
            if (workspaceId != null && workspaceId != 0L) {
                params.put("wsId", workspaceId);
                // 워크스페이스 그래프에서는 경로를 구성하는 모든 관계가 해당 workspaceId를 포함해야 합니다.
                workspaceWhere = "AND all(rel IN relationships(p) WHERE $wsId IN coalesce(rel.workspaceIds, [])) ";
            }

            // Neo4j shortestPath()로 RELATED_TO 관계 기반 최단 경로를 찾습니다.
            // Cypher는 가변 길이 *..8로 탐색하고, 서비스 파라미터 maxDepth로 실제 허용 길이를 한 번 더 제한합니다.
            var result = s.run(
                    "MATCH (start {name: $from}), (end {name: $to}) " +
                            "WHERE (start:Country OR start:Organization OR start:Person) " +
                            "  AND (end:Country OR end:Organization OR end:Person) " +
                            "MATCH p = shortestPath((start)-[:RELATED_TO*..8]-(end)) " +
                            "WHERE length(p) <= $maxDepth " +
                            "  AND all(rel IN relationships(p) WHERE size(coalesce(rel.sourceKeys, [])) > 0) " +
                            workspaceWhere +
                            "RETURN [n IN nodes(p) | {name: n.name, type: coalesce(n.type, 'Unknown')}] AS nodes, " +
                            "       [r IN relationships(p) | {source: startNode(r).name, target: endNode(r).name, " +
                            "                              strength: coalesce(r.strength, 0), articleCount: coalesce(r.articleCount, 0), " +
                            "                              lastMentioned: coalesce(r.lastMentioned, '')}] AS edges " +
                            "LIMIT 1",
                    params
            );

            if (!result.hasNext()) return Optional.empty();
            var row = result.next();

            // shortestPath 결과는 Cypher map 리스트로 반환되므로 여기서는 toNodeDto를 쓰지 않고 직접 NodeDto를 만듭니다.
            List<NodeDto> nodes = row.get("nodes").asList(v -> {
                var m = v.asMap();
                String nodeName = String.valueOf(m.getOrDefault("name", ""));
                return new NodeDto(nodeName, nodeName, String.valueOf(m.getOrDefault("type", "Unknown")), 0);
            });

            // 경로용 EdgeDto도 Cypher map 리스트에서 직접 생성합니다. DTO record 자체는 GraphDto.java에 있습니다.
            List<EdgeDto> edges = row.get("edges").asList(v -> {
                var m = v.asMap();
                return new EdgeDto(
                        String.valueOf(m.getOrDefault("source", "")),
                        String.valueOf(m.getOrDefault("target", "")),
                        ((Number) m.getOrDefault("strength", 0)).intValue(),
                        ((Number) m.getOrDefault("articleCount", 0)).intValue(),
                        String.valueOf(m.getOrDefault("lastMentioned", ""))
                );
            });
            return Optional.of(new PathResponse(nodes, edges));
        }
    }

    /**
     * 두 엔티티의 RELATED_TO 관계가 생성된 근거 문서 목록을 조회합니다.
     * 전체 그래프에서는 Article 출처를 반환하고, 워크스페이스 그래프에서는
     * 해당 워크스페이스의 Document 출처를 반환합니다.
     *
     * @param source 관계의 시작 엔티티 이름
     * @param target 관계의 도착 엔티티 이름
     * @param workspaceId 워크스페이스 ID. null 또는 0이면 전체 기사 출처를 조회합니다.
     * @return 관계 생성에 기여한 기사 또는 워크스페이스 문서 목록
     */
    public List<EdgeSourceDto> getEdgeSources(String source, String target, Long workspaceId) {
        try (Session s = driver.session()) {
            if (workspaceId != null && workspaceId != 0L) {
                // 워크스페이스 그래프에서는 관계의 workspaceDocKeys를 Document 노드와 매칭해 출처 문서를 찾습니다.
                return s.run(
                        "MATCH (a {name: $source})-[r:RELATED_TO]-(b {name: $target}) " +
                                "MATCH (d:Document {workspaceId: $wsId}) " +
                                "WHERE any(key IN coalesce(r.workspaceDocKeys, []) " +
                                "          WHERE key = $wsIdText + ':doc:' + toString(d.docId)) " +
                                "RETURN coalesce(d.title, d.url, '') AS title, coalesce(d.url, '') AS url, " +
                                "       'workspace' AS source, coalesce(d.createdAt, '') AS publishedAt, 'Document' AS kind " +
                                "ORDER BY publishedAt DESC LIMIT 20",
                        Map.of("source", source, "target", target, "wsId", workspaceId, "wsIdText", String.valueOf(workspaceId))
                ).list(this::toEdgeSourceDto);
            }

            // 전체 그래프에서는 sourceKeys의 article:url 값을 Article 노드의 url과 매칭해 출처 기사를 찾습니다.
            return s.run(
                    "MATCH (a {name: $source})-[r:RELATED_TO]-(b {name: $target}) " +
                            "MATCH (article:Article) " +
                            "WHERE ('article:' + article.url) IN coalesce(r.sourceKeys, []) " +
                            "RETURN coalesce(article.title, article.url, '') AS title, article.url AS url, " +
                            "       coalesce(article.source, '') AS source, coalesce(article.publishedAt, '') AS publishedAt, 'Article' AS kind " +
                            "ORDER BY publishedAt DESC LIMIT 20",
                    Map.of("source", source, "target", target)
            ).list(this::toEdgeSourceDto);
        }
    }

    /**
     * 기존 엔티티 두 개 사이에 사용자가 지정한 RELATED_TO 관계를 생성합니다.
     * 관계가 이미 존재하면 새 관계를 중복 생성하지 않고 강도와 기사 수를 누적합니다.
     * 수동 관계에도 고유한 sourceKey를 기록해 출처가 없는 관계와 구분합니다.
     *
     * @param request 출발 노드, 도착 노드, 관계 강도와 선택적 워크스페이스 ID
     * @return 생성되거나 갱신된 관계. 대상 노드를 찾지 못하면 {@link Optional#empty()}
     * @throws IllegalArgumentException 이름 또는 관계 강도가 유효하지 않은 경우
     */
    public Optional<EdgeDto> createEdge(EdgeCreateRequest request) {
        // EdgeCreateRequest record는 GraphDto.java에 정의되어 있고 GraphController.createEdge()가 RequestBody로 받습니다.
        String source = request.source() != null ? request.source().trim() : "";
        String target = request.target() != null ? request.target().trim() : "";
        int strength = request.strength() != null ? request.strength() : 1;
        Long workspaceId = request.workspaceId();

        if (source.isBlank() || target.isBlank()) {
            throw new IllegalArgumentException("출발 노드와 도착 노드를 입력하세요.");
        }
        if (source.equals(target)) {
            throw new IllegalArgumentException("같은 노드끼리는 연결할 수 없습니다.");
        }
        if (strength < 1 || strength > 1000) {
            throw new IllegalArgumentException("관계 강도는 1 이상 1000 이하로 입력하세요.");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("source", source);
        params.put("target", target);
        params.put("strength", (long) strength);
        // 수동 생성 관계도 출처 추적 대상이므로 manual:{UUID}를 sourceKeys에 추가합니다.
        params.put("sourceKey", "manual:" + UUID.randomUUID());
        params.put("now", Instant.now().toString());
        params.put("wsId", workspaceId);

        try (Session s = driver.session()) {
            // MERGE는 두 엔티티 사이에 관계가 없으면 만들고, 있으면 기존 RELATED_TO를 재사용합니다.
            // 이렇게 해야 같은 두 노드 사이에 중복 관계가 여러 개 생기지 않습니다.
            var result = s.run(
                    "MATCH (a {name: $source}), (b {name: $target}) " +
                            "WHERE (a:Country OR a:Organization OR a:Person) " +
                            "  AND (b:Country OR b:Organization OR b:Person) " +
                            "WITH a, b WHERE id(a) <> id(b) " +
                            "MERGE (a)-[r:RELATED_TO]-(b) " +
                            "SET r.strength = coalesce(r.strength, 0) + $strength, " +
                            "    r.articleCount = coalesce(r.articleCount, 0) + $strength, " +
                            "    r.lastMentioned = $now, " +
                            "    r.sourceKeys = CASE " +
                            "      WHEN $sourceKey IN coalesce(r.sourceKeys, []) THEN coalesce(r.sourceKeys, []) " +
                            "      ELSE coalesce(r.sourceKeys, []) + [$sourceKey] END, " +
                            "    r.workspaceIds = CASE " +
                            "      WHEN $wsId IS NULL OR $wsId = 0 THEN coalesce(r.workspaceIds, []) " +
                            "      WHEN $wsId IN coalesce(r.workspaceIds, []) THEN coalesce(r.workspaceIds, []) " +
                            "      ELSE coalesce(r.workspaceIds, []) + [$wsId] END " +
                            "RETURN a.name AS source, b.name AS target, " +
                            "       r.strength AS strength, r.articleCount AS articleCount, " +
                            "       coalesce(r.lastMentioned, '') AS lastMentioned",
                    params
            );
            if (!result.hasNext()) return Optional.empty();
            // toEdgeDto는 이 GraphService 파일 하단 private 메서드입니다. GraphDto.EdgeDto record로 변환합니다.
            return Optional.of(toEdgeDto(result.next()));
        }
    }

    /**
     * Neo4j 엔티티 노드의 이름 또는 타입을 수정합니다.
     * 이름 변경 전 중복 엔티티가 있는지 검사하고, 타입 변경 시
     * Country, Organization, Person 라벨과 type 속성을 함께 변경합니다.
     *
     * @param oldName 수정할 현재 엔티티 이름
     * @param request 새로운 이름과 타입
     * @return 수정된 노드. 대상 노드를 찾지 못하면 {@link Optional#empty()}
     * @throws IllegalArgumentException 타입이 유효하지 않거나 같은 이름의 노드가 이미 존재하는 경우
     */
    public Optional<NodeDto> updateNode(String oldName, EntityUpdateRequest request) {
        // EntityUpdateRequest record는 GraphDto.java에 있고 GraphController.updateNode()가 RequestBody로 받습니다.
        String newName = request.name() != null && !request.name().isBlank()
                ? request.name().trim()
                : oldName;
        String newType = request.type() != null && !request.type().isBlank()
                ? request.type().trim()
                : null;
        if (newType != null && !List.of("Country", "Organization", "Person").contains(newType)) {
            throw new IllegalArgumentException("지원하지 않는 엔티티 타입입니다.");
        }

        try (Session s = driver.session()) {
            // 이름 변경 시 같은 이름의 다른 엔티티가 이미 있으면 그래프 노드 충돌이 생기므로 먼저 검사합니다.
            Map<String, Object> duplicateParams = Map.of("oldName", oldName, "newName", newName);
            var duplicate = s.run(
                    "MATCH (other {name: $newName}) " +
                            "WHERE (other:Country OR other:Organization OR other:Person) AND other.name <> $oldName " +
                            "RETURN count(other) AS count",
                    duplicateParams
            );
            if (duplicate.hasNext() && duplicate.next().get("count").asInt(0) > 0) {
                throw new IllegalArgumentException("이미 같은 이름의 엔티티가 있습니다.");
            }

            Map<String, Object> params = new HashMap<>();
            params.put("oldName", oldName);
            params.put("newName", newName);
            params.put("newType", newType);

            // name/type 속성을 갱신하고, type이 바뀐 경우 Neo4j 라벨도 함께 교체합니다.
            // FOREACH + CASE WHEN은 Cypher에서 조건부 SET/REMOVE를 수행하기 위한 패턴입니다.
            var result = s.run(
                    "MATCH (e {name: $oldName}) " +
                            "WHERE e:Country OR e:Organization OR e:Person " +
                            "WITH collect(e)[0] AS e " +
                            "WHERE e IS NOT NULL " +
                            "SET e.name = $newName, e.type = coalesce($newType, e.type) " +
                            "FOREACH (_ IN CASE WHEN $newType = 'Country' THEN [1] ELSE [] END | SET e:Country REMOVE e:Organization REMOVE e:Person) " +
                            "FOREACH (_ IN CASE WHEN $newType = 'Organization' THEN [1] ELSE [] END | SET e:Organization REMOVE e:Country REMOVE e:Person) " +
                            "FOREACH (_ IN CASE WHEN $newType = 'Person' THEN [1] ELSE [] END | SET e:Person REMOVE e:Country REMOVE e:Organization) " +
                            "RETURN e.name AS name, coalesce(e.type, 'Unknown') AS type, " +
                            "       size([(e)-[r:RELATED_TO]-() WHERE size(coalesce(r.sourceKeys, [])) > 0 | r]) AS degree",
                    params
            );
            if (!result.hasNext()) return Optional.empty();
            return Optional.of(toNodeDto(result.next()));
        }
    }

    /**
     * 엣지 조회의 IN 조건에 사용할 노드 이름 집합을 만듭니다.
     */
    private Set<String> nodeNames(List<NodeDto> nodes) {
        Set<String> names = new HashSet<>();
        nodes.forEach(n -> names.add(n.name()));
        return names;
    }

    /**
     * 기존 Cypher 파라미터 Map에 names 배열을 추가합니다.
     * 출처: getGraph(), getWorkspaceGraph()의 2단계 엣지 조회에서 사용됩니다.
     */
    private Map<String, Object> withNames(Map<String, Object> params, Set<String> names) {
        Map<String, Object> next = new HashMap<>(params);
        next.put("names", new ArrayList<>(names));
        return next;
    }

    /**
     * 현재 시각에서 days일을 뺀 ISO-8601 문자열입니다.
     * Neo4j 관계의 lastMentioned도 ISO 문자열로 저장되어 있어 문자열 비교로 시간 필터를 적용합니다.
     */
    private String since(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS).toString();
    }

    /**
     * 관계 alias별 시간 필터 Cypher 조각을 만듭니다.
     * 출처: getGraph(), getWorkspaceGraph()의 엣지 조회 쿼리에서 사용됩니다.
     */
    private String timeFilter(String relAlias, Integer days) {
        return days == null ? "" : "  AND coalesce(" + relAlias + ".lastMentioned, '') >= $since ";
    }

    /**
     * Neo4j Record를 GraphDto.NodeDto로 변환합니다.
     * 출처: 이 파일 내부 getGraph(), getNodeDetail(), search(), getWorkspaceGraph(), updateNode()에서
     * .list(this::toNodeDto) 또는 직접 호출 형태로 사용됩니다.
     */
    private NodeDto toNodeDto(org.neo4j.driver.Record r) {
        String name = r.get("name").asString("");
        return new NodeDto(
                name,
                name,
                r.get("type").asString("Unknown"),
                r.get("degree").asInt(0)
        );
    }

    /**
     * Neo4j Record를 GraphDto.EdgeDto로 변환합니다.
     *
     * 메서드 위치:
     * - backend/src/main/java/com/ohara/service/GraphService.java 안에 있는 private 메서드입니다.
     *
     * DTO 출처:
     * - EdgeDto record는 backend/src/main/java/com/ohara/model/GraphDto.java에 정의되어 있습니다.
     *
     * 호출 출처:
     * - getGraph(): 전체 그래프 관계 목록 변환
     * - getWorkspaceGraph(): 워크스페이스 그래프 관계 목록 변환
     * - createEdge(): 수동 관계 생성/갱신 결과 변환
     */
    private EdgeDto toEdgeDto(org.neo4j.driver.Record r) {
        return new EdgeDto(
                r.get("source").asString(""),
                r.get("target").asString(""),
                r.get("strength").asInt(0),
                r.get("articleCount").asInt(0),
                r.get("lastMentioned").asString("")
        );
    }

    /**
     * Neo4j Article 조회 결과를 GraphDto.ArticleDto로 변환합니다.
     * 출처: getNodeDetail()의 recentArticles 구성에서 사용됩니다.
     */
    private ArticleDto toArticleDto(org.neo4j.driver.Record r) {
        return new ArticleDto(
                r.get("title").asString(""),
                r.get("url").asString(""),
                r.get("source").asString(""),
                r.get("publishedAt").asString("")
        );
    }

    /**
     * 관계 출처 조회 결과를 GraphDto.EdgeSourceDto로 변환합니다.
     * 출처: getEdgeSources()에서 Article 또는 Document 출처를 공통 응답 형태로 바꿀 때 사용됩니다.
     */
    private EdgeSourceDto toEdgeSourceDto(org.neo4j.driver.Record r) {
        return new EdgeSourceDto(
                r.get("title").asString(""),
                r.get("url").asString(""),
                r.get("source").asString(""),
                r.get("publishedAt").asString(""),
                r.get("kind").asString("")
        );
    }
}
