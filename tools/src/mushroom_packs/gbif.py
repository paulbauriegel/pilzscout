"""Step 2: GBIF backbone key + German occurrence count per species (cached per species)."""
from __future__ import annotations

import pandas as pd
from tqdm import tqdm

from .config import Config
from .http import Client, cached_json

GBIF = "https://api.gbif.org/v1"


def lookup(client: Client, binomial: str) -> dict:
    match = client.get_json(f"{GBIF}/species/match", {"name": binomial, "kingdom": "Fungi", "strict": "false"})
    key = match.get("usageKey")
    result = {
        "binomial": binomial,
        "gbif_key": key,
        "match_type": match.get("matchType"),
        "confidence": match.get("confidence"),
        "matched_name": match.get("canonicalName") or match.get("scientificName"),
        "de_occurrences": 0,
    }
    if key and match.get("matchType") not in (None, "NONE"):
        count = client.get_json(f"{GBIF}/occurrence/count", {"taxonKey": key, "country": "DE"})
        result["de_occurrences"] = int(count)
    return result


def run(cfg: Config, limit: int | None = None) -> pd.DataFrame:
    species = pd.read_parquet(cfg.species_parquet())
    client = Client(cfg.user_agent, cfg.gbif_rps)
    rows = []
    it = species.itertuples(index=False)
    if limit:
        it = list(it)[:limit]
    for row in tqdm(it, total=limit or len(species), desc="gbif", unit="sp"):
        path = cfg.cache_dir / "gbif" / f"{row.id}.json"
        rows.append(cached_json(path, lambda: lookup(client, row.binomial)))
    df = pd.DataFrame(rows)
    # A HIGHERRANK match means GBIF only knows the genus; the count would describe the genus, not the species.
    higher = df["match_type"] == "HIGHERRANK"
    df.loc[higher, "gbif_key"] = None
    df.loc[higher, "de_occurrences"] = 0
    df["in_germany"] = (df["de_occurrences"] >= cfg.gbif_min_de_occurrences).astype(int)
    out = cfg.cache_dir / "gbif.parquet"
    df.to_parquet(out, index=False)
    return df
