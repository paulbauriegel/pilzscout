"""Step 3: Wikipedia (DE/EN) article extracts, sections, thumbnails and common names via Wikidata.

Per species the work is cached in cache/wiki/<species_id>.json so reruns only fetch what is missing.
Outputs: cache/wiki_articles.parquet, cache/common_names.json, cache/build/wiki/thumbs/*.webp
"""
from __future__ import annotations

import hashlib
import io
import json
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote

import mwparserfromhell
import pandas as pd
from PIL import Image
from tqdm import tqdm

from .config import Config
from .http import Client

WIKIDATA_API = "https://www.wikidata.org/w/api.php"
LICENSE = "CC BY-SA 4.0"

SECTION_PATTERNS = {
    "morphology": re.compile(r"^(Merkmale|Beschreibung|Makroskopische|Mikroskopische|Description|Morphology|Characteristics|Identification)", re.I),
    "habitat": re.compile(r"^(Ökologie|Vorkommen|Verbreitung|Lebensweise|Standort|Ecology|Habitat|Distribution|Range)", re.I),
    "similar": re.compile(r"^(Verwechslung|Ähnliche Arten|Artabgrenzung|Similar species|Look-?alikes|Similar)", re.I),
    "toxicity": re.compile(r"^(Giftigkeit|Toxizität|Speisewert|Bedeutung|Toxicity|Edibility|Uses)", re.I),
}
MAX_SECTION_CHARS = 2500
THUMB_EDGE = 300


def wikidata_lookup(client: Client, binomial: str) -> dict | None:
    res = client.get_json(
        WIKIDATA_API,
        {"action": "query", "list": "search", "srsearch": f"haswbstatement:P225={binomial}", "srlimit": 1, "format": "json"},
    )
    hits = res.get("query", {}).get("search", [])
    if not hits:
        # fallback: plain label search restricted to taxa is noisy; try exact label search
        res = client.get_json(
            WIKIDATA_API,
            {"action": "wbsearchentities", "search": binomial, "language": "en", "type": "item", "limit": 3, "format": "json"},
        )
        cands = [c for c in res.get("search", []) if (c.get("label") or "").lower() == binomial.lower()]
        if not cands:
            return None
        qid = cands[0]["id"]
    else:
        qid = hits[0]["title"]
    ent = client.get_json(
        WIKIDATA_API,
        {"action": "wbgetentities", "ids": qid, "props": "sitelinks|labels|claims", "sitefilter": "dewiki|enwiki", "languages": "de|en", "format": "json"},
    )
    e = ent.get("entities", {}).get(qid, {})
    sitelinks = e.get("sitelinks", {})
    common: dict[str, list[str]] = {"de": [], "en": []}
    for claim in e.get("claims", {}).get("P1843", []):
        v = claim.get("mainsnak", {}).get("datavalue", {}).get("value", {})
        lang, text = v.get("language"), v.get("text")
        if lang in common and text and text not in common[lang]:
            # preferred rank first
            if claim.get("rank") == "preferred":
                common[lang].insert(0, text)
            else:
                common[lang].append(text)
    return {
        "qid": qid,
        "dewiki": sitelinks.get("dewiki", {}).get("title"),
        "enwiki": sitelinks.get("enwiki", {}).get("title"),
        "labels": {k: v.get("value") for k, v in e.get("labels", {}).items()},
        "common": common,
    }


def fetch_summary(client: Client, lang: str, title: str) -> dict | None:
    try:
        s = client.get_json(f"https://{lang}.wikipedia.org/api/rest_v1/page/summary/{quote(title, safe='')}")
    except Exception:
        return None
    if s.get("type") == "disambiguation":
        return None
    return {
        "title": s.get("title") or title,
        "url": s.get("content_urls", {}).get("desktop", {}).get("page") or f"https://{lang}.wikipedia.org/wiki/{quote(title)}",
        "revision_id": int(s.get("revision") or 0),
        "summary": s.get("extract") or "",
        "description": s.get("description"),
        "thumbnail": (s.get("thumbnail") or {}).get("source"),
    }


def fetch_sections(client: Client, lang: str, title: str) -> dict[str, str]:
    try:
        res = client.get_json(
            f"https://{lang}.wikipedia.org/w/api.php",
            {"action": "parse", "page": title, "prop": "wikitext", "redirects": 1, "format": "json", "formatversion": 2},
        )
    except Exception:
        return {}
    wikitext = res.get("parse", {}).get("wikitext") or ""
    code = mwparserfromhell.parse(wikitext)
    # Drop file/image links, galleries and reference tags before rendering to text.
    for link in code.filter_wikilinks():
        if re.match(r"^\s*(File|Image|Datei|Bild|Media):", str(link.title), re.I):
            try:
                code.remove(link)
            except ValueError:
                pass
    for tag in code.filter_tags():
        if str(tag.tag).lower() in ("gallery", "ref", "imagemap", "timeline"):
            try:
                code.remove(tag)
            except ValueError:
                pass
    out: dict[str, str] = {}
    for section in code.get_sections(include_lead=False, flat=True):
        headings = section.filter_headings()
        if not headings:
            continue
        heading = headings[0].title.strip_code().strip()
        for key, pattern in SECTION_PATTERNS.items():
            if pattern.match(heading) and key not in out:
                # remove the heading, references, templates, and collapse whitespace
                body = section.strip_code(normalize=True, collapse=True)
                body = body.replace(heading, "", 1).strip()
                body = re.sub(r"(?m)^\s*(mini|thumb|hochkant|upright|left|right|links|rechts)\|[^\n]*$", "", body)
                body = re.sub(r"\b(mini|thumb)\|[^\n.]*", "", body)
                body = re.sub(r"\n{3,}", "\n\n", body)
                body = re.sub(r"[ \t]+", " ", body).strip()
                if body:
                    out[key] = body[:MAX_SECTION_CHARS]
    return out


def save_thumb(client: Client, url: str, out_dir: Path) -> str | None:
    name = hashlib.sha1(url.encode()).hexdigest()[:16] + ".webp"
    path = out_dir / name
    if not path.exists():
        try:
            data = client.get_bytes(url)
            img = Image.open(io.BytesIO(data)).convert("RGB")
            img.thumbnail((THUMB_EDGE, THUMB_EDGE))
            out_dir.mkdir(parents=True, exist_ok=True)
            img.save(path, "WEBP", quality=75, method=4)
        except Exception:
            return None
    return f"wiki/thumbs/{name}"


def process_species(client: Client, species_id: str, binomial: str, langs: list[str], thumb_dir: Path) -> dict:
    wd = wikidata_lookup(client, binomial)
    result: dict = {"species_id": species_id, "binomial": binomial, "wikidata": wd, "articles": {}}
    if not wd:
        return result
    for lang in langs:
        title = wd.get(f"{lang}wiki")
        if not title:
            continue
        summary = fetch_summary(client, lang, title)
        if not summary:
            continue
        sections = fetch_sections(client, lang, title)
        thumb = save_thumb(client, summary["thumbnail"], thumb_dir) if summary.get("thumbnail") else None
        result["articles"][lang] = {**summary, "sections": sections, "thumb_file": thumb, "retrieved_at": datetime.now(timezone.utc).isoformat(timespec="seconds")}
    return result


def run(cfg: Config, langs: list[str], limit: int | None = None) -> None:
    species = pd.read_parquet(cfg.species_parquet())
    client = Client(cfg.user_agent, cfg.wiki_rps)
    cache = cfg.cache_dir / "wiki"
    cache.mkdir(parents=True, exist_ok=True)
    thumb_dir = cfg.cache_dir / "build" / "wiki" / "thumbs"
    rows = list(species.itertuples(index=False))
    if limit:
        rows = rows[:limit]

    def one(row) -> dict:
        path = cache / f"{row.id}.json"
        if path.exists():
            return json.loads(path.read_text())
        res = None
        for attempt in range(3):
            try:
                res = process_species(client, row.id, row.binomial, langs, thumb_dir)
                break
            except Exception as e:  # network hiccup: back off and retry
                if attempt == 2:
                    print(f"\nfailed {row.binomial}: {e}")
                    res = {"species_id": row.id, "binomial": row.binomial, "wikidata": None, "articles": {}, "error": str(e)}
                time.sleep(5)
        path.write_text(json.dumps(res, ensure_ascii=False))
        return res

    from concurrent.futures import ThreadPoolExecutor

    with ThreadPoolExecutor(max_workers=12) as pool:
        results = list(tqdm(pool.map(one, rows), total=len(rows), desc="wiki", unit="sp"))
    # keep species order for deterministic outputs
    write_outputs(cfg, results)


def write_outputs(cfg: Config, results: list[dict]) -> None:
    articles = []
    common: dict[str, dict] = {}
    for r in results:
        sid = r["species_id"]
        wd = r.get("wikidata") or {}
        names = {"de": None, "en": None, "de_all": [], "en_all": []}
        for lang in ("de", "en"):
            cands = list((wd.get("common") or {}).get(lang, []))
            article_title = (r.get("articles", {}).get(lang) or {}).get("title")
            # A Wikipedia title that is not the binomial is a common name too (German articles use it as title).
            if article_title and article_title.lower() != r["binomial"].lower() and not re.match(r"^[A-Z][a-z]+ [a-z-]+$", article_title):
                if article_title not in cands:
                    cands.insert(0, article_title)
            label = (wd.get("labels") or {}).get(lang)
            if label and label.lower() != r["binomial"].lower() and not re.match(r"^[A-Z][a-z]+ [a-z-]+$", label) and label not in cands:
                cands.append(label)
            names[lang] = cands[0] if cands else None
            names[f"{lang}_all"] = cands
        if names["de"] or names["en"]:
            common[sid] = names
        for lang, a in r.get("articles", {}).items():
            articles.append(
                {
                    "species_id": sid, "lang": lang, "title": a["title"], "url": a["url"], "revision_id": a.get("revision_id") or 0,
                    "summary": a.get("summary") or "", "description": a.get("description"), "thumb_file": a.get("thumb_file"),
                    "sections_json": json.dumps(a.get("sections") or {}, ensure_ascii=False), "license": LICENSE,
                    "retrieved_at": a.get("retrieved_at") or "",
                }
            )
    pd.DataFrame(articles).to_parquet(cfg.cache_dir / "wiki_articles.parquet", index=False)
    (cfg.cache_dir / "common_names.json").write_text(json.dumps(common, ensure_ascii=False, indent=0))
    n_de = sum(1 for a in articles if a["lang"] == "de")
    n_en = sum(1 for a in articles if a["lang"] == "en")
    print(f"articles: de={n_de} en={n_en}; species with common names: {len(common)}")
