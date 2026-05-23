"""
ai-engine/api.py
----------------
Spring Boot에서 POST /analyze/url 로 호출하는 FastAPI 서버.

실행:
    cd ai-engine
    source venv/bin/activate
    uvicorn api:app --port 8001 --reload
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional
import requests
from bs4 import BeautifulSoup
from datetime import datetime, timezone

from nlp.extractor import extract_batch
from nlp.normalizer import get_canonical_name, get_entity_type
from crawler.collector import Article
from processor.graph_writer import GraphWriter

# ── Neo4j 연결 (main.py와 동일한 설정) ────────────────────────────
NEO4J_URI      = os.environ.get("NEO4J_URI",      "bolt://localhost:7687")
NEO4J_USER     = os.environ.get("NEO4J_USER",     "neo4j")
NEO4J_PASSWORD = os.environ.get("NEO4J_PASSWORD", "12345678")

writer = GraphWriter(NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD)

app = FastAPI(title="OHARA AI Engine")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── 요청/응답 모델 ─────────────────────────────────────────────────
class AnalyzeUrlRequest(BaseModel):
    url: str
    workspace_id: int
    doc_id: Optional[int] = None


class EntityResult(BaseModel):
    name: str
    type: str  # Country | Organization | Person


class AnalyzeUrlResponse(BaseModel):
    title: str
    entity_count: int
    entities: list[EntityResult]
    workspace_id: int


# ── URL 텍스트 추출 ────────────────────────────────────────────────
def fetch_text_from_url(url: str) -> dict:
    """URL에서 제목과 본문 텍스트를 추출"""
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
        )
    }
    try:
        resp = requests.get(url, headers=headers, timeout=15)
        resp.raise_for_status()
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"URL 접근 실패: {e}")

    soup = BeautifulSoup(resp.text, "html.parser")

    # 불필요한 태그 제거
    for tag in soup(["script", "style", "nav", "footer", "header", "aside", "ads"]):
        tag.decompose()

    title_tag = soup.find("title")
    title = title_tag.get_text(strip=True) if title_tag else url

    # og:title이 더 정확할 때 사용
    og_title = soup.find("meta", property="og:title")
    if og_title and og_title.get("content"):
        title = og_title["content"]

    # 본문 텍스트 추출 (최대 8000자)
    text = soup.get_text(separator=" ", strip=True)
    text = " ".join(text.split())[:8000]

    return {"title": title, "text": text}


# ── 엔드포인트 ─────────────────────────────────────────────────────
@app.post("/analyze/url", response_model=AnalyzeUrlResponse)
def analyze_url(req: AnalyzeUrlRequest):
    """
    URL에서 텍스트 추출 → NER 분석 → Neo4j에 워크스페이스 태그로 저장
    """
    # 1) 텍스트 추출
    fetched = fetch_text_from_url(req.url)

    # 2) spaCy NER
    article = Article(
        title=fetched["title"],
        url=req.url,
        source="user",
        published_at=datetime.now(timezone.utc),
        text=f"{fetched['title']}. {fetched['text']}",
    )
    results = extract_batch([article])
    entities = results[0].entities  # List[Entity]

    # 3) Neo4j에 저장 (워크스페이스 ID 태그 포함)
    _save_to_neo4j(req.workspace_id, req.doc_id, req.url, fetched["title"], entities)

    entity_list = [
        EntityResult(name=e.name, type=e.etype)
        for e in entities
    ]

    return AnalyzeUrlResponse(
        title=fetched["title"],
        entity_count=len(entity_list),
        entities=entity_list,
        workspace_id=req.workspace_id,
    )


def _save_to_neo4j(workspace_id: int, doc_id, url: str, title: str, entities):
    """
    워크스페이스 전용 그래프 저장.
    - (Document) 노드에 workspaceId 속성 부여
    - 엔티티 노드는 글로벌 공유 (같은 인물이 여러 워크스페이스에 등장 가능)
    - MENTIONED_IN_WORKSPACE 관계로 워크스페이스 필터링 지원
    """
    from itertools import combinations
    from neo4j import GraphDatabase
    from datetime import datetime, timezone

    now = datetime.now(timezone.utc).isoformat()

    try:
        driver = GraphDatabase.driver(
            "bolt://localhost:7687",
            auth=("neo4j", "12345678")
        )
        with driver.session() as s:
            # Document 노드 생성
            s.run("""
                MERGE (d:Document {url: $url})
                ON CREATE SET d.title=$title, d.workspaceId=$wsId,
                              d.docId=$docId, d.createdAt=$now
                ON MATCH  SET d.title=$title
            """, url=url, title=title, wsId=workspace_id,
                 docId=doc_id, now=now)

            # 엔티티 노드 + MENTIONED_IN_WORKSPACE 관계
            for e in entities:
                s.run(f"""
                    MERGE (e:{e.etype} {{name: $name}})
                    ON CREATE SET e.type=$etype, e.createdAt=$now
                    ON MATCH  SET e.type=$etype
                    WITH e
                    MATCH (d:Document {{url: $url}})
                    MERGE (e)-[r:MENTIONED_IN_WORKSPACE {{workspaceId: $wsId}}]->(d)
                """, name=str(e.name), etype=e.etype,
                     url=url, wsId=workspace_id, now=now)

            # 엔티티 간 RELATED_TO (글로벌 공유)
            from itertools import combinations
            for x, y in combinations(entities, 2):
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
        driver.close()
    except Exception as e:
        print(f"[Neo4j 저장 실패] {e}")


# ── 헬스체크 ───────────────────────────────────────────────────────
@app.get("/health")
def health():
    return {"status": "ok"}