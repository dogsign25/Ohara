"""
엔티티 정규화
"U.S." / "America" / "United States" → 동일 노드로 통일
"""

import re

# ── 국가 alias ───────────────────────────────────────────────────────
COUNTRY_ALIASES = {
    "united states": "United States",
    "united states of america": "United States",
    "the united states": "United States",
    "u.s.": "United States",
    "u.s.a.": "United States",
    "us": "United States",
    "usa": "United States",
    "america": "United States",

    "united kingdom": "United Kingdom",
    "the united kingdom": "United Kingdom",
    "u.k.": "United Kingdom",
    "uk": "United Kingdom",
    "great britain": "United Kingdom",
    "britain": "United Kingdom",

    "russian federation": "Russia",
    "the russian federation": "Russia",

    "people's republic of china": "China",
    "prc": "China",

    "north korea": "North Korea",
    "democratic people's republic of korea": "North Korea",
    "dprk": "North Korea",

    "south korea": "South Korea",
    "republic of korea": "South Korea",

    "islamic republic of iran": "Iran",
    "uae": "United Arab Emirates",
    "czech republic": "Czechia",
}

# ── 기관 alias ───────────────────────────────────────────────────────
ORG_ALIASES = {
    "nato": "NATO",
    "north atlantic treaty organization": "NATO",
    "the un": "United Nations",
    "u.n.": "United Nations",
    "eu": "European Union",
    "the eu": "European Union",
    "imf": "IMF",
    "international monetary fund": "IMF",
    "who": "WHO",
    "world health organization": "WHO",
    "fbi": "FBI",
    "cia": "CIA",
    "fed": "Federal Reserve",
    "the fed": "Federal Reserve",
    "federal reserve": "Federal Reserve",
    "g7": "G7",
    "g20": "G20",
    "opec": "OPEC",
    "brics": "BRICS",
    "state department": "U.S. State Department",
    "pentagon": "U.S. Department of Defense",
    "white house": "White House",
}

# ── 노이즈 필터 ──────────────────────────────────────────────────────
_NOISE = re.compile(
    r"^\d+$|^[^a-zA-Z]+$|^.{1,2}$"
    r"|^(monday|tuesday|wednesday|thursday|friday|saturday|sunday)$"
    r"|^(january|february|march|april|may|june|july|august"
    r"|september|october|november|december)$",
    re.IGNORECASE,
)


def normalize(text, spacy_label):
    """
    spaCy 엔티티 → canonical name 반환, 노이즈면 None
    """
    cleaned = text.strip()
    if _NOISE.match(cleaned):
        return None

    key = cleaned.lower()

    if spacy_label == "GPE":          # 국가
        return COUNTRY_ALIASES.get(key, _title(cleaned))

    if spacy_label == "ORG":          # 기관
        return ORG_ALIASES.get(key, cleaned)

    if spacy_label == "PERSON":       # 인물
        if len(cleaned) < 4 or not any(c.isupper() for c in cleaned):
            return None
        return _title(cleaned)

    return None


def spacy_label_to_type(label):
    return {"GPE": "Country", "ORG": "Organization", "PERSON": "Person"}.get(label)


def _title(text):
    LOWER = {"of", "the", "and", "in", "on", "at", "to", "for", "a", "an"}
    words = text.split()
    return " ".join(
        w if (i > 0 and w.lower() in LOWER) else w.capitalize()
        for i, w in enumerate(words)
    )
