package com.ohara.service;

import com.ohara.model.GraphDto.*;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GraphService {

    private final Driver driver;

    public GraphService(Driver driver) {
        this.driver = driver;
    }

    /**
     * 전체 Neo4j 엔티티 그래프를 조회합니다.
     * 먼저 degree가 높은 엔티티 노드를 limit만큼 고르고, 선택된 노드들 사이의 RELATED_TO 관계만 반환합니다.
     */
    public GraphResponse getGraph(int limit, int minStrength) {
        try (Session s = driver.session()) {

            // e.type 속성을 직접 읽음 — labels()/CASE WHEN 없이도 타입 조회 가능
            List<NodeDto> nodes = s.run(
                    "MATCH (e) " +
                            "WHERE e:Country OR e:Organization OR e:Person " +
                            "OPTIONAL MATCH (e)-[rel:RELATED_TO]-() " +
                            "WITH e, count(rel) AS degree " +
                            "WHERE degree > 0 " +
                            "ORDER BY degree DESC " +
                            "LIMIT $limit " +
                            "RETURN e.name AS name, " +
                            "       coalesce(e.type, 'Unknown') AS type, " +
                            "       degree",
                    Map.of("limit", (long) limit)
            ).list(r -> new NodeDto(
                    r.get("name").asString(""),
                    r.get("name").asString(""),
                    r.get("type").asString("Unknown"),
                    r.get("degree").asInt(0)
            ));

            if (nodes.isEmpty())
                return new GraphResponse(List.of(), List.of(), 0, 0);

            Set<String> names = new HashSet<>();
            nodes.forEach(n -> names.add(n.name()));

            List<EdgeDto> edges = s.run(
                    "MATCH (a)-[r:RELATED_TO]-(b) " +
                            "WHERE a.name IN $names " +
                            "  AND b.name IN $names " +
                            "  AND r.strength >= $min " +
                            "  AND id(a) < id(b) " +
                            "RETURN a.name AS source, " +
                            "       b.name AS target, " +
                            "       r.strength AS strength, " +
                            "       r.articleCount AS articleCount, " +
                            "       coalesce(r.lastMentioned, '') AS lastMentioned " +
                            "ORDER BY r.strength DESC",
                    Map.of("names", new ArrayList<>(names), "min", (long) minStrength)
            ).list(r -> new EdgeDto(
                    r.get("source").asString(""),
                    r.get("target").asString(""),
                    r.get("strength").asInt(0),
                    r.get("articleCount").asInt(0),
                    r.get("lastMentioned").asString("")
            ));

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
                            "WHERE e:Country OR e:Organization OR e:Person " +
                            "OPTIONAL MATCH (e)-[rel:RELATED_TO]-() " +
                            "WITH e, count(rel) AS degree " +
                            "RETURN coalesce(e.type, 'Unknown') AS type, degree",
                    Map.of("name", name));

            if (!row.hasNext()) return Optional.empty();
            var nr = row.next();

            List<NodeDto> related = s.run(
                    "MATCH (e {name: $name})-[r:RELATED_TO]-(o) " +
                            "WHERE o:Country OR o:Organization OR o:Person " +
                            "OPTIONAL MATCH (o)-[other:RELATED_TO]-() " +
                            "WITH o, r, count(other) AS degree " +
                            "RETURN o.name AS name, " +
                            "       coalesce(o.type, 'Unknown') AS type, degree " +
                            "ORDER BY r.strength DESC LIMIT 20",
                    Map.of("name", name)
            ).list(r -> new NodeDto(
                    r.get("name").asString(""),
                    r.get("name").asString(""),
                    r.get("type").asString("Unknown"),
                    r.get("degree").asInt(0)
            ));

            List<ArticleDto> articles = s.run(
                    "MATCH (e {name: $name})-[:MENTIONED_IN]->(a:Article) " +
                            "RETURN a.title AS title, " +
                            "       a.url AS url, " +
                            "       coalesce(a.source, '') AS source, " +
                            "       coalesce(a.publishedAt, '') AS publishedAt " +
                            "ORDER BY a.publishedAt DESC LIMIT 10",
                    Map.of("name", name)
            ).list(r -> new ArticleDto(
                    r.get("title").asString(""),
                    r.get("url").asString(""),
                    r.get("source").asString(""),
                    r.get("publishedAt").asString("")
            ));

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
                            "WHERE (e:Country OR e:Organization OR e:Person) " +
                            "  AND toLower(e.name) CONTAINS toLower($q) " +
                            "OPTIONAL MATCH (e)-[rel:RELATED_TO]-() " +
                            "WITH e, count(rel) AS degree " +
                            "ORDER BY degree DESC LIMIT $limit " +
                            "RETURN e.name AS name, " +
                            "       coalesce(e.type, 'Unknown') AS type, degree",
                    Map.of("q", query, "limit", (long) limit)
            ).list(r -> new NodeDto(
                    r.get("name").asString(""),
                    r.get("name").asString(""),
                    r.get("type").asString("Unknown"),
                    r.get("degree").asInt(0)
            ));
        }
    }
    /**
     * 워크스페이스 전용 그래프를 조회합니다.
     * id=0은 가상 Default 워크스페이스로 전체 그래프를 그대로 반환합니다.
     * 일반 워크스페이스는 해당 workspaceId의 문서에 언급된 엔티티만 포함합니다.
     */
    public GraphResponse getWorkspaceGraph(Long workspaceId, int limit, int minStrength) {
        if (workspaceId != null && workspaceId == 0L) {
            return getGraph(limit, minStrength);
        }

        try (Session s = driver.session()) {

            // 이 워크스페이스의 문서에 언급된 엔티티만 조회
            // (MENTIONED_IN_WORKSPACE 관계 활용)
            List<NodeDto> nodes = s.run(
                    "MATCH (e)-[:MENTIONED_IN_WORKSPACE {workspaceId: $wsId}]->(d:Document) " +
                            "WHERE e:Country OR e:Organization OR e:Person " +
                            "WITH e, count(d) AS docCount " +
                            "OPTIONAL MATCH (e)-[rel:RELATED_TO]-() " +
                            "WITH e, docCount, count(rel) AS degree " +
                            "ORDER BY degree DESC LIMIT $limit " +
                            "RETURN e.name AS name, " +
                            "       coalesce(e.type, 'Unknown') AS type, " +
                            "       degree",
                    Map.of("wsId", workspaceId, "limit", (long) limit)
            ).list(r -> new NodeDto(
                    r.get("name").asString(""),
                    r.get("name").asString(""),
                    r.get("type").asString("Unknown"),
                    r.get("degree").asInt(0)
            ));

            if (nodes.isEmpty())
                return new GraphResponse(List.of(), List.of(), 0, 0);

            Set<String> names = new HashSet<>();
            nodes.forEach(n -> names.add(n.name()));

            List<EdgeDto> edges = s.run(
                    "MATCH (a)-[:MENTIONED_IN_WORKSPACE {workspaceId: $wsId}]->(d:Document)" +
                            "<-[:MENTIONED_IN_WORKSPACE {workspaceId: $wsId}]-(b) " +
                            "MATCH (a)-[r:RELATED_TO]-(b) " +
                            "WHERE a.name IN $names AND b.name IN $names " +
                            "  AND r.strength >= $min AND id(a) < id(b) " +
                            "WITH a, b, r, count(DISTINCT d) AS workspaceArticleCount " +
                            "RETURN a.name AS source, b.name AS target, " +
                            "       r.strength AS strength, workspaceArticleCount AS articleCount, " +
                            "       coalesce(r.lastMentioned, '') AS lastMentioned " +
                            "ORDER BY workspaceArticleCount DESC, r.strength DESC",
                    Map.of("wsId", workspaceId, "names", new ArrayList<>(names), "min", (long) minStrength)
            ).list(r -> new EdgeDto(
                    r.get("source").asString(""),
                    r.get("target").asString(""),
                    r.get("strength").asInt(0),
                    r.get("articleCount").asInt(0),
                    r.get("lastMentioned").asString("")
            ));

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
}
