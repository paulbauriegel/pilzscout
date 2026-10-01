import json
import tarfile
from pathlib import Path

import pytest

from mushroom_packs import package, publish
from mushroom_packs.config import Config


def _pack(tmp_path: Path, monkeypatch, version: str = "2026.10.1") -> Config:
    """A tiny fake pack built from fake cached inputs through the real package step."""
    cache = tmp_path / "cache"
    export = tmp_path / "training" / "outputs" / "run" / "export"
    export.mkdir(parents=True)
    (export / "export_info.json").write_text(json.dumps({"model": "vit", "img_size": 384, "num_classes": 2}))
    (export / "mushroom_model_dynamic.tflite").write_bytes(b"model")
    (export / "labels.txt").write_text("a\nb\n")
    build = cache / "build"
    (build / "core").mkdir(parents=True)
    (build / "core" / "species.db").write_bytes(b"db")
    for comp, names in {"wiki": ["b.webp", "a.webp"], "fungitastic": ["x.webp"]}.items():
        (build / comp / "thumbs").mkdir(parents=True)
        for n in names:
            (build / comp / "thumbs" / n).write_bytes(n.encode() * 100)
    cfg = Config(training_dir=tmp_path / "training", run_name="run", cache_dir=cache, out_dir=tmp_path / "packs",
                 assets_dir=tmp_path / "assets" / "packs")
    monkeypatch.setattr(package, "_schema_version", lambda cfg: 4)
    package.run(cfg, version=version, copy_to_assets=True, prefer_fp16=False)
    return cfg


def test_package_is_reproducible_and_archives_match_files(tmp_path, monkeypatch):
    cfg = _pack(tmp_path, monkeypatch)
    out = cfg.out_dir / "2026.10.1"
    first = {p.relative_to(out): p.read_bytes() for p in out.rglob("*") if p.is_file()}
    package.run(cfg, version="2026.10.1", copy_to_assets=False, prefer_fp16=False)
    second = {p.relative_to(out): p.read_bytes() for p in out.rglob("*") if p.is_file()}
    assert first == second

    manifest = package.verify(out)
    assert manifest["formatVersion"] == package.FORMAT_VERSION
    wiki = next(c for c in manifest["components"] if c["id"] == "wiki")
    assert wiki["archive"]["path"] == "wiki.tar" and wiki["license"] == "CC-BY-SA-4.0"
    with tarfile.open(out / "wiki.tar") as tar:
        assert tar.getnames() == ["wiki/thumbs/a.webp", "wiki/thumbs/b.webp"]
        assert all(m.mtime == 0 and m.uid == 0 for m in tar.getmembers())
    assert "archive" not in next(c for c in manifest["components"] if c["id"] == "core")
    # archives are for remote installs only and never land in the APK assets
    assert not list(cfg.assets_dir.glob("*.tar"))
    assert (cfg.assets_dir / "wiki" / "thumbs" / "a.webp").exists()


def test_verify_detects_tampering(tmp_path, monkeypatch):
    cfg = _pack(tmp_path, monkeypatch)
    (cfg.out_dir / "2026.10.1" / "core" / "species.db").write_bytes(b"other")
    with pytest.raises(SystemExit):
        package.verify(cfg.out_dir / "2026.10.1")


def test_upload_set_uses_archives_instead_of_loose_thumbnails(tmp_path, monkeypatch):
    cfg = _pack(tmp_path, monkeypatch)
    out = cfg.out_dir / "2026.10.1"
    manifest = json.loads((out / "manifest.json").read_text())
    remote = sorted(u.remote for u in publish.upload_set(out, manifest))
    assert remote == [
        "2026.10.1/core/species.db",
        "2026.10.1/fungitastic.tar",
        "2026.10.1/manifest.json",
        "2026.10.1/model/labels.txt",
        "2026.10.1/model/model.json",
        "2026.10.1/model/mushroom_model_dynamic.tflite",
        "2026.10.1/wiki.tar",
    ]


def test_merge_catalog_replaces_same_version_and_sorts_newest_first():
    e = lambda v, rev: {"packVersion": v, "revision": rev}
    cat = publish.merge_catalog(None, e("2026.9.10", "a"))
    cat = publish.merge_catalog(cat, e("2026.10.1", "b"))
    cat = publish.merge_catalog(cat, e("2026.9.10", "c"))
    assert [(p["packVersion"], p["revision"]) for p in cat["packs"]] == [("2026.10.1", "b"), ("2026.9.10", "c")]


def test_mirror_publish_has_hub_layout(tmp_path, monkeypatch):
    cfg = _pack(tmp_path, monkeypatch)
    mirror = tmp_path / "mirror"
    catalog = publish.run(cfg, "2026.10.1", repo_id=None, mirror=mirror, min_app_version_code=1, private=True, allow_dirty=True)
    entry = catalog["packs"][0]
    assert entry["revision"] == "local-2026.10.1" and entry["schemaVersion"] == 4
    assert json.loads((mirror / "resolve" / "main" / "catalog.json").read_text()) == catalog
    manifest_file = mirror / "resolve" / entry["revision"] / entry["manifest"]
    assert package.sha256(manifest_file) == entry["manifestSha256"]
    assert (mirror / "resolve" / entry["revision"] / "2026.10.1" / "wiki.tar").exists()


def test_publish_refuses_dirty_builds(tmp_path, monkeypatch):
    cfg = _pack(tmp_path, monkeypatch)
    manifest_path = cfg.out_dir / "2026.10.1" / "manifest.json"
    m = json.loads(manifest_path.read_text())
    m["build"]["toolsDirty"] = True
    manifest_path.write_text(json.dumps(m))
    with pytest.raises(SystemExit, match="uncommitted"):
        publish.run(cfg, "2026.10.1", repo_id=None, mirror=tmp_path / "m", min_app_version_code=1, private=True, allow_dirty=False)
