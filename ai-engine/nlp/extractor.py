import spacy
import logging
from dataclasses import dataclass
from .normalizer import normalize, spacy_label_to_type

logger = logging.getLogger(__name__)

_nlp = spacy.load("en_core_web_lg")
TARGET = {"GPE", "ORG", "PERSON"}


@dataclass
class Entity:
    name: str         # canonical name
    etype: str        # Country | Organization | Person


@dataclass
class Result:
    article: object   # crawler.collector.Article
    entities: list    # List[Entity]


def extract_batch(articles):
    results = []
    texts = [a.text for a in articles]

    for article, doc in zip(articles, _nlp.pipe(texts, batch_size=32)):
        seen = set()
        entities = []

        for ent in doc.ents:
            if ent.label_ not in TARGET:
                continue
            etype = spacy_label_to_type(ent.label_)
            name  = normalize(ent.text, ent.label_)
            if not name or not etype:
                continue
            key = f"{etype}:{name}"
            if key in seen:
                continue
            seen.add(key)
            entities.append(Entity(name=name, etype=etype))

        results.append(Result(article=article, entities=entities))

    return results
