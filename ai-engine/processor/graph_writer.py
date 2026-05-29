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
    "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Document)     REQUIRE n.docId IS UNIQUE",
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
        if not result.entities:
            return
        now = datetime.now(timezone.utc).isoformat()
        a   = result.article

        with self._driver.session() as s:
            self._merge_article(s, a, now)
            self._merge_article_entities(s, a.url, result.entities, now)
            self._write_related_to(
                s,
                getattr(result, "relations", None),
                result.entities,
                now,
                source_key=f"article:{a.url}",
            )

    def write_batch(self, results):
        ok = 0
        for r in results:
            try:
                self.write(r)
                ok += 1
            except Exception as e:
                logger.error(f"저장 실패: {e}")
        logger.info(f"저장 완료: {ok}/{len(results)}")

    def write_workspace_document(self, workspace_id: int, doc_id, url: str, title: str, entities, relations=None):
        now = datetime.now(timezone.utc).isoformat()

        with self._driver.session() as s:
            self._merge_workspace_document(s, workspace_id, doc_id, url, title, now)
            self._merge_workspace_entities(s, workspace_id, doc_id, url, entities, now)
            doc_key = self._document_key(workspace_id, doc_id, url)
            self._write_related_to(
                s,
                relations,
                entities,
                now,
                source_key=f"workspace:{workspace_id}:{doc_key}",
                workspace_id=workspace_id,
                workspace_doc_key=f"{workspace_id}:{doc_key}",
            )

    def _merge_article(self, session, article, now):
        session.run("""
            MERGE (a:Article {url: $url})
            ON CREATE SET a.title=$title, a.source=$source,
                          a.publishedAt=$pub, a.createdAt=$now
            ON MATCH  SET a.title=$title
        """, url=article.url, title=article.title, source=article.source,
             pub=article.published_at.isoformat(), now=now)

    def _merge_article_entities(self, session, article_url, entities, now):
        for e in entities:
            session.run(f"""
                MERGE (e:{e.etype} {{name: $name}})
                ON CREATE SET e.type=$etype, e.createdAt=$now
                ON MATCH  SET e.type=$etype
                WITH e
                MATCH (a:Article {{url: $url}})
                MERGE (e)-[:MENTIONED_IN]->(a)
            """, name=str(e.name), etype=e.etype, url=article_url, now=now)

    def _merge_workspace_document(self, session, workspace_id, doc_id, url, title, now):
        if doc_id is not None:
            session.run("""
                MERGE (d:Document {docId: $docId})
                ON CREATE SET d.url=$url, d.title=$title, d.workspaceId=$wsId,
                              d.createdAt=$now
                ON MATCH  SET d.url=$url, d.title=$title, d.workspaceId=$wsId
            """, docId=doc_id, url=url, title=title, wsId=workspace_id, now=now)
            return

        session.run("""
            MERGE (d:Document {url: $url, workspaceId: $wsId})
            ON CREATE SET d.title=$title, d.createdAt=$now
            ON MATCH  SET d.title=$title
        """, url=url, title=title, wsId=workspace_id, now=now)

    def _merge_workspace_entities(self, session, workspace_id, doc_id, url, entities, now):
        for e in entities:
            session.run(f"""
                MERGE (e:{e.etype} {{name: $name}})
                ON CREATE SET e.type=$etype, e.createdAt=$now
                ON MATCH  SET e.type=$etype
                WITH e
                MATCH (d:Document)
                WHERE ($docId IS NOT NULL AND d.docId = $docId)
                   OR ($docId IS NULL AND d.url = $url AND d.workspaceId = $wsId)
                MERGE (e)-[:MENTIONED_IN_WORKSPACE {{workspaceId: $wsId}}]->(d)
            """, name=str(e.name), etype=e.etype, docId=doc_id,
                 url=url, wsId=workspace_id, now=now)

    def _document_key(self, workspace_id, doc_id, url):
        return f"doc:{doc_id}" if doc_id is not None else f"url:{workspace_id}:{url}"

    def _write_related_to(
        self,
        session,
        relations,
        entities,
        now,
        source_key=None,
        workspace_id=None,
        workspace_doc_key=None,
    ):
        pairs = relations if relations is not None else combinations(entities, 2)

        for x, y in pairs:
            if x.etype == y.etype and x.name == y.name:
                continue
            session.run(f"""
                MATCH (a:{x.etype} {{name: $nx}})
                MATCH (b:{y.etype} {{name: $ny}})
                MERGE (a)-[r:RELATED_TO]-(b)
                ON CREATE SET r.strength=0, r.articleCount=0,
                              r.sourceKeys=[], r.workspaceIds=[],
                              r.workspaceDocKeys=[], r.firstMentioned=$now
                WITH r,
                     coalesce(r.sourceKeys, []) AS sourceKeys,
                     coalesce(r.workspaceIds, []) AS workspaceIds,
                     coalesce(r.workspaceDocKeys, []) AS workspaceDocKeys
                WITH r, sourceKeys, workspaceIds, workspaceDocKeys,
                     CASE
                       WHEN $sourceKey IS NULL OR NOT ($sourceKey IN sourceKeys)
                       THEN 1 ELSE 0
                     END AS addCount
                SET r.sourceKeys =
                    CASE
                      WHEN $sourceKey IS NULL OR $sourceKey IN sourceKeys
                      THEN sourceKeys ELSE sourceKeys + [$sourceKey]
                    END,
                    r.workspaceIds =
                    CASE
                      WHEN $workspaceId IS NULL OR $workspaceId IN workspaceIds
                      THEN workspaceIds ELSE workspaceIds + [$workspaceId]
                    END,
                    r.workspaceDocKeys =
                    CASE
                      WHEN $workspaceDocKey IS NULL OR $workspaceDocKey IN workspaceDocKeys
                      THEN workspaceDocKeys ELSE workspaceDocKeys + [$workspaceDocKey]
                    END,
                    r.strength=coalesce(r.strength, 0) + addCount,
                    r.articleCount=coalesce(r.articleCount, 0) + addCount,
                    r.lastMentioned=$now
            """, nx=str(x.name), ny=str(y.name), now=now,
                 sourceKey=source_key, workspaceId=workspace_id,
                 workspaceDocKey=workspace_doc_key)
