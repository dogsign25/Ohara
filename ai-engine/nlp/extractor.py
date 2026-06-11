import spacy
import logging
from dataclasses import dataclass
from itertools import combinations
from .normalizer import get_canonical_name, get_entity_type

logger = logging.getLogger(__name__)

_nlp = spacy.load("en_core_web_lg")
TARGET = {"GPE", "ORG", "PERSON"}


@dataclass
class Entity:
    name: str   # 반드시 str
    etype: str  # 반드시 str


@dataclass
class Result:
    article: object
    entities: list
    relations: list


def extract_batch(articles):
    """여러 Article을 spaCy로 분석해 엔티티와 문장 단위 관계를 반환한다."""
    results = []
    texts = [a.text for a in articles]
    for article, doc in zip(articles, _nlp.pipe(texts, batch_size=32)):
        entities, relations = _extract(doc)
        results.append(Result(article=article, entities=entities, relations=relations))
    return results


def _extract(doc):
    """spaCy Doc 하나에서 정규화된 엔티티와 공동 등장 관계를 추출한다."""
    mentions = []

    for ent in doc.ents:
        if ent.label_ not in TARGET:
            continue

        # 두 함수 각각 호출 — 튜플 언팩 없음
        name  = get_canonical_name(ent.text, ent.label_)
        etype = get_entity_type(ent.text, ent.label_)

        if not name or not etype:
            continue

        # 타입 검증 — 혹시라도 이상한 값이 들어오면 건너뜀
        if not isinstance(name, str) or not isinstance(etype, str):
            logger.warning(f"잘못된 타입: name={name!r}, etype={etype!r}")
            continue

        mentions.append({
            "name": name,
            "etype": etype,
            "sent_start": ent.sent.start,
        })

    person_map = _build_person_map(
        m["name"] for m in mentions
        if m["etype"] == "Person"
    )

    seen = set()
    entities = []
    sentence_entities = {}

    for mention in mentions:
        name = person_map.get(mention["name"], mention["name"])
        e = Entity(name=name, etype=mention["etype"])
        key = f"{e.etype}:{e.name}"

        if key not in seen:
            seen.add(key)
            entities.append(e)

        sentence_entities.setdefault(mention["sent_start"], {})[key] = e

    relations = _build_sentence_relations(sentence_entities)
    return entities, relations


def _build_sentence_relations(sentence_entities):
    """같은 문장에 등장한 서로 다른 엔티티 쌍을 관계 후보로 만든다."""
    seen = set()
    relations = []

    for entity_map in sentence_entities.values():
        for x, y in combinations(entity_map.values(), 2):
            key = tuple(sorted((f"{x.etype}:{x.name}", f"{y.etype}:{y.name}")))
            if key in seen or key[0] == key[1]:
                continue
            seen.add(key)
            relations.append((x, y))

    return relations


def _build_person_map(names):
    """성·전체 이름 표현을 같은 인물로 합치기 위한 이름 매핑을 만든다."""
    """성(last name) 기준으로 풀네임 통합"""
    full_names   = {}
    single_names = []

    for n in names:
        if not isinstance(n, str):
            continue
        parts = n.split()
        if len(parts) >= 2:
            last = parts[-1].lower()
            if last not in full_names or len(n) > len(full_names[last]):
                full_names[last] = n
        else:
            single_names.append(n)

    name_map = {n: n for n in full_names.values()}
    for s in single_names:
        name_map[s] = full_names.get(s.lower(), s)

    return name_map
