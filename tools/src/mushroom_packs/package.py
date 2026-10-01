"""Step 7: assemble packs/<version>/ with manifest.json and copy it into the app assets.

The output is deterministic: given the same cached inputs, the files, the tar archives and manifest.json
are byte-identical between runs (sorted entries, zeroed tar metadata, no timestamps). `packs publish`
relies on that to detect an already published version.
"""
from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import tarfile
from dataclasses import dataclass, field
from pathlib import Path

from .config import REPO_DIR, Config

SIZE_BUDGET_MB = {"model": 95, "core": 30, "wiki": 40, "fungitastic": 130, "images-hd": 400}

# Manifest layout version. 1: per-file lists only (assets). 2: adds per-component `archive`, `license`
# and the `build` block. The app ignores fields it does not know, so 2 stays readable by older apps.
FORMAT_VERSION = 2

# Components with many small files are additionally packed into one uncompressed tar (WebP is already
# compressed) so a remote install is one download instead of thousands of requests.
ARCHIVED_COMPONENTS = {"wiki", "fungitastic", "images-hd"}

# Licence of each component as shipped. See docs/remote-packs.md ("Licences") before publishing publicly.
LICENSES = {
    "model": {
        "license": "DINOv3-License AND CC-BY-NC-SA-4.0",
        "attribution": "Built with DINOv3 (Meta, DINOv3 License). Fine-tuned on FungiTastic (Picek et al.; "
        "Atlas of Danish Fungi), CC BY-NC-SA 4.0, non-commercial use only.",
    },
    "core": {
        "license": "CC-BY-SA-4.0 AND CC-BY-NC-SA-4.0 AND CC-BY-4.0",
        "attribution": "Wikipedia extracts (CC BY-SA 4.0, per-article revision references); FungiTastic "
        "aggregates and captions (CC BY-NC-SA 4.0); GeoNames place names (CC BY 4.0); GBIF occurrence counts.",
    },
    "wiki": {
        "license": "CC-BY-SA-4.0",
        "attribution": "Wikipedia/Wikimedia Commons article thumbnails. Individual Commons files carry their "
        "own licences (mostly CC BY-SA, CC BY or public domain).",
    },
    "fungitastic": {
        "license": "CC-BY-NC-SA-4.0",
        "attribution": "FungiTastic (Picek et al.), photographs from the Atlas of Danish Fungi, CC BY-NC-SA 4.0, "
        "non-commercial use only.",
    },
    "images-hd": {
        "license": "CC-BY-NC-SA-4.0",
        "attribution": "FungiTastic (Picek et al.), photographs from the Atlas of Danish Fungi, CC BY-NC-SA 4.0, "
        "non-commercial use only.",
    },
}


@dataclass
class Component:
    id: str
    version: str
    required: bool
    bundled: bool
    files: list[tuple[Path, str]]  # (absolute source, manifest-relative path)
    archived: bool = field(default=False)


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


def write_tar(out: Path, files: list[tuple[Path, str]]) -> None:
    """Uncompressed ustar archive with manifest-relative names and zeroed metadata (reproducible)."""
    out.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(out, "w", format=tarfile.USTAR_FORMAT) as tar:
        for src, rel in sorted(files, key=lambda f: f[1]):
            info = tarfile.TarInfo(rel)
            info.size = src.stat().st_size
            info.mtime = 0
            info.mode = 0o644
            info.uid = info.gid = 0
            info.uname = info.gname = ""
            with src.open("rb") as f:
                tar.addfile(info, f)


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
        # Short edge the app resizes to before the centre crop; written by the training export since
        # full_vits16_v4_384 (384 / 0.875). Older export_info.json files came from the 320 px runs.
        "evalResize": int(info.get("eval_resize") or round(int(info["img_size"]) / 0.875)),
        # the export calibrated LayerNorm/softmax for fp16 activations -> the app runs the GPU in fp16 (fp32 accumulate)
        "gpuFp16Safe": bool(info.get("fp16_safe", False)),
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


def build_info(cfg: Config) -> dict:
    """Where the pack came from. Deterministic for a given commit and inputs (no timestamps)."""
    def git(*args: str) -> str:
        try:
            return subprocess.run(["git", *args], cwd=REPO_DIR, capture_output=True, text=True, check=True).stdout.strip()
        except (OSError, subprocess.CalledProcessError):
            return ""

    commit = git("rev-parse", "HEAD")
    dirty = bool(git("status", "--porcelain", "--", "tools", "app/schemas"))
    return {
        "toolsCommit": commit or None,
        "toolsDirty": dirty,
        "trainingRun": cfg.run_name,
        "preparedDataset": cfg.prepared_dir_name,
    }


def components(cfg: Config, version: str, prefer_fp16: bool) -> list[Component]:
    build = cfg.cache_dir / "build"
    return [
        model_component(cfg, prefer_fp16),
        Component("core", version, True, True, [(build / "core" / "species.db", "core/species.db")]),
        Component("wiki", version, False, True, _collect(build / "wiki", "wiki"), archived=True),
        Component("fungitastic", version, False, True, _collect(build / "fungitastic", "fungitastic"), archived=True),
        Component("images-hd", version, False, False, _collect(build / "images-hd", "images-hd"), archived=True),
    ]


def run(cfg: Config, version: str, copy_to_assets: bool, prefer_fp16: bool) -> Path:
    out = cfg.out_dir / version
    if out.exists():
        shutil.rmtree(out)
    manifest = {
        "packVersion": version,
        "region": "DE",
        "schemaVersion": _schema_version(cfg),
        "formatVersion": FORMAT_VERSION,
        "build": build_info(cfg),
        "components": [],
    }
    for c in components(cfg, version, prefer_fp16):
        entries = []
        total = 0
        for src, rel in c.files:
            if not src.exists():
                raise SystemExit(f"missing file for component {c.id}: {src}")
            dst = out / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dst)
            size = src.stat().st_size
            total += size
            entries.append({"path": rel, "sha256": sha256(src), "bytes": size})
        budget = SIZE_BUDGET_MB.get(c.id)
        if budget and total > budget * 1e6:
            raise SystemExit(f"component {c.id} is {total / 1e6:.1f} MB, over the {budget} MB budget")
        component = {
            "id": c.id,
            "version": c.version,
            "bytes": total,
            "required": c.required,
            "bundled": c.bundled and bool(entries),
            **LICENSES[c.id],
            "files": entries,
        }
        if c.archived and entries:
            tar_path = out / f"{c.id}.tar"
            write_tar(tar_path, [(out / e["path"], e["path"]) for e in entries])
            component["archive"] = {"path": tar_path.name, "sha256": sha256(tar_path), "bytes": tar_path.stat().st_size, "format": "tar"}
        manifest["components"].append(component)
        print(f"{c.id:12s} {total / 1e6:8.1f} MB  {len(entries)} files  bundled={c.bundled and bool(entries)}"
              + ("  +tar" if "archive" in component else ""))
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    if copy_to_assets:
        if cfg.assets_dir.exists():
            shutil.rmtree(cfg.assets_dir)
        cfg.assets_dir.parent.mkdir(parents=True, exist_ok=True)
        # images-hd is never bundled into the APK, and the archives are only for remote installs.
        shutil.copytree(out, cfg.assets_dir, ignore=shutil.ignore_patterns("images-hd", "*.tar"))
        print(f"copied to {cfg.assets_dir}")
    return out


def verify(pack_dir: Path) -> dict:
    """Checks every file and archive listed in pack_dir/manifest.json against its size and SHA-256."""
    manifest = json.loads((pack_dir / "manifest.json").read_text())
    problems = []
    for c in manifest["components"]:
        listed = {e["path"]: e for e in c["files"]}
        for e in c["files"]:
            p = pack_dir / e["path"]
            if not p.exists() or p.stat().st_size != e["bytes"] or sha256(p) != e["sha256"]:
                problems.append(e["path"])
        a = c.get("archive")
        if a:
            p = pack_dir / a["path"]
            if not p.exists() or p.stat().st_size != a["bytes"] or sha256(p) != a["sha256"]:
                problems.append(a["path"])
            else:
                with tarfile.open(p) as tar:
                    names = tar.getnames()
                if sorted(names) != sorted(listed):
                    problems.append(f"{a['path']} (entries differ from files[])")
    if problems:
        raise SystemExit("pack verification failed:\n  " + "\n  ".join(problems[:20]))
    return manifest


def _schema_version(cfg: Config) -> int:
    from .sqlite import load_room_schema

    return int(load_room_schema(cfg)["version"])
