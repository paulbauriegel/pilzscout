"""Step 9: publish packs/<version>/ to a Hugging Face dataset repo, or to a local mirror for testing.

Repo layout (one dataset repo, e.g. paulbauriegel/pilzscout-pack-de):

    catalog.json                 the only file the app reads from `main`; lists every published pack
    README.md                    dataset card with sources and licences (generated)
    <version>/manifest.json      the pack manifest, as written by `packs package`
    <version>/model/...          model files (few, large)
    <version>/core/species.db
    <version>/<component>.tar    one archive per thumbnail component instead of thousands of files

Each version is uploaded in one commit, tagged `pack-<version>`, and the catalog entry records that
commit SHA. The app resolves every pack file by commit SHA, so a published version never changes under
it; republishing an identical version is a no-op, and a different build under an existing version
number is refused.

A mirror (`--mirror DIR`) writes the same URLs to disk (DIR/resolve/<revision>/<path>), so
`python -m http.server -d DIR` behaves like the Hub for the app's `play` flavour.
"""
from __future__ import annotations

import json
import shutil
from dataclasses import dataclass
from pathlib import Path

from .config import Config
from .package import sha256, verify

CATALOG_FORMAT = 1
CATALOG = "catalog.json"


@dataclass
class Upload:
    local: Path
    remote: str  # path in repo


def upload_set(pack_dir: Path, manifest: dict) -> list[Upload]:
    """Files a remote install needs: the manifest, the loose files of non-archived components, the archives."""
    version = manifest["packVersion"]
    uploads = [Upload(pack_dir / "manifest.json", f"{version}/manifest.json")]
    for c in manifest["components"]:
        if not c["files"]:
            continue
        if c.get("archive"):
            uploads.append(Upload(pack_dir / c["archive"]["path"], f"{version}/{c['archive']['path']}"))
        else:
            uploads += [Upload(pack_dir / e["path"], f"{version}/{e['path']}") for e in c["files"]]
    return uploads


def catalog_entry(manifest: dict, manifest_sha: str, revision: str, min_app_version_code: int) -> dict:
    version = manifest["packVersion"]
    return {
        "packVersion": version,
        "region": manifest["region"],
        "schemaVersion": manifest["schemaVersion"],
        "formatVersion": manifest.get("formatVersion", 1),
        "minAppVersionCode": min_app_version_code,
        "revision": revision,
        "manifest": f"{version}/manifest.json",
        "manifestSha256": manifest_sha,
        "bytes": {c["id"]: (c.get("archive") or {}).get("bytes", c["bytes"]) for c in manifest["components"] if c["files"]},
    }


def merge_catalog(catalog: dict | None, entry: dict) -> dict:
    """Adds or replaces the entry for entry['packVersion']; newest version first."""
    packs = [p for p in (catalog or {}).get("packs", []) if p["packVersion"] != entry["packVersion"]]
    packs.append(entry)
    packs.sort(key=lambda p: _version_key(p["packVersion"]), reverse=True)
    return {"formatVersion": CATALOG_FORMAT, "packs": packs}


def _version_key(version: str) -> tuple:
    return tuple(int(p) if p.isdigit() else p for p in version.split("."))


def dataset_card(repo_id: str, catalog: dict, manifest: dict) -> str:
    rows = "\n".join(
        f"| `{c['id']}` | {c['bytes'] / 1e6:.1f} MB | {c.get('license', '?')} | {c.get('attribution', '')} |"
        for c in manifest["components"] if c["files"]
    )
    versions = "\n".join(
        f"| {p['packVersion']} | `{p['revision'][:12]}` | {p['schemaVersion']} | {p['minAppVersionCode']} |" for p in catalog["packs"]
    )
    return f"""---
license: other
license_name: pilzscout-mixed
pretty_name: PilzScout offline pack (Germany)
tags:
- fungi
- mushrooms
- image-classification
- litert
viewer: false
---

# PilzScout offline pack (Germany)

Data packs downloaded by the PilzScout Android app (`de.pilzscout.app`): an on-device LiteRT classifier,
a species database and thumbnails. The app reads `catalog.json` from `main` and downloads every other file
by the commit SHA listed there. Files are published by `packs publish` in the app repository's `tools/`;
do not edit this repo by hand.

**Never eat a mushroom based on an app result.**

## Components and licences (pack {manifest['packVersion']})

| Component | Size | Licence | Attribution |
|-----------|------|---------|-------------|
{rows}

The FungiTastic data (and therefore the classifier fine-tuned on it and the FungiTastic-derived parts of the
species database) is licensed for **non-commercial use only** (CC BY-NC-SA 4.0). The classifier is built with
DINOv3 and redistributed under the DINOv3 License (https://ai.meta.com/resources/models-and-libraries/dinov3-license/).

## Published versions

| Pack | Revision | Room schema | Min. app versionCode |
|------|----------|-------------|----------------------|
{versions}

Repository: `{repo_id}`
"""


def run(
    cfg: Config,
    version: str,
    repo_id: str | None,
    mirror: Path | None,
    min_app_version_code: int,
    private: bool,
    allow_dirty: bool,
) -> dict:
    pack_dir = cfg.out_dir / version
    if not (pack_dir / "manifest.json").exists():
        raise SystemExit(f"{pack_dir}/manifest.json not found; run `packs package --version {version}` first")
    manifest = verify(pack_dir)
    if manifest.get("formatVersion", 1) < 2:
        raise SystemExit("pack was built before archives existed; rerun `packs package` for this version")
    build = manifest.get("build") or {}
    if (build.get("toolsDirty") or not build.get("toolsCommit")) and not allow_dirty:
        raise SystemExit(
            "pack was built from uncommitted tools/ or app/schemas changes, so it cannot be reproduced. "
            "Commit, rerun `packs package`, or pass --allow-dirty."
        )
    manifest_sha = sha256(pack_dir / "manifest.json")
    uploads = upload_set(pack_dir, manifest)
    total = sum(u.local.stat().st_size for u in uploads)
    print(f"pack {version}: {len(uploads)} files, {total / 1e6:.1f} MB")
    if mirror is not None:
        return _publish_mirror(mirror, manifest, manifest_sha, uploads, min_app_version_code, repo_id or "local/mirror")
    if not repo_id:
        raise SystemExit("--repo (or PACKS_HF_REPO) is required unless --mirror is given")
    return _publish_hub(repo_id, manifest, manifest_sha, uploads, min_app_version_code, private)


def _publish_mirror(root: Path, manifest: dict, manifest_sha: str, uploads: list[Upload], min_code: int, repo_id: str) -> dict:
    revision = f"local-{manifest['packVersion']}"
    base = root / "resolve"
    for u in uploads:
        dst = base / revision / u.remote
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(u.local, dst)
    catalog_path = base / "main" / CATALOG
    existing = json.loads(catalog_path.read_text()) if catalog_path.exists() else None
    catalog = merge_catalog(existing, catalog_entry(manifest, manifest_sha, revision, min_code))
    catalog_path.parent.mkdir(parents=True, exist_ok=True)
    catalog_path.write_text(json.dumps(catalog, indent=2) + "\n")
    (base / "main" / "README.md").write_text(dataset_card(repo_id, catalog, manifest))
    print(f"mirror: {root} (serve with `python -m http.server -d {root} 8000`)")
    return catalog


def _publish_hub(repo_id: str, manifest: dict, manifest_sha: str, uploads: list[Upload], min_code: int, private: bool) -> dict:
    try:
        from huggingface_hub import CommitOperationAdd, HfApi, hf_hub_download
        from huggingface_hub.utils import EntryNotFoundError
    except ImportError as e:  # pragma: no cover
        raise SystemExit("huggingface_hub is missing: `uv sync --extra publish`") from e

    api = HfApi()  # token from HF_TOKEN or `hf auth login`
    version = manifest["packVersion"]
    tag = f"pack-{version}"
    api.create_repo(repo_id, repo_type="dataset", private=private, exist_ok=True)

    def download(path: str, revision: str = "main") -> dict | None:
        try:
            return json.loads(Path(hf_hub_download(repo_id, path, repo_type="dataset", revision=revision)).read_text())
        except EntryNotFoundError:
            return None

    catalog = download(CATALOG)
    published = next((p for p in (catalog or {}).get("packs", []) if p["packVersion"] == version), None)
    if published is not None:
        if published["manifestSha256"] != manifest_sha:
            raise SystemExit(
                f"pack {version} is already published at {published['revision']} with a different manifest. "
                "Published versions are immutable: bump the version and rerun `packs package`."
            )
        print(f"pack {version} is already published at {published['revision']}; refreshing the catalog entry only")
        revision = published["revision"]
    else:
        info = api.create_commit(
            repo_id,
            repo_type="dataset",
            operations=[CommitOperationAdd(path_in_repo=u.remote, path_or_fileobj=str(u.local)) for u in uploads],
            commit_message=f"Pack {version}",
        )
        revision = info.oid
        api.create_tag(repo_id, repo_type="dataset", tag=tag, revision=revision, exist_ok=True)
        print(f"uploaded pack {version} as {revision} (tag {tag})")

    merged = merge_catalog(catalog, catalog_entry(manifest, manifest_sha, revision, min_code))
    if merged == catalog:
        print("catalog already up to date")
        return merged
    catalog = merged
    api.create_commit(
        repo_id,
        repo_type="dataset",
        operations=[
            CommitOperationAdd(path_in_repo=CATALOG, path_or_fileobj=(json.dumps(catalog, indent=2) + "\n").encode()),
            CommitOperationAdd(path_in_repo="README.md", path_or_fileobj=dataset_card(repo_id, catalog, manifest).encode()),
        ],
        commit_message=f"Catalog: pack {version}",
    )
    print(f"catalog updated: https://huggingface.co/datasets/{repo_id}/blob/main/{CATALOG}")
    return catalog
