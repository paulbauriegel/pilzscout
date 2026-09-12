"""Step 6: build core/species.db exactly as Room expects it.

The CREATE statements are taken verbatim from the exported Room schema JSON (app/schemas), so the
prepackaged-database validation that Room runs on first open passes. Missing step outputs (GBIF,
Wikipedia, FungiTastic, traits) are tolerated: the corresponding tables stay empty and the species
rows get conservative defaults.
"""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path

import pandas as pd

from .config import Config
from .textnorm import normalize

SCHEMA_PKG = "de.pilzscout.app.data.species.SpeciesDatabase"


def load_room_schema(cfg: Config) -> dict:
    schema_dir = cfg.schemas_dir / SCHEMA_PKG
    versions = sorted((int(p.stem) for p in schema_dir.glob("*.json")), reverse=True)
    if not versions:
        raise SystemExit(f"no Room schema found under {schema_dir}; run ./gradlew :app:kspDebugKotlin")
    return json.loads((schema_dir / f"{versions[0]}.json").read_text())["database"]


def create_tables(conn: sqlite3.Connection, database: dict) -> None:
    for entity in database["entities"]:
        table = entity["tableName"]
        conn.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            conn.execute(index["createSql"].replace("${TABLE_NAME}", table))
    conn.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
    conn.execute("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)", (database["identityHash"],))


def _optional_parquet(path: Path) -> pd.DataFrame | None:
    return pd.read_parquet(path) if path.exists() else None


def _optional_json(path: Path) -> dict:
    return json.loads(path.read_text()) if path.exists() else {}


def build(cfg: Config, out: Path) -> Path:
    database = load_room_schema(cfg)
    species = pd.read_parquet(cfg.species_parquet())
    gbif = _optional_parquet(cfg.cache_dir / "gbif.parquet")
    wiki = _optional_parquet(cfg.cache_dir / "wiki_articles.parquet")
    common = _optional_json(cfg.cache_dir / "common_names.json")  # species_id -> {"de": ..., "en": ...}
    stats = _optional_parquet(cfg.cache_dir / "species_stats.parquet")
    observations = _optional_parquet(cfg.cache_dir / "ft_observations.parquet")
    photos = _optional_parquet(cfg.cache_dir / "ft_photos.parquet")
    traits = _optional_parquet(cfg.cache_dir / "traits.parquet")
    edibility = _optional_parquet(cfg.cache_dir / "edibility.parquet")
    edibility_by_id = edibility.set_index("species_id").to_dict("index") if edibility is not None else {}

    if gbif is None:
        print("WARNING: cache/gbif.parquet missing - every species is treated as recorded in Germany")
    if wiki is None:
        print("WARNING: no Wikipedia data - wiki_article stays empty")
    if stats is None:
        print("WARNING: no FungiTastic stats - species_stats stays empty")

    gbif_by_id = gbif.set_index(gbif["binomial"].map(dict(zip(species["binomial"], species["id"])))).to_dict("index") if gbif is not None else {}
    wiki_langs: dict[str, set[str]] = {}
    if wiki is not None:
        for row in wiki.itertuples(index=False):
            wiki_langs.setdefault(row.species_id, set()).add(row.lang)
    n_obs = stats.set_index("species_id")["n_obs"].to_dict() if stats is not None else {}

    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()
    conn = sqlite3.connect(out)
    try:
        create_tables(conn, database)
        species_rows = []
        search_rows = []
        for s in species.to_dict("records"):
            g = gbif_by_id.get(s["id"], {})
            names = common.get(s["id"], {})
            langs = wiki_langs.get(s["id"], set())
            ed = edibility_by_id.get(s["id"], {})
            species_rows.append(
                (
                    s["id"], s["scientific_name"], s["binomial"], s["genus"], s["specific_epithet"], s.get("family"),
                    s.get("order_name"), s.get("class_name"), names.get("de"), names.get("en"), int(s["poisonous"]),
                    int(g["gbif_key"]) if g.get("gbif_key") == g.get("gbif_key") and g.get("gbif_key") is not None else None,
                    int(g.get("de_occurrences", 0) or 0),
                    int(g.get("in_germany", 1)) if g else 1,
                    int(s["model_class_index"]), int("de" in langs), int("en" in langs), int(n_obs.get(s["id"], 0)),
                    ed.get("edibility") if ed.get("edibility") == ed.get("edibility") else None,
                    ed.get("edibility_source") if ed.get("edibility_source") == ed.get("edibility_source") else None,
                )
            )
            search_rows.append((s["id"], normalize(s["binomial"]), "scientific"))
            search_rows.append((s["id"], normalize(s["genus"]), "genus"))
            for lang in ("de", "en"):
                for name in names.get(f"{lang}_all", [names[lang]] if names.get(lang) else []):
                    search_rows.append((s["id"], normalize(name), f"common_{lang}"))
        conn.executemany(
            """INSERT INTO species (id, scientific_name, binomial, genus, specific_epithet, family, order_name, class_name,
               common_de, common_en, poisonous, gbif_key, de_occurrences, in_germany, model_class_index, has_wiki_de,
               has_wiki_en, n_observations, edibility, edibility_source) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            species_rows,
        )
        conn.executemany("INSERT INTO species_search (species_id, text_norm, kind) VALUES (?,?,?)", search_rows)

        if traits is not None:
            conn.executemany(
                "INSERT INTO trait (species_id, feature, lang, text, value_key, basis, source) VALUES (?,?,?,?,?,?,?)",
                [(t.species_id, t.feature, t.lang, t.text, t.value_key, t.basis, t.source) for t in traits.itertuples(index=False)],
            )
        if stats is not None:
            conn.executemany(
                "INSERT INTO species_stats (species_id, habitats_json, substrates_json, month_hist_json, regions_json, n_obs, n_dna) VALUES (?,?,?,?,?,?,?)",
                [(r.species_id, r.habitats_json, r.substrates_json, r.month_hist_json, r.regions_json, int(r.n_obs), int(r.n_dna)) for r in stats.itertuples(index=False)],
            )
        if wiki is not None:
            conn.executemany(
                """INSERT INTO wiki_article (species_id, lang, title, url, revision_id, summary, description, thumb_file,
                   sections_json, license, retrieved_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                [
                    (r.species_id, r.lang, r.title, r.url, int(r.revision_id), r.summary, r.description, r.thumb_file,
                     r.sections_json, r.license, r.retrieved_at)
                    for r in wiki.itertuples(index=False)
                ],
            )
        if observations is not None:
            conn.executemany(
                """INSERT INTO fungitastic_observation (observation_id, species_id, event_date, month, region, district, habitat,
                   substrate, meta_substrate, lat, lon, coord_uncert, split, dna_sequenced, biogeo_region, elevation)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                [tuple(None if v != v else v for v in r) for r in observations[[
                    "observation_id", "species_id", "event_date", "month", "region", "district", "habitat", "substrate",
                    "meta_substrate", "lat", "lon", "coord_uncert", "split", "dna_sequenced", "biogeo_region", "elevation",
                ]].itertuples(index=False, name=None)],
            )
        if photos is not None:
            conn.executemany(
                "INSERT INTO fungitastic_photo (filename, observation_id, species_id, caption, has_mask, thumb_file, hd_file) VALUES (?,?,?,?,?,?,?)",
                [tuple(None if v != v else v for v in r) for r in photos[[
                    "filename", "observation_id", "species_id", "caption", "has_mask", "thumb_file", "hd_file",
                ]].itertuples(index=False, name=None)],
            )
        conn.commit()
        conn.execute("VACUUM")
    finally:
        conn.close()
    return out


def run(cfg: Config) -> Path:
    return build(cfg, cfg.cache_dir / "build" / "core" / "species.db")
