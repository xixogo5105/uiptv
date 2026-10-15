"""Name normalization and alias handling for UIPTV EPG Builder."""

from __future__ import annotations

import re
import unicodedata
from functools import lru_cache

# Try to use regex module for Unicode property support, fallback to re
try:
    import regex as re_unicode
    HAS_REGEX = True
except ImportError:
    import re as re_unicode
    HAS_REGEX = False

# Patterns matching the Java implementation
WHITESPACE = re.compile(r"\s+")
if HAS_REGEX:
    QUALITY = re_unicode.compile(r"(?<![\p{L}\p{N}])(?:fhd|uhd|hd|sd|4k|8k|720p|1080p|2160p)(?![\p{L}\p{N}])")
    EMPTY_BRACKETS = re_unicode.compile(r"\([\s|/,-]*\)|\[[\s|/,-]*\]")
    EDGE_SEPARATORS = re_unicode.compile(r"^[\s|/,-]+|[\s|/,-]+$")
    CIRCLED_CHARS = re_unicode.compile(r"[Ⓐ-Ⓩⓐ-ⓩ]")
    # Keep + so time-shift channels never collapse into their parent station
    # Also keep spaces for word separation
    FINAL_CLEAN = re_unicode.compile(r"[^\p{L}\p{N} +]")
else:
    # Fallback patterns without Unicode properties
    QUALITY = re.compile(r"(?<![a-zA-Z0-9])(?:fhd|uhd|hd|sd|4k|8k|720p|1080p|2160p)(?![a-zA-Z0-9])", re.IGNORECASE)
    EMPTY_BRACKETS = re.compile(r"\([\s|/,-]*\)|\[[\s|/,-]*\]")
    EDGE_SEPARATORS = re.compile(r"^[\s|/,-]+|[\s|/,-]+$")
    CIRCLED_CHARS = re.compile(r"[Ⓐ-Ⓩⓐ-ⓩ]")
    # Keep + so time-shift channels never collapse into their parent station
    FINAL_CLEAN = re.compile(r"[^a-zA-Z0-9 +]")


def normalize_name(name: str | None) -> str:
    """Unicode-normalize builder candidate names; cache signatures remain opaque Java values."""
    if not name:
        return ""
    # Unicode normalize, case fold, collapse whitespace
    normalized = unicodedata.normalize("NFKC", name.strip().casefold())
    normalized = WHITESPACE.sub(" ", normalized)
    return normalized


def normalize_quality_name(name: str | None) -> str:
    """Normalize channel name removing quality markers (matches Java XmltvGuide.normalizeQuality)."""
    if not name:
        return ""

    # First apply basic normalization
    value = normalize_name(name)

    # Remove quality markers
    value = QUALITY.sub(" ", value)

    # Remove empty brackets and edge separators
    value = EDGE_SEPARATORS.sub("", EMPTY_BRACKETS.sub(" ", value))

    # Remove circled characters (playlist annotations)
    value = CIRCLED_CHARS.sub("", value)

    # Keep only letters, numbers, and + (for time-shift)
    value = FINAL_CLEAN.sub("", value)

    return value.replace(" ", "")


def extract_country_from_name(name: str) -> str:
    """Extract country code from channel name if present."""
    # Look for common patterns like "BBC One UK", "CNN US", etc.
    patterns = [
        r"\b(UK|US|CA|AU|DE|FR|ES|IT|NL|PL|BE|CH|AT|SE|NO|DK|FI|IE|PT|GR|CZ|HU|RO|BG|HR|SI|SK|LT|LV|EE|MT|CY)\b",
        r"\b(GB|USA|CAN|AUS|GER|FRA|ESP|ITA|NLD|POL)\b",
    ]
    for pattern in patterns:
        match = re.search(pattern, name, re.IGNORECASE)
        if match:
            return match.group(1).upper()
    return ""


def extract_language_from_name(name: str) -> str:
    """Extract language code from channel name if present."""
    # Look for language indicators
    lang_patterns = {
        "en": r"\b(EN|ENG|ENGLISH)\b",
        "de": r"\b(DE|DEU|GERMAN|DEUTSCH)\b",
        "fr": r"\b(FR|FRA|FRENCH|FRANCAIS)\b",
        "es": r"\b(ES|SPA|SPANISH|ESPAÑOL)\b",
        "it": r"\b(IT|ITA|ITALIAN|ITALIANO)\b",
        "nl": r"\b(NL|NLD|DUTCH|NEDERLANDS)\b",
        "pl": r"\b(PL|POL|POLISH|POLSKI)\b",
    }
    for code, pattern in lang_patterns.items():
        if re.search(pattern, name, re.IGNORECASE):
            return code
    return ""


@lru_cache(maxsize=1024)
def get_quality_variants(name: str) -> list[str]:
    """Get all quality variants of a name for matching."""
    base = normalize_name(name)
    quality_removed = normalize_quality_name(name)
    variants = {base, quality_removed}

    return sorted(v for v in variants if v)


def names_are_similar(name1: str, name2: str, threshold: float = 0.85) -> bool:
    """Check if two names are similar using simple ratio."""
    from difflib import SequenceMatcher
    return SequenceMatcher(None, normalize_name(name1), normalize_name(name2)).ratio() >= threshold


# Country-specific alias dictionaries
COUNTRY_ALIASES: dict[str, dict[str, str]] = {
    "GB": {
        "bbc1": "bbc one",
        "bbc2": "bbc two",
        "itv1": "itv",
        "channel4": "channel 4",
        "channel5": "channel 5",
        "sky news": "sky news",
        "sky sports 1": "sky sports main event",
    },
    "US": {
        "abc": "abc",
        "cbs": "cbs",
        "nbc": "nbc",
        "fox": "fox",
        "pbs": "pbs",
        "cw": "the cw",
    },
    "DE": {
        "das erste": "ard",
        "zdf": "zdf",
        "rtl": "rtl",
        "sat1": "sat.1",
        "pro7": "pro sieben",
    },
}


def apply_country_aliases(name: str, country: str) -> str:
    """Apply country-specific aliases."""
    if country in COUNTRY_ALIASES:
        normalized = normalize_name(name)
        return COUNTRY_ALIASES[country].get(normalized, name)
    return name