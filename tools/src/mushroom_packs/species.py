"""Step 1: species base table from the training run's classes.json + labels.txt + FungiTastic taxonomy."""
from __future__ import annotations

import json
import re
from pathlib import Path

import pandas as pd

from .config import Config

_SLUG_RE = re.compile(r"[^a-z0-9]+")


def slugify(binomial: str) -> str:
    return _SLUG_RE.sub("-", binomial.strip().lower()).strip("-")


def load_classes(cfg: Config) -> pd.DataFrame:
    classes = json.loads(cfg.classes_json.read_text())
    labels = (cfg.export_dir / "labels.txt").read_text().splitlines()
    if len(labels) != len(classes):
        raise SystemExit(f"labels.txt has {len(labels)} lines but classes.json has {len(classes)} classes")
    rows = []
    for i, c in enumerate(classes):
        if c["label"] != i or c["name"] != labels[i]:
            raise SystemExit(f"class index mismatch at {i}: {c} vs label {labels[i]!r}")
        rows.append(
            {
                "id": slugify(c["name"]),
                "binomial": c["name"],
                "scientific_name": c.get("scientificName") or c["name"],
                "genus": c.get("genus") or c["name"].split()[0],
                "specific_epithet": c.get("specificEpithet") or " ".join(c["name"].split()[1:]),
                "family": c.get("family"),
                "poisonous": int(bool(c.get("poisonous", 0))),
                "model_class_index": i,
                "category_ids": ",".join(str(x) for x in (c.get("category_ids") or [c.get("category_id")])),
            }
        )
    df = pd.DataFrame(rows)
    if df["id"].duplicated().any():
        dupes = df[df["id"].duplicated(keep=False)]["binomial"].tolist()
        raise SystemExit(f"duplicate species ids: {dupes}")
    return df


def load_taxonomy(cfg: Config, train_csv: Path | None = None) -> pd.DataFrame:
    """order/class/phylum per category_id from the FungiTastic train CSV (streamed in chunks)."""
    path = train_csv or (cfg.metadata_dir / "FungiTastic-Train.csv")
    seen: dict[int, dict] = {}
    for chunk in pd.read_csv(path, usecols=["category_id", "order", "class", "phylum"], chunksize=200_000):
        chunk = chunk.drop_duplicates("category_id")
        for rec in chunk.to_dict("records"):
            cid = int(rec["category_id"])
            if cid not in seen:
                seen[cid] = {"order_name": rec["order"], "class_name": rec["class"], "phylum": rec["phylum"]}
    return pd.DataFrame.from_dict(seen, orient="index")


def build_species(cfg: Config) -> pd.DataFrame:
    df = load_classes(cfg)
    tax = load_taxonomy(cfg)
    first_cid = df["category_ids"].str.split(",").str[0].astype(int)
    df["order_name"] = first_cid.map(tax["order_name"])
    df["class_name"] = first_cid.map(tax["class_name"])
    df["phylum"] = first_cid.map(tax["phylum"])
    out = cfg.species_parquet()
    out.parent.mkdir(parents=True, exist_ok=True)
    df.to_parquet(out, index=False)
    return df
