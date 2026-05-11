import spacy
import logging
from dataclasses import dataclass
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


def extract_batch(articles):
    results = []
    texts = [a.text for a in articles]
    for article, doc in zip(articles, _nlp.pipe(texts, batch_size=32)):
        entities = _extract(doc)
        results.append(Result(article=article, entities=entities))
    return results


def _extract(doc):
    raw_persons = []
    raw_others  = []

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

        if etype == "Person":
            raw_persons.append(name)
        else:
            raw_others.append(Entity(name=name, etype=etype))

    unified = _unify_names(raw_persons)

    seen = set()
    result = []
    for e in raw_others:
        key = f"{e.etype}:{e.name}"
        if key not in seen:
            seen.add(key)
            result.append(e)
    for n in unified:
        key = f"Person:{n}"
        if key not in seen:
            seen.add(key)
            result.append(Entity(name=n, etype="Person"))

    return result


def _unify_names(names):
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

    unified = set(full_names.values())
    for s in single_names:
        unified.add(full_names.get(s.lower(), s))

    return list(unified)