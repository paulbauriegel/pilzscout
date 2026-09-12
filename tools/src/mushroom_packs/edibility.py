"""Step 3b: reference edibility per species.

Source 1: the `edibility` (and `edibility2`) parameter of the English Wikipedia mushroom infobox
({{Mycomorphbox}}), fetched from the article wikitext for every species with an English article.
Source 2: FungiTastic's binary poisonous flag as a fallback.
Output: cache/edibility.parquet with species_id, edibility, edibility_source.
"""
from __future__ import annotations

import json
import re
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import mwparserfromhell
import pandas as pd
from tqdm import tqdm

from .config import Config
from .http import Client

# normalised keys, ordered from most to least dangerous (used when two values are given)
DANGER_ORDER = ["DEADLY", "POISONOUS", "PSYCHOACTIVE", "CAUTION", "INEDIBLE", "UNKNOWN", "EDIBLE", "CHOICE"]

VALUE_MAP = [
    ("DEADLY", r"deadly|lethal|t[öo]dlich"),
    ("POISONOUS", r"poison|toxic|giftig"),
    ("PSYCHOACTIVE", r"psychoactive|hallucinogen|psilocyb"),
    ("CAUTION", r"caution|allergenic|not recommended|conditionally|cooked"),
    ("INEDIBLE", r"inedible|ungenie"),
    ("CHOICE", r"choice"),
    ("EDIBLE", r"edible|essbar"),
    ("UNKNOWN", r"unknown|unbekannt"),
]


def normalise(raw: str | None) -> str | None:
    if not raw:
        return None
    text = raw.lower()
    for key, pattern in VALUE_MAP:
        if re.search(pattern, text):
            return key
    return None


def worst(values: list[str]) -> str | None:
    vals = [v for v in values if v]
    if not vals:
        return None
    return sorted(vals, key=DANGER_ORDER.index)[0]


MORPH_PARAMS = ("hymeniumType", "capShape", "whichGills", "stipeCharacter", "sporePrintColor", "ecologicalType")


def infobox_params(wikitext: str) -> dict[str, list[str]]:
    """All Mycomorphbox parameters (howEdible*, hymeniumType, stipeCharacter, ...) as lists of raw values."""
    code = mwparserfromhell.parse(wikitext)
    out: dict[str, list[str]] = {}
    for t in code.filter_templates():
        if t.name.strip().lower().replace("_", " ") in ("mycomorphbox", "mycomorph box"):
            for param in t.params:
                name = str(param.name).strip()
                value = str(param.value).strip()
                if not value:
                    continue
                base = "howEdible" if name.lower().startswith("howedible") or name.lower().startswith("edibility") else name
                out.setdefault(base, []).append(value)
    return out


def infobox_edibility(wikitext: str) -> tuple[str | None, list[str]]:
    """Returns (normalised value, raw values) from the howEdible parameters of Mycomorphbox templates."""
    raw = infobox_params(wikitext).get("howEdible", [])
    return worst([normalise(v) for v in raw]), raw


def fetch_wikitext(client: Client, lang: str, title: str) -> str:
    res = client.get_json(
        f"https://{lang}.wikipedia.org/w/api.php",
        {"action": "parse", "page": title, "prop": "wikitext", "redirects": 1, "format": "json", "formatversion": 2},
    )
    return res.get("parse", {}).get("wikitext") or ""


def run(cfg: Config) -> Path:
    species = pd.read_parquet(cfg.species_parquet())
    wiki_cache = cfg.cache_dir / "wiki"
    cache = cfg.cache_dir / "edibility"
    cache.mkdir(parents=True, exist_ok=True)
    client = Client(cfg.user_agent, cfg.wiki_rps)

    def one(row) -> dict:
        path = cache / f"{row.id}.json"
        if path.exists():
            return json.loads(path.read_text())
        result = {"species_id": row.id, "edibility": None, "raw": [], "source": None, "infobox": {}, "revision": None}
        wiki_path = wiki_cache / f"{row.id}.json"
        if wiki_path.exists():
            w = json.loads(wiki_path.read_text())
            en = (w.get("articles") or {}).get("en")
            if en:
                try:
                    params = infobox_params(fetch_wikitext(client, "en", en["title"]))
                    raw = params.get("howEdible", [])
                    value = worst([normalise(v) for v in raw])
                    result.update(
                        edibility=value, raw=raw, source=f"wiki:en:{en.get('revision_id', 0)}" if value else None,
                        infobox={k: v for k, v in params.items() if k in MORPH_PARAMS}, revision=en.get("revision_id", 0),
                    )
                except Exception as e:  # keep going; the species simply has no value
                    result["error"] = str(e)
        path.write_text(json.dumps(result, ensure_ascii=False))
        return result

    with ThreadPoolExecutor(max_workers=12) as pool:
        results = list(tqdm(pool.map(one, list(species.itertuples(index=False))), total=len(species), desc="edibility", unit="sp"))

    poisonous = dict(zip(species["id"], species["poisonous"]))
    rows = []
    for r in results:
        value, source = r["edibility"], r["source"]
        if value is None and poisonous.get(r["species_id"]):
            value, source = "POISONOUS", "fungitastic:poisonous"
        rows.append({"species_id": r["species_id"], "edibility": value, "edibility_source": source})
    df = pd.DataFrame(rows)
    out = cfg.cache_dir / "edibility.parquet"
    df.to_parquet(out, index=False)
    box_rows = [
        {"species_id": r["species_id"], "revision": r.get("revision") or 0, **{k: " / ".join(v) for k, v in (r.get("infobox") or {}).items()}}
        for r in results if r.get("infobox")
    ]
    pd.DataFrame(box_rows).to_parquet(cfg.cache_dir / "infobox.parquet", index=False)
    print(df["edibility"].value_counts(dropna=False).to_string())
    print(f"infobox morphology for {len(box_rows)} species")
    return out
