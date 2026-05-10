package com.ohara.service;

import com.ohara.model.Dto.*;
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

    // ── 전체 그래프 ─────────────────────────────────────────────────
    public GraphResponse getGraph(int limit, int minStrength) {
        try (Session s = driver.session()) {

            // 수정 1: int → long 캐스팅 (Neo4j 드라이버는 Long 요구)
            // 수정 2: size([...]) 대신 OPTIONAL MATCH + count() 사용 (버전 호환)
            List<NodeDto> nodes = s.run("""
                MATCH (e)
                WHERE e:Country OR e:Organization OR e:Person
                OPTIONAL MATCH (e)-[rel:RELATED_TO]-()
                WITH e, count(rel) AS degree
                WHERE degree > 0
                ORDER BY degree DESC
                LIMIT $limit
                RETURN e.name AS name, labels(e)[0] AS type, degree
                """,
                    Map.of("limit", (long) limit)
            ).list(r -> new NodeDto(
                    r.get("name").asString(),
                    r.get("name").asString(),
                    r.get("type").asString(),
                    r.get("degree").asInt(0)
            ));

            if (nodes.isEmpty())
                return new GraphResponse(List.of(), List.of(), 0, 0);

            Set<String> names = new HashSet<>();
            nodes.forEach(n -> names.add(n.name()));

            // 수정 3: $min 도 long, coalesce 로 null 속성 안전 처리
            List<EdgeDto> edges = s.run("""
                MATCH (a)-[r:RELATED_TO]-(b)
                WHERE a.name IN $names
                  AND b.name IN $names
                  AND r.strength >= $min
                  AND id(a) < id(b)
                RETURN a.name                        AS source,
                       b.name                        AS target,
                       r.strength                    AS strength,
                       r.articleCount                AS articleCount,
                       coalesce(r.lastMentioned, '') AS lastMentioned
                ORDER BY r.strength DESC
                """,
                    Map.of("names", new ArrayList<>(names), "min", (long) minStrength)
            ).list(r -> new EdgeDto(
                    r.get("source").asString(),
                    r.get("target").asString(),
                    r.get("strength").asInt(0),
                    r.get("articleCount").asInt(0),
                    r.get("lastMentioned").asString("")
            ));

            return new GraphResponse(nodes, edges, nodes.size(), edges.size());
        }
    }

    // ── 특정 노드 상세 ───────────────────────────────────────────────
    public Optional<NodeDetailDto> getNodeDetail(String name) {
        try (Session s = driver.session()) {

            var row = s.run("""
                MATCH (e {name: $name})
                WHERE e:Country OR e:Organization OR e:Person
                OPTIONAL MATCH (e)-[rel:RELATED_TO]-()
                WITH e, count(rel) AS degree
                RETURN labels(e)[0] AS type, degree
                """, Map.of("name", name));

            if (!row.hasNext()) return Optional.empty();
            var nr = row.next();

            List<NodeDto> related = s.run("""
                MATCH (e {name: $name})-[r:RELATED_TO]-(o)
                WHERE o:Country OR o:Organization OR o:Person
                OPTIONAL MATCH (o)-[other:RELATED_TO]-()
                WITH o, r, count(other) AS degree
                RETURN o.name AS name, labels(o)[0] AS type, degree
                ORDER BY r.strength DESC
                LIMIT 20
                """, Map.of("name", name)
            ).list(r -> new NodeDto(
                    r.get("name").asString(),
                    r.get("name").asString(),
                    r.get("type").asString(),
                    r.get("degree").asInt(0)
            ));

            List<ArticleDto> articles = s.run("""
                MATCH (e {name: $name})-[:MENTIONED_IN]->(a:Article)
                RETURN a.title                    AS title,
                       a.url                      AS url,
                       coalesce(a.source, '')     AS source,
                       coalesce(a.publishedAt,'') AS publishedAt
                ORDER BY a.publishedAt DESC
                LIMIT 10
                """, Map.of("name", name)
            ).list(r -> new ArticleDto(
                    r.get("title").asString(""),
                    r.get("url").asString(""),
                    r.get("source").asString(""),
                    r.get("publishedAt").asString("")
            ));

            return Optional.of(new NodeDetailDto(
                    name,
                    nr.get("type").asString(""),
                    nr.get("degree").asInt(0),
                    related,
                    articles
            ));
        }
    }

    // ── 검색 자동완성 ────────────────────────────────────────────────
    public List<NodeDto> search(String query, int limit) {
        try (Session s = driver.session()) {
            return s.run("""
                MATCH (e)
                WHERE (e:Country OR e:Organization OR e:Person)
                  AND toLower(e.name) CONTAINS toLower($q)
                OPTIONAL MATCH (e)-[rel:RELATED_TO]-()
                WITH e, count(rel) AS degree
                ORDER BY degree DESC
                LIMIT $limit
                RETURN e.name AS name, labels(e)[0] AS type, degree
                """, Map.of("q", query, "limit", (long) limit)
            ).list(r -> new NodeDto(
                    r.get("name").asString(),
                    r.get("name").asString(),
                    r.get("type").asString(),
                    r.get("degree").asInt(0)
            ));
        }
    }
}