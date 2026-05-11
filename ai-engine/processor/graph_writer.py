import logging
from datetime import datetime, timezone
from itertools import combinations
from neo4j import GraphDatabase

logger = logging.getLogger(__name__)

CONSTRAINTS = [
    "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Country)      REQUIRE n.name IS UNIQUE",
    "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Organization) REQUIRE n.name IS UNIQUE",
    "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Person)       REQUIRE n.name IS UNIQUE",
    "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Article)      REQUIRE n.url  IS UNIQUE",
]


class GraphWriter:
    def __init__(self, uri, user, password):
        self._driver = GraphDatabase.driver(uri, auth=(user, password))
        self._init()

    def close(self):
        self._driver.close()

    def _init(self):
        with self._driver.session() as s:
            for c in CONSTRAINTS:
                s.run(c)
        logger.info("Neo4j 제약조건 완료")

    def write(self, result):
        if len(result.entities) < 2:
            return
        now = datetime.now(timezone.utc).isoformat()
        a   = result.article

        with self._driver.session() as s:
            # Article 노드
            s.run("""
                MERGE (a:Article {url: $url})
                ON CREATE SET a.title=$title, a.source=$source,
                              a.publishedAt=$pub, a.createdAt=$now
                ON MATCH  SET a.title=$title
            """, url=a.url, title=a.title, source=a.source,
                 pub=a.published_at.isoformat(), now=now)

            # Entity 노드 — type을 속성으로 저장 (Java에서 labels() 없이 읽기 위함)
            for e in result.entities:
                s.run(f"""
                    MERGE (e:{e.etype} {{name: $name}})
                    ON CREATE SET e.type=$etype, e.createdAt=$now
                    ON MATCH  SET e.type=$etype
                    WITH e
                    MATCH (a:Article {{url: $url}})
                    MERGE (e)-[:MENTIONED_IN]->(a)
                """, name=str(e.name), etype=e.etype, url=a.url, now=now)

            # RELATED_TO: strength 누적
            for x, y in combinations(result.entities, 2):
                s.run(f"""
                    MATCH (a:{x.etype} {{name: $nx}})
                    MATCH (b:{y.etype} {{name: $ny}})
                    MERGE (a)-[r:RELATED_TO]-(b)
                    ON CREATE SET r.strength=1, r.articleCount=1,
                                  r.firstMentioned=$now, r.lastMentioned=$now
                    ON MATCH  SET r.strength=r.strength+1,
                                  r.articleCount=r.articleCount+1,
                                  r.lastMentioned=$now
                """, nx=str(x.name), ny=str(y.name), now=now)

    def write_batch(self, results):
        ok = 0
        for r in results:
            try:
                self.write(r)
                ok += 1
            except Exception as e:
                logger.error(f"저장 실패: {e}")
        logger.info(f"저장 완료: {ok}/{len(results)}")