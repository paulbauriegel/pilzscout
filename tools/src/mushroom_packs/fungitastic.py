"""Step 4: FungiTastic aggregates, sampled observations and thumbnails.

Streams the (large) metadata CSVs once, keeps per-species statistics and up to CANDIDATES observations
per species, then picks the observations to ship (DNA-verified first, then captioned ones with several
photos) and renders 256 px WebP thumbnails from the 500p images.
Outputs: cache/species_stats.parquet, cache/ft_observations.parquet, cache/ft_photos.parquet,
         cache/build/fungitastic/thumbs/*.webp (and images-hd/ when --hd)
"""
from __future__ import annotations

import json
from collections import Counter, defaultdict
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import pandas as pd
from PIL import Image
from tqdm import tqdm

from .config import Config

# DNA-verified and held-out observations are streamed first so they enter the per-species candidate pool.
SPLITS = [
    ("FungiTastic-DNA-Test.csv", "dna"),
    ("FungiTastic-ClosedSet-Test.csv", "test"),
    ("FungiTastic-ClosedSet-Val.csv", "val"),
    ("FungiTastic-Train.csv", "train"),
]
WANTED = {
    "observationID", "filename", "category_id", "eventDate", "month", "habitat", "substrate", "metaSubstrate", "region",
    "district", "latitude", "longitude", "coorUncert", "elevation", "biogeographicalRegion", "captions", "countryCode",
}
CANDIDATES = 40
OBS_DE, OBS_OTHER = 2, 1
MAX_PHOTOS_PER_OBS = 1
MAX_PHOTOS_PER_SPECIES = 2
THUMB_EDGE, HD_EDGE = 224, 640
THUMB_QUALITY = 60
MAX_CAPTION = 600


def _opt(v):
    return None if v is None or (isinstance(v, float) and v != v) else v


def stream(cfg: Config):
    for name, split in SPLITS:
        path = cfg.metadata_dir / name
        if not path.exists():
            print(f"WARNING: {path} missing")
            continue
        for chunk in pd.read_csv(path, usecols=lambda c: c in WANTED, chunksize=100_000, low_memory=False):
            chunk["split"] = split
            yield chunk


def aggregate(cfg: Config):
    species = pd.read_parquet(cfg.species_parquet())
    cat2sp: dict[int, str] = {}
    for r in species.itertuples(index=False):
        for c in str(r.category_ids).split(","):
            if c:
                cat2sp[int(c)] = r.id
    stats = defaultdict(lambda: {"habitat": Counter(), "substrate": Counter(), "month": [0] * 12, "region": Counter(), "obs": set(), "dna": set(), "counted": set()})
    cands: dict[str, dict[int, dict]] = defaultdict(dict)  # species -> obsID -> {split, dna, rows}
    for chunk in stream(cfg):
        chunk = chunk[chunk["category_id"].isin(cat2sp)]
        for rec in chunk.to_dict("records"):
            sid = cat2sp[int(rec["category_id"])]
            oid = int(rec["observationID"])
            st = stats[sid]
            is_dna = rec["split"] == "dna"
            st["obs"].add(oid)
            if is_dna:
                st["dna"].add(oid)
            if oid not in st["counted"]:  # count each observation once (DNA rows duplicate test rows)
                st["counted"].add(oid)
                h, s, reg = _opt(rec.get("habitat")), _opt(rec.get("substrate")), _opt(rec.get("region"))
                if h:
                    st["habitat"][h] += 1
                if s:
                    st["substrate"][s] += 1
                if reg:
                    st["region"][reg] += 1
                m = _opt(rec.get("month"))
                if m and 1 <= int(m) <= 12:
                    st["month"][int(m) - 1] += 1
            c = cands[sid]
            if oid in c:
                c[oid]["dna"] = c[oid]["dna"] or is_dna
                if is_dna:
                    c[oid]["split"] = "dna"
                if len(c[oid]["rows"]) < MAX_PHOTOS_PER_OBS and rec["filename"] not in {x["filename"] for x in c[oid]["rows"]}:
                    c[oid]["rows"].append(rec)
            elif len(c) < CANDIDATES:
                c[oid] = {"split": rec["split"], "dna": is_dna, "rows": [rec]}
    return species, stats, cands


def choose(cfg: Config, species, stats, cands):
    gbif_path = cfg.cache_dir / "gbif.parquet"
    in_de = {}
    if gbif_path.exists():
        g = pd.read_parquet(gbif_path)
        sp_by_binomial = dict(zip(species["binomial"], species["id"]))
        in_de = {sp_by_binomial.get(b): bool(v) for b, v in zip(g["binomial"], g["in_germany"])}
    obs_rows, photo_rows = [], []
    for sid, c in cands.items():
        n_take = OBS_DE if in_de.get(sid, True) else OBS_OTHER
        ranked = sorted(
            c.items(),
            key=lambda kv: (not kv[1]["dna"], not any(_opt(r.get("captions")) for r in kv[1]["rows"]), -len(kv[1]["rows"]), kv[0]),
        )
        photos = 0
        for oid, o in ranked[:n_take]:
            first = o["rows"][0]
            obs_rows.append(
                {
                    "observation_id": oid, "species_id": sid, "event_date": _opt(first.get("eventDate")),
                    "month": int(first["month"]) if _opt(first.get("month")) else None,
                    "region": _opt(first.get("region")), "district": _opt(first.get("district")), "habitat": _opt(first.get("habitat")),
                    "substrate": _opt(first.get("substrate")), "meta_substrate": _opt(first.get("metaSubstrate")),
                    "lat": round(float(first["latitude"]), 1) if _opt(first.get("latitude")) is not None else None,
                    "lon": round(float(first["longitude"]), 1) if _opt(first.get("longitude")) is not None else None,
                    "coord_uncert": float(first["coorUncert"]) if _opt(first.get("coorUncert")) is not None else None,
                    "split": o["split"], "dna_sequenced": int(o["dna"]), "biogeo_region": _opt(first.get("biogeographicalRegion")),
                    "elevation": float(first["elevation"]) if _opt(first.get("elevation")) is not None else None,
                }
            )
            for r in o["rows"]:
                if photos >= MAX_PHOTOS_PER_SPECIES:
                    break
                cap = _opt(r.get("captions"))
                photo_rows.append(
                    {
                        "filename": r["filename"], "observation_id": oid, "species_id": sid, "split": r["split"] if r["split"] != "dna" else "test",
                        "caption": (str(cap).strip()[:MAX_CAPTION] if cap else None), "has_mask": 0, "thumb_file": None, "hd_file": None,
                    }
                )
                photos += 1
    stats_rows = []
    for sid, st in stats.items():
        stats_rows.append(
            {
                "species_id": sid,
                "habitats_json": json.dumps(st["habitat"].most_common(5), ensure_ascii=False),
                "substrates_json": json.dumps(st["substrate"].most_common(5), ensure_ascii=False),
                "month_hist_json": json.dumps(st["month"]),
                "regions_json": json.dumps(st["region"].most_common(5), ensure_ascii=False),
                "n_obs": len(st["obs"]), "n_dna": len(st["dna"]),
            }
        )
    return pd.DataFrame(stats_rows), pd.DataFrame(obs_rows), pd.DataFrame(photo_rows)


def _find_image(images_dir: Path, split: str, filename: str) -> Path | None:
    for s in (split, "test", "val", "train"):
        p = images_dir / s / "500p" / filename
        if p.exists():
            return p
    return None


def _render(args) -> tuple[str, str | None, str | None]:
    src, thumb_path, hd_path = args
    try:
        img = Image.open(src).convert("RGB")
    except Exception:
        return (str(src), None, None)
    out_t = out_h = None
    if not thumb_path.exists():
        t = img.copy()
        t.thumbnail((THUMB_EDGE, THUMB_EDGE))
        thumb_path.parent.mkdir(parents=True, exist_ok=True)
        t.save(thumb_path, "WEBP", quality=THUMB_QUALITY, method=4)
    out_t = thumb_path.name
    if hd_path is not None:
        if not hd_path.exists():
            h = img.copy()
            h.thumbnail((HD_EDGE, HD_EDGE))
            hd_path.parent.mkdir(parents=True, exist_ok=True)
            h.save(hd_path, "WEBP", quality=80, method=4)
        out_h = hd_path.name
    return (str(src), out_t, out_h)


def run(cfg: Config, hd: bool = False, masks: str | None = None) -> None:
    species, stats, cands = aggregate(cfg)
    stats_df, obs_df, photos_df = choose(cfg, species, stats, cands)
    print(f"stats for {len(stats_df)} species, {len(obs_df)} observations, {len(photos_df)} photos selected")

    thumb_dir = cfg.cache_dir / "build" / "fungitastic" / "thumbs"
    hd_dir = cfg.cache_dir / "build" / "images-hd"
    jobs, index = [], []
    for i, r in enumerate(photos_df.itertuples(index=False)):
        src = _find_image(cfg.images_dir, r.split, r.filename)
        if src is None:
            continue
        stem = Path(r.filename).stem
        jobs.append((src, thumb_dir / f"{stem}.webp", (hd_dir / f"{stem}.webp") if hd else None))
        index.append(i)
    thumbs = [None] * len(photos_df)
    hds = [None] * len(photos_df)
    with ProcessPoolExecutor() as pool:
        for i, (_, t, h) in zip(index, tqdm(pool.map(_render, jobs, chunksize=32), total=len(jobs), desc="thumbs", unit="img")):
            thumbs[i] = f"fungitastic/thumbs/{t}" if t else None
            hds[i] = f"images-hd/{h}" if h else None
    photos_df["thumb_file"] = thumbs
    photos_df["hd_file"] = hds
    photos_df = photos_df[photos_df["thumb_file"].notna()]
    obs_df = obs_df[obs_df["observation_id"].isin(set(photos_df["observation_id"]))]

    if masks:
        photos_df = _apply_masks(cfg, photos_df, Path(masks))

    # Remove thumbnails from earlier runs that are no longer referenced, so the pack only ships what the DB knows.
    keep = {Path(t).name for t in photos_df["thumb_file"].dropna()}
    for old in thumb_dir.glob("*.webp"):
        if old.name not in keep:
            old.unlink()
    stats_df.to_parquet(cfg.cache_dir / "species_stats.parquet", index=False)
    # Species-level reference photos only; individual observations and captions are not shipped in the app.
    photos_df[["filename", "species_id", "has_mask", "thumb_file", "hd_file"]].to_parquet(cfg.cache_dir / "ft_photos.parquet", index=False)
    print(f"wrote {len(photos_df)} reference photos with thumbnails for {photos_df.species_id.nunique()} species")


def _apply_masks(cfg: Config, photos_df: pd.DataFrame, parquet_dir: Path) -> pd.DataFrame:
    """Optional: FungiTastic-Mini body-part masks (CVAT RLE) -> 1-bit PNG per photo, sets has_mask."""
    import numpy as np

    files = list(parquet_dir.glob("*Masks.parquet")) if parquet_dir.is_dir() else [parquet_dir]
    if not files:
        return photos_df
    masks = pd.concat(pd.read_parquet(f) for f in files)
    col = "file_name" if "file_name" in masks.columns else "filename"
    wanted = set(photos_df["filename"])
    masks = masks[masks[col].isin(wanted)]
    out_dir = cfg.cache_dir / "build" / "fungitastic" / "masks"
    out_dir.mkdir(parents=True, exist_ok=True)
    done = set()
    for fname, group in masks.groupby(col):
        h, w = int(group.iloc[0]["height"]), int(group.iloc[0]["width"])
        canvas = np.zeros((h, w), dtype=np.uint8)
        for _, m in group.iterrows():
            rle = list(m["rle"])
            counts = rle[:-4] if len(rle) > 4 else rle  # last four values are the bounding box
            mask = np.zeros(h * w, dtype=np.uint8)
            pos, val = 0, 0
            for c in counts:
                c = int(c)
                if val:
                    mask[pos:pos + c] = 1
                pos += c
                val ^= 1
            canvas |= mask.reshape(h, w)
        img = Image.fromarray(canvas * 255).convert("1")
        img.thumbnail((THUMB_EDGE, THUMB_EDGE))
        img.save(out_dir / f"{Path(fname).stem}.png", optimize=True)
        done.add(fname)
    photos_df = photos_df.copy()
    photos_df["has_mask"] = photos_df["filename"].isin(done).astype(int)
    print(f"masks for {len(done)} photos")
    return photos_df
