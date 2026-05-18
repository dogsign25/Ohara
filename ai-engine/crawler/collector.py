import feedparser
import html
import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from dateutil import parser as dateparser

logger = logging.getLogger(__name__)

# ── 세계정세 전용 RSS 소스 (Reuters 제외 — 공개 RSS 차단됨) ──────────
RSS_SOURCES = [
    # BBC
    {"name": "BBC",          "url": "https://feeds.bbci.co.uk/news/world/rss.xml"},
    {"name": "BBC Politics",  "url": "https://feeds.bbci.co.uk/news/politics/rss.xml"},
    # Al Jazeera
    {"name": "Al Jazeera",   "url": "https://www.aljazeera.com/xml/rss/all.xml"},
    # The Guardian
    {"name": "Guardian World","url": "https://www.theguardian.com/world/rss"},
    {"name": "Guardian Pol", "url": "https://www.theguardian.com/politics/rss"},
    # NPR
    {"name": "NPR World",    "url": "https://feeds.npr.org/1004/rss.xml"},
    # AP (정치/세계)
    {"name": "AP Politics",  "url": "https://rsshub.app/apnews/topics/politics"},
    # DW (Deutsche Welle)
    {"name": "DW World",     "url": "https://rss.dw.com/rdf/rss-en-world"},
]

# ── 세계정세 관련 키워드 필터 ─────────────────────────────────────────
# 이 키워드 중 하나라도 포함된 기사만 처리
GEOPOLITICS_KEYWORDS = {
    # 정치·외교
    "president", "prime minister", "minister", "government", "parliament",
    "senate", "congress", "election", "diplomacy", "diplomatic", "sanction",
    "treaty", "summit", "bilateral", "foreign policy", "state department",
    "white house", "kremlin", "cabinet",
    # 국제기구
    "nato", "united nations", "un ", " eu ", "european union", "g7", "g20",
    "imf", "world bank", "opec", "brics", "security council",
    # 분쟁·안보
    "war", "conflict", "military", "troops", "missile", "nuclear",
    "ceasefire", "invasion", "occupation", "airstrike", "drone",
    "attack", "terrorism", "intelligence",
    # 경제정치
    "tariff", "trade war", "embargo", "alliance",
}

# ── 제외 키워드 (스포츠·연예·범죄) ───────────────────────────────────
EXCLUDE_KEYWORDS = {
    "nfl", "nba", "soccer", "football", "basketball", "tennis", "golf",
    "oscar", "grammy", "celebrity", "actor", "actress", "movie", "film",
    "murder", "arrest", "police", "crime", "trial", "verdict",
    "recipe", "fashion", "lifestyle",
}


@dataclass
class Article:
    title: str
    url: str
    source: str
    published_at: datetime
    text: str   # NLP 대상 (title + description, HTML 엔티티 제거됨)


def fetch_all():
    articles = []
    for src in RSS_SOURCES:
        try:
            fetched = _fetch(src["name"], src["url"])
            articles.extend(fetched)
            logger.info(f"[{src['name']}] {len(fetched)}개 수집")
        except Exception as e:
            logger.error(f"[{src['name']}] 실패: {e}")
    logger.info(f"세계정세 필터 후 총 {len(articles)}개")
    return articles


def _fetch(name, url):
    feed = feedparser.parse(url)
    result = []
    for entry in feed.entries:
        title = _clean(entry.get("title", ""))
        link  = entry.get("link", "").strip()
        desc  = _clean(entry.get("summary", entry.get("description", "")))

        if not title or not link:
            continue

        combined = f"{title} {desc}".lower()

        # 제외 키워드 먼저 체크
        if any(kw in combined for kw in EXCLUDE_KEYWORDS):
            continue

        # 세계정세 키워드 포함 여부
        if not any(kw in combined for kw in GEOPOLITICS_KEYWORDS):
            continue

        result.append(Article(
            title=title,
            url=link,
            source=name,
            published_at=_parse_date(entry),
            text=f"{title}. {desc}",
        ))
    return result


def _clean(text):
    """HTML 엔티티 디코딩 + 공백 정리 (문제 3: women&#039;s 깨짐 해결)"""
    return html.unescape(text).strip()


def _parse_date(entry):
    try:
        if hasattr(entry, "published"):
            return dateparser.parse(entry.published).astimezone(timezone.utc)
    except Exception:
        pass
    return datetime.now(timezone.utc)