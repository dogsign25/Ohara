package com.ohara.service;

import com.ohara.model.GraphDto.*;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GraphService {

    private static final String ENTITY_LABEL_FILTER = "e:Country OR e:Organization OR e:Person ";
    private static final String VALID_REL_COUNT =
            "count(CASE WHEN size(coalesce(rel.sourceKeys, [])) > 0 THEN rel END) ";
    private static final String WORKSPACE_REL_COUNT =
            "count(CASE WHEN $wsId IN coalesce(rel.workspaceIds, []) THEN rel END) ";

    private final Driver driver;

    public GraphService(Driver driver) {
        this.driver = driver;
    }

    /**
     * 전체 Neo4j 엔티티 그래프를 조회합니다.
     * 먼저 degree가 높은 엔티티 노드를 limit만큼 고르고, 선택된 노드들 사이의 RELATED_TO 관계만 반환합니다.
     */
    public GraphResponse getGraph(int limit, int minStrength) {
        return getGraph(limit, minStrength, null);
    }

    public GraphResponse getGraph(int limit, int minStrength, Integer days) {
        try (Session s = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("limit", (long) limit);
            params.put("min", (long) minStrength);
            String relTimeFilter = "";
            if (days != null) {
                params.put("since", since(days));
                relTimeFilter = "WHERE coalesce(rel.lastMentioned, '') >= $since ";
            }

            // e.type 속성을 직접 읽음 — labels()/CASE WHEN 없이도 타입 조회 가능
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

            if (nodes.isEmpty())
                return new GraphResponse(List.of(), List.of(), 0, 0);

            Set<String> names = nodeNames(nodes);

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

            return new GraphResponse(nodes, edges, nodes.size(), edges.size());
        }
    }

    /**
     * 엔티티 이름으로 상세 정보를 조회합니다.
     * 관련 노드는 RELATED_TO 강도순으로, 기사는 MENTIONED_IN 관계 기준 최신순으로 반환합니다.
     */
    public Optional<NodeDetailDto> getNodeDetail(String name) {
        try (Session s = driver.session()) {

            var row = s.run(
                    "MATCH (e {name: $name}) " +
                            "WHERE " + ENTITY_LABEL_FILTER +
                            "OPTIONAL MATCH (e)-[rel:RELATED_TO]-() " +
                            "WITH e, " + VALID_REL_COUNT + "AS degree " +
                            "RETURN coalesce(e.type, 'Unknown') AS type, degree",
                    Map.of("name", name));

            if (!row.hasNext()) return Optional.empty();
            var nr = row.next();

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
    public GraphResponse getWorkspaceGraph(Long workspaceId, int limit, int minStrength) {
        return getWorkspaceGraph(workspaceId, limit, minStrength, null);
    }

    public GraphResponse getWorkspaceGraph(Long workspaceId, int limit, int minStrength, Integer days) {
        if (workspaceId != null && workspaceId == 0L) {
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

            // 이 워크스페이스의 문서에 언급된 엔티티만 조회
            // (MENTIONED_IN_WORKSPACE 관계 활용)
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

    public Optional<PathResponse> findPath(String from, String to, int maxDepth, Long workspaceId) {
        try (Session s = driver.session()) {
            Map<String, Object> params = new HashMap<>();
            params.put("from", from);
            params.put("to", to);
            params.put("maxDepth", (long) maxDepth);
            String workspaceWhere = "";
            if (workspaceId != null && workspaceId != 0L) {
                params.put("wsId", workspaceId);
                workspaceWhere = "AND all(rel IN relationships(p) WHERE $wsId IN coalesce(rel.workspaceIds, [])) ";
            }

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
            List<NodeDto> nodes = row.get("nodes").asList(v -> {
                var m = v.asMap();
                String nodeName = String.valueOf(m.getOrDefault("name", ""));
                return new NodeDto(nodeName, nodeName, String.valueOf(m.getOrDefault("type", "Unknown")), 0);
            });
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

    public List<EdgeSourceDto> getEdgeSources(String source, String target, Long workspaceId) {
        try (Session s = driver.session()) {
            if (workspaceId != null && workspaceId != 0L) {
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

    public Optional<NodeDto> updateNode(String oldName, EntityUpdateRequest request) {
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

    private Set<String> nodeNames(List<NodeDto> nodes) {
        Set<String> names = new HashSet<>();
        nodes.forEach(n -> names.add(n.name()));
        return names;
    }

    private Map<String, Object> withNames(Map<String, Object> params, Set<String> names) {
        Map<String, Object> next = new HashMap<>(params);
        next.put("names", new ArrayList<>(names));
        return next;
    }

    private String since(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS).toString();
    }

    private String timeFilter(String relAlias, Integer days) {
        return days == null ? "" : "  AND coalesce(" + relAlias + ".lastMentioned, '') >= $since ";
    }

    private NodeDto toNodeDto(org.neo4j.driver.Record r) {
        String name = r.get("name").asString("");
        return new NodeDto(
                name,
                name,
                r.get("type").asString("Unknown"),
                r.get("degree").asInt(0)
        );
    }

    private EdgeDto toEdgeDto(org.neo4j.driver.Record r) {
        return new EdgeDto(
                r.get("source").asString(""),
                r.get("target").asString(""),
                r.get("strength").asInt(0),
                r.get("articleCount").asInt(0),
                r.get("lastMentioned").asString("")
        );
    }

    private ArticleDto toArticleDto(org.neo4j.driver.Record r) {
        return new ArticleDto(
                r.get("title").asString(""),
                r.get("url").asString(""),
                r.get("source").asString(""),
                r.get("publishedAt").asString("")
        );
    }

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
