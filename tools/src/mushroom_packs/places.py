"""Step 8: offline gazetteer for approximate place names (GeoNames, CC BY 4.0).

Input: cache/geonames/DE.txt (+ admin1CodesASCII.txt). Populated places with a population, plus all
administrative seats, are kept; the app resolves a coordinate to the nearest entry.
Output: cache/places.parquet (id, name, admin1, lat, lon, population, kind)
"""
from __future__ import annotations

from pathlib import Path

import pandas as pd

from .config import Config

COLS = [
    "geonameid", "name", "asciiname", "alternatenames", "lat", "lon", "fclass", "fcode", "cc", "cc2",
    "admin1", "admin2", "admin3", "admin4", "population", "elevation", "dem", "tz", "moddate",
]
KEEP_CODES = {"PPL", "PPLA", "PPLA2", "PPLA3", "PPLA4", "PPLC", "PPLX", "PPLF", "PPLL"}
MIN_POPULATION = 300
# GeoNames' primary name is the English exonym for a few cities; the app is German-first.
GERMAN_NAMES = {"Munich": "München", "Nuremberg": "Nürnberg", "Cologne": "Köln", "Hanover": "Hannover", "Brunswick": "Braunschweig", "Constance": "Konstanz", "Coblenz": "Koblenz", "Ratisbon": "Regensburg"}


def run(cfg: Config, country: str = "DE") -> Path:
    src = cfg.cache_dir / "geonames" / f"{country}.txt"
    if not src.exists():
        raise SystemExit(f"{src} missing: download https://download.geonames.org/export/dump/{country}.zip and unzip it there")
    df = pd.read_csv(src, sep="\t", names=COLS, header=None, dtype=str, keep_default_na=False, quoting=3)
    df = df[df["fclass"] == "P"]
    df["population"] = pd.to_numeric(df["population"], errors="coerce").fillna(0).astype(int)
    seats = df["fcode"].isin({"PPLA", "PPLA2", "PPLA3", "PPLC"})
    df = df[df["fcode"].isin(KEEP_CODES) & ((df["population"] >= MIN_POPULATION) | seats)]
    admin_path = cfg.cache_dir / "geonames" / "admin1CodesASCII.txt"
    admin = {}
    if admin_path.exists():
        for line in admin_path.read_text(encoding="utf-8").splitlines():
            parts = line.split("\t")
            if len(parts) >= 2 and parts[0].startswith(f"{country}."):
                admin[parts[0].split(".", 1)[1]] = parts[1]
    out = pd.DataFrame(
        {
            "id": df["geonameid"].astype(int),
            "name": df["name"].map(lambda n: GERMAN_NAMES.get(n, n)),
            "admin1": df["admin1"].map(admin).fillna(df["admin1"]),
            "lat": df["lat"].astype(float),
            "lon": df["lon"].astype(float),
            "population": df["population"],
            "kind": df["fcode"],
        }
    ).sort_values(["lat", "lon"])
    path = cfg.cache_dir / "places.parquet"
    out.to_parquet(path, index=False)
    print(f"{len(out)} places kept ({country}); e.g. {out.sort_values('population', ascending=False).head(3).name.tolist()}")
    return path
