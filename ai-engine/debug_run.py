"""
Neo4j 없이 수집 + 추출 결과만 콘솔로 확인하는 스크립트
Phase 1 검증에 사용
"""
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

from crawler.collector import fetch_all
from nlp.extractor     import extract_batch

articles = fetch_all()
print(f"\n수집된 기사: {len(articles)}개\n{'─'*60}")

results = extract_batch(articles)

for r in results[:10]:
    if not r.entities:
        continue
    print(f"\n📰 [{r.article.source}] {r.article.title[:80]}")
    for e in r.entities:
        print(f"   [{e.etype:<14}] {e.name}")

total    = sum(len(r.entities) for r in results)
nonempty = sum(1 for r in results if r.entities)
print(f"\n{'─'*60}")
print(f"엔티티 있는 기사: {nonempty}/{len(results)}")
print(f"총 추출 엔티티:   {total}개")
