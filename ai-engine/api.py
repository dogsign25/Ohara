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
    allow_origins=[
        origin.strip()
        for origin in os.environ.get("CORS_ALLOWED_ORIGINS", "http://localhost:3000").split(",")
        if origin.strip()
    ],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── 요청/응답 모델 ─────────────────────────────────────────────────
class AnalyzeUrlRequest(BaseModel):
    url: str
    workspace_id: int
    doc_id: Optional[int] = None


class AnalyzeTextRequest(BaseModel):
    title: str
    text: str
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
    relations = results[0].relations

    # 3) Neo4j에 저장 (워크스페이스 ID 태그 포함)
    _save_to_neo4j(req.workspace_id, req.doc_id, req.url, fetched["title"], entities, relations)

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


@app.post("/analyze/text", response_model=AnalyzeUrlResponse)
def analyze_text(req: AnalyzeTextRequest):
    """
    사용자가 직접 입력하거나 업로드한 텍스트 → NER 분석 → Neo4j 저장
    """
    title = req.title.strip() if req.title and req.title.strip() else "Untitled document"
    text = " ".join(req.text.split())[:12000]
    if not text:
        raise HTTPException(status_code=400, detail="분석할 텍스트가 없습니다.")

    article = Article(
        title=title,
        url=f"workspace://{req.workspace_id}/{req.doc_id or title}",
        source="workspace",
        published_at=datetime.now(timezone.utc),
        text=f"{title}. {text}",
    )
    results = extract_batch([article])
    entities = results[0].entities
    relations = results[0].relations

    _save_to_neo4j(req.workspace_id, req.doc_id, article.url, title, entities, relations)

    entity_list = [
        EntityResult(name=e.name, type=e.etype)
        for e in entities
    ]

    return AnalyzeUrlResponse(
        title=title,
        entity_count=len(entity_list),
        entities=entity_list,
        workspace_id=req.workspace_id,
    )


def _save_to_neo4j(workspace_id: int, doc_id, url: str, title: str, entities, relations):
    """
    워크스페이스 전용 그래프 저장.
    - (Document) 노드에 workspaceId 속성 부여
    - 엔티티 노드는 글로벌 공유 (같은 인물이 여러 워크스페이스에 등장 가능)
    - MENTIONED_IN_WORKSPACE 관계로 워크스페이스 필터링 지원
    """
    try:
        writer.write_workspace_document(workspace_id, doc_id, url, title, entities, relations)
    except Exception as e:
        print(f"[Neo4j 저장 실패] {e}")


# ── 헬스체크 ───────────────────────────────────────────────────────
@app.get("/health")
def health():
    return {"status": "ok"}
