import feedparser
import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from dateutil import parser as dateparser

logger = logging.getLogger(__name__)

RSS_SOURCES = [
    {"name": "Reuters",    "url": "https://feeds.reuters.com/reuters/topNews"},
    {"name": "BBC",        "url": "https://feeds.bbci.co.uk/news/world/rss.xml"},
    {"name": "CNN",        "url": "http://rss.cnn.com/rss/edition_world.rss"},
    {"name": "Al Jazeera", "url": "https://www.aljazeera.com/xml/rss/all.xml"},
]


@dataclass
class Article:
    title: str
    url: str
    source: str
    published_at: datetime
    text: str  # NLP 대상 (title + description)


def fetch_all():
    articles = []
    for src in RSS_SOURCES:
        try:
            fetched = _fetch(src["name"], src["url"])
            articles.extend(fetched)
            logger.info(f"[{src['name']}] {len(fetched)}개 수집")
        except Exception as e:
            logger.error(f"[{src['name']}] 실패: {e}")
    return articles


def _fetch(name, url):
    feed = feedparser.parse(url)
    result = []
    for entry in feed.entries:
        title = entry.get("title", "").strip()
        link  = entry.get("link",  "").strip()
        desc  = entry.get("summary", entry.get("description", "")).strip()
        if not title or not link:
            continue
        result.append(Article(
            title=title,
            url=link,
            source=name,
            published_at=_parse_date(entry),
            text=f"{title}. {desc}",
        ))
    return result


def _parse_date(entry):
    try:
        if hasattr(entry, "published"):
            return dateparser.parse(entry.published).astimezone(timezone.utc)
    except Exception:
        pass
    return datetime.now(timezone.utc)
