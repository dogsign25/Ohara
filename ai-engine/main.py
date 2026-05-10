import sys
import os
import logging
import time
import schedule

sys.path.insert(0, os.path.dirname(__file__))

from crawler.collector    import fetch_all
from nlp.extractor        import extract_batch
from processor.graph_writer import GraphWriter

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("ohara")

NEO4J_URI      = os.environ.get("NEO4J_URI",      "bolt://localhost:7687")
NEO4J_USER     = os.environ.get("NEO4J_USER",     "neo4j")
NEO4J_PASSWORD = os.environ.get("NEO4J_PASSWORD", "12345678")
INTERVAL_SEC   = int(os.environ.get("INTERVAL_SEC", "300"))


def run(writer):
    logger.info("=== 파이프라인 시작 ===")
    articles = fetch_all()
    logger.info(f"수집: {len(articles)}개")
    if not articles:
        return
    results = extract_batch(articles)
    logger.info(f"추출: {sum(len(r.entities) for r in results)}개 엔티티")
    writer.write_batch(results)
    logger.info("=== 파이프라인 완료 ===")


def main():
    logger.info(f"OHARA NLP Engine 시작 (수집 주기: {INTERVAL_SEC}초)")
    writer = GraphWriter(NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD)

    run(writer)
    schedule.every(INTERVAL_SEC).seconds.do(run, writer=writer)

    try:
        while True:
            schedule.run_pending()
            time.sleep(10)
    except KeyboardInterrupt:
        logger.info("종료")
    finally:
        writer.close()


if __name__ == "__main__":
    main()
