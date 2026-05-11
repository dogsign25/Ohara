import re
import html

KNOWN_PERSONS = {
    "trump": "Donald Trump", "donald trump": "Donald Trump",
    "biden": "Joe Biden", "joe biden": "Joe Biden",
    "harris": "Kamala Harris", "kamala harris": "Kamala Harris",
    "obama": "Barack Obama", "barack obama": "Barack Obama",
    "rubio": "Marco Rubio", "marco rubio": "Marco Rubio",
    "blinken": "Antony Blinken", "antony blinken": "Antony Blinken",
    "putin": "Vladimir Putin", "vladimir putin": "Vladimir Putin",
    "lavrov": "Sergei Lavrov", "sergei lavrov": "Sergei Lavrov",
    "xi": "Xi Jinping", "xi jinping": "Xi Jinping",
    "macron": "Emmanuel Macron", "emmanuel macron": "Emmanuel Macron",
    "scholz": "Olaf Scholz", "olaf scholz": "Olaf Scholz",
    "starmer": "Keir Starmer", "keir starmer": "Keir Starmer",
    "meloni": "Giorgia Meloni", "giorgia meloni": "Giorgia Meloni",
    "von der leyen": "Ursula von der Leyen",
    "netanyahu": "Benjamin Netanyahu", "benjamin netanyahu": "Benjamin Netanyahu",
    "erdogan": "Recep Tayyip Erdogan",
    "bin salman": "Mohammed bin Salman", "mbs": "Mohammed bin Salman",
    "khamenei": "Ali Khamenei",
    "modi": "Narendra Modi", "narendra modi": "Narendra Modi",
    "zelensky": "Volodymyr Zelensky", "zelenskyy": "Volodymyr Zelensky",
    "volodymyr zelensky": "Volodymyr Zelensky",
    "kim jong un": "Kim Jong Un", "kim jong-un": "Kim Jong Un",
    "yoon": "Yoon Suk-yeol",
}

COUNTRY_ALIASES = {
    "united states": "United States", "united states of america": "United States",
    "the united states": "United States", "u.s.": "United States",
    "u.s.a.": "United States", "usa": "United States", "america": "United States",
    "united kingdom": "United Kingdom", "the united kingdom": "United Kingdom",
    "u.k.": "United Kingdom", "great britain": "United Kingdom", "britain": "United Kingdom",
    "russian federation": "Russia", "the russian federation": "Russia",
    "people's republic of china": "China", "prc": "China",
    "north korea": "North Korea", "dprk": "North Korea",
    "democratic people's republic of korea": "North Korea",
    "south korea": "South Korea", "republic of korea": "South Korea",
    "islamic republic of iran": "Iran",
    "uae": "United Arab Emirates", "czech republic": "Czechia",
    "taiwan": "Taiwan", "republic of china": "Taiwan",
    "saudi": "Saudi Arabia",
    "eu": "European Union", "the eu": "European Union",
}

ORG_ALIASES = {
    "nato": "NATO", "north atlantic treaty organization": "NATO",
    "the un": "United Nations", "u.n.": "United Nations", "un": "United Nations",
    "european union": "European Union",
    "imf": "IMF", "international monetary fund": "IMF",
    "who": "WHO", "world health organization": "WHO",
    "fbi": "FBI", "cia": "CIA",
    "fed": "Federal Reserve", "the fed": "Federal Reserve",
    "g7": "G7", "g20": "G20", "opec": "OPEC", "brics": "BRICS",
    "state department": "U.S. State Department",
    "us state department": "U.S. State Department",
    "pentagon": "U.S. Department of Defense",
    "white house": "White House", "kremlin": "Kremlin",
    "iaea": "IAEA", "icc": "ICC", "world bank": "World Bank",
}

_TITLE_PREFIX = re.compile(
    r"^(mr\.?|mrs\.?|ms\.?|dr\.?|prof\.?|sir|lord|president|prime minister"
    r"|minister|secretary|general|admiral|senator|rep\.?|gov\.?)\s+",
    re.IGNORECASE,
)

_NOISE = re.compile(
    r"^\d+$|^[^a-zA-Z]+$|^.{1,2}$"
    r"|^(monday|tuesday|wednesday|thursday|friday|saturday|sunday)$"
    r"|^(january|february|march|april|may|june|july|august"
    r"|september|october|november|december)$",
    re.IGNORECASE,
)


def get_canonical_name(raw_text: str, spacy_label: str):
    """
    엔티티 이름 정규화. 실패 시 None 반환.
    반환값: str | None  (튜플 아님)
    """
    text = html.unescape(raw_text).strip()
    if not text or _NOISE.match(text):
        return None

    key = text.lower()

    # 알려진 인물 사전 (오분류 교정)
    if key in KNOWN_PERSONS:
        return KNOWN_PERSONS[key]
    stripped_key = _TITLE_PREFIX.sub("", text).strip().lower()
    if stripped_key in KNOWN_PERSONS:
        return KNOWN_PERSONS[stripped_key]

    if spacy_label == "GPE":
        return COUNTRY_ALIASES.get(key, _title(text))

    if spacy_label == "ORG":
        return ORG_ALIASES.get(key, text)

    if spacy_label == "PERSON":
        clean = _TITLE_PREFIX.sub("", text).strip()
        if len(clean) < 3 or not any(c.isupper() for c in clean):
            return None
        return _title(clean)

    return None


def get_entity_type(raw_text: str, spacy_label: str):
    """
    엔티티 타입 반환. 오분류 교정 포함.
    반환값: 'Country' | 'Organization' | 'Person' | None
    """
    text = html.unescape(raw_text).strip()
    key  = text.lower()
    stripped_key = _TITLE_PREFIX.sub("", text).strip().lower()

    # 알려진 인물이면 무조건 Person
    if key in KNOWN_PERSONS or stripped_key in KNOWN_PERSONS:
        return "Person"

    return {"GPE": "Country", "ORG": "Organization", "PERSON": "Person"}.get(spacy_label)


def _title(text):
    LOWER = {"of", "the", "and", "in", "on", "at", "to", "for", "a", "an", "van", "de", "bin"}
    words = text.split()
    return " ".join(
        w if (i > 0 and w.lower() in LOWER) else w.capitalize()
        for i, w in enumerate(words)
    )