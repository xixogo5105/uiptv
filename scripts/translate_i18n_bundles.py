#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
I18N_DIR = ROOT / "src" / "main" / "resources" / "i18n"
SOURCE_FILE = I18N_DIR / "messages_en.properties"

LOCALE_TO_LANG = {
    "ar_SA": "ar",
    "bn_BD": "bn",
    "de_DE": "de",
    "es_ES": "es",
    "fr_FR": "fr",
    "hi_IN": "hi",
    "id_ID": "id",
    "it_IT": "it",
    "ja_JP": "ja",
    "ko_KR": "ko",
    "ml_IN": "ml",
    "pa_IN": "pa",
    "pt_BR": "pt",
    "pt_PT": "pt",
    "ru_RU": "ru",
    "ta_IN": "ta",
    "te_IN": "te",
    "th_TH": "th",
    "tr_TR": "tr",
    "uk_UA": "uk",
    "ur_PK": "ur",
    "vi_VN": "vi",
    "zh_CN": "zh-CN",
    "zh_TW": "zh-TW",
}

ENTRY_PATTERN = re.compile(r"^([^#!][^=]*)=(.*)$")
TOKEN_PATTERN = re.compile(r"(%(?:\\d+\\$)?[-+#0,(\\d.]*[a-zA-Z]|\\{\\d+})")
SEP = "\n§§§UIPTV_SPLIT§§§\n"


def read_properties(path: Path) -> tuple[list[str], list[tuple[str, str]]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    ordered_entries = []
    for line in lines:
        match = ENTRY_PATTERN.match(line)
        if not match:
            continue
        key, value = match.group(1), match.group(2)
        ordered_entries.append((key, value))
    return lines, ordered_entries


def should_translate(value: str) -> bool:
    trimmed = value.strip()
    if not trimmed:
        return False
    if len(trimmed) <= 2:
        return False
    if "http://" in trimmed or "https://" in trimmed:
        return False
    if re.search(r"^[a-z]+/[a-z0-9+.-]+$", trimmed, flags=re.IGNORECASE):
        return False
    if re.search(r"^[A-Za-z0-9_.-]+$", trimmed) and len(trimmed) > 20:
        return False
    if re.search(r"[\\[\\]{}<>|`]", trimmed):
        return False
    return bool(re.search(r"[A-Za-z]", trimmed))


def mask_tokens(text: str) -> tuple[str, list[str]]:
    tokens: list[str] = []

    def repl(match: re.Match[str]) -> str:
        idx = len(tokens)
        tokens.append(match.group(0))
        return f"__PH_{idx}__"

    return TOKEN_PATTERN.sub(repl, text), tokens


def unmask_tokens(text: str, tokens: list[str]) -> str:
    out = text
    for idx, token in enumerate(tokens):
        out = out.replace(f"__PH_{idx}__", token)
    return out


def google_translate(text: str, target_lang: str, retries: int = 4) -> str:
    encoded = urllib.parse.quote(text, safe="")
    url = (
        "https://translate.googleapis.com/translate_a/single"
        f"?client=gtx&sl=en&tl={urllib.parse.quote(target_lang)}&dt=t&q={encoded}"
    )
    delay = 0.6
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                payload = response.read().decode("utf-8")
            data = json.loads(payload)
            return "".join(part[0] for part in data[0])
        except Exception:
            if attempt == retries - 1:
                raise
            time.sleep(delay)
            delay *= 1.8
    return text


def translate_values(values: list[str], target_lang: str) -> list[str]:
    if not values:
        return []
    translated: list[str] = []
    batch_size = 25
    for i in range(0, len(values), batch_size):
        batch = values[i : i + batch_size]
        joined = SEP.join(batch)
        translated_joined = google_translate(joined, target_lang)
        pieces = translated_joined.split(SEP)
        if len(pieces) != len(batch):
            # Fallback to one-by-one when split markers are altered.
            pieces = [google_translate(item, target_lang) for item in batch]
        translated.extend(pieces)
        time.sleep(0.05)
    return translated


def build_locale_lines(
    base_lines: list[str],
    translations: dict[str, str],
) -> list[str]:
    output: list[str] = []
    for line in base_lines:
        match = ENTRY_PATTERN.match(line)
        if not match:
            output.append(line)
            continue
        key = match.group(1)
        value = translations.get(key, match.group(2))
        output.append(f"{key}={value}")
    return output


def main() -> None:
    base_lines, entries = read_properties(SOURCE_FILE)
    key_to_value = dict(entries)
    for locale, lang in LOCALE_TO_LANG.items():
        target_file = I18N_DIR / f"messages_{locale}.properties"
        if not target_file.exists():
            continue
        print(f"Translating {target_file.name} ({lang}) ...")
        keys_to_translate: list[str] = []
        masked_values: list[str] = []
        token_map: dict[str, list[str]] = {}
        for key, value in entries:
            if not should_translate(value):
                continue
            masked, tokens = mask_tokens(value)
            keys_to_translate.append(key)
            masked_values.append(masked)
            token_map[key] = tokens

        translated_masked = translate_values(masked_values, lang)
        resolved: dict[str, str] = dict(key_to_value)
        for idx, key in enumerate(keys_to_translate):
            translated_value = translated_masked[idx].strip()
            translated_value = unmask_tokens(translated_value, token_map[key])
            if translated_value:
                resolved[key] = translated_value

        out_lines = build_locale_lines(base_lines, resolved)
        target_file.write_text("\n".join(out_lines) + "\n", encoding="utf-8")

    print("Translation complete.")


if __name__ == "__main__":
    main()
