"""Step 7: assemble packs/<version>/ with manifest.json and copy it into the app assets."""
from __future__ import annotations

import hashlib
import json
import shutil
from dataclasses import dataclass
from pathlib import Path

from .config import Config

SIZE_BUDGET_MB = {"model": 95, "core": 30, "wiki": 40, "fungitastic": 130, "images-hd": 400}


@dataclass
class Component:
    id: str
    version: str
    required: bool
    bundled: bool
    files: list[tuple[Path, str]]  # (absolute source, manifest-relative path)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def _collect(root: Path, rel_prefix: str) -> list[tuple[Path, str]]:
    if not root.exists():
        return []
    return [(p, f"{rel_prefix}/{p.relative_to(root).as_posix()}") for p in sorted(root.rglob("*")) if p.is_file()]


def model_component(cfg: Config, prefer_fp16: bool) -> Component:
    export = cfg.export_dir
    info = json.loads((export / "export_info.json").read_text())
    fp16 = export / "mushroom_model_fp16.tflite"
    f32 = export / "mushroom_model_dynamic.tflite"
    model = fp16 if prefer_fp16 and fp16.exists() else f32
    precision = "fp16" if model == fp16 else "float32"
    files = [(model, f"model/{model.name}"), (export / "labels.txt", "model/labels.txt")]
    model_meta = {
        "modelFile": model.name,
        "precision": precision,
        "name": info.get("model"),
        "checkpoint": info.get("checkpoint"),
        "inputSize": info.get("img_size"),
        "numClasses": info.get("num_classes"),
        "inputLayout": info.get("input_layout"),
        "output": info.get("output"),
        "evalResize": 366,
        "runName": cfg.run_name,
    }
    meta_path = cfg.cache_dir / "build" / "model" / "model.json"
    meta_path.parent.mkdir(parents=True, exist_ok=True)
    meta_path.write_text(json.dumps(model_meta, indent=2))
    files.append((meta_path, "model/model.json"))
    vectors = cfg.cache_dir / "build" / "model" / "test_vectors.json"
    if vectors.exists():
        files.append((vectors, "model/test_vectors.json"))
    return Component("model", f"{cfg.run_name}+{precision}", required=True, bundled=True, files=files)


def run(cfg: Config, version: str, copy_to_assets: bool, prefer_fp16: bool) -> Path:
    build = cfg.cache_dir / "build"
    components = [
        model_component(cfg, prefer_fp16),
        Component("core", version, True, True, [(build / "core" / "species.db", "core/species.db")]),
        Component("wiki", version, False, True, _collect(build / "wiki", "wiki")),
        Component("fungitastic", version, False, True, _collect(build / "fungitastic", "fungitastic")),
        Component("images-hd", version, False, False, _collect(build / "images-hd", "images-hd")),
    ]
    out = cfg.out_dir / version
    if out.exists():
        shutil.rmtree(out)
    manifest = {"packVersion": version, "region": "DE", "schemaVersion": _schema_version(cfg), "components": []}
    for c in components:
        entries = []
        total = 0
        for src, rel in c.files:
            if not src.exists():
                raise SystemExit(f"missing file for component {c.id}: {src}")
            dst = out / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)
            size = src.stat().st_size
            total += size
            entries.append({"path": rel, "sha256": sha256(src), "bytes": size})
        budget = SIZE_BUDGET_MB.get(c.id)
        if budget and total > budget * 1e6:
            raise SystemExit(f"component {c.id} is {total / 1e6:.1f} MB, over the {budget} MB budget")
        manifest["components"].append(
            {"id": c.id, "version": c.version, "bytes": total, "required": c.required, "bundled": c.bundled and bool(entries), "files": entries}
        )
        print(f"{c.id:12s} {total / 1e6:8.1f} MB  {len(entries)} files  bundled={c.bundled and bool(entries)}")
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2))
    if copy_to_assets:
        if cfg.assets_dir.exists():
            shutil.rmtree(cfg.assets_dir)
        cfg.assets_dir.parent.mkdir(parents=True, exist_ok=True)
        # images-hd is never bundled into the APK.
        shutil.copytree(out, cfg.assets_dir, ignore=shutil.ignore_patterns("images-hd"))
        print(f"copied to {cfg.assets_dir}")
    return out


def _schema_version(cfg: Config) -> int:
    from .sqlite import load_room_schema

    return int(load_room_schema(cfg)["version"])
