"""Paths and tunables. Everything can be overridden through environment variables."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[2]
REPO_DIR = TOOLS_DIR.parent


def _env_path(name: str, default: Path) -> Path:
    value = os.environ.get(name)
    return Path(value).expanduser().resolve() if value else default


@dataclass
class Config:
    # Inputs from the training project (mushroom-hunter/training)
    training_dir: Path = field(default_factory=lambda: _env_path("PACKS_TRAINING_DIR", REPO_DIR.parent / "mushroom-hunter" / "training"))
    run_name: str = os.environ.get("PACKS_RUN_NAME", "full_vits16_v4_384")
    prepared_dir_name: str = os.environ.get("PACKS_PREPARED", "full_500p")

    # Working directories
    cache_dir: Path = field(default_factory=lambda: _env_path("PACKS_CACHE_DIR", TOOLS_DIR / "cache"))
    out_dir: Path = field(default_factory=lambda: _env_path("PACKS_OUT_DIR", REPO_DIR / "packs"))
    assets_dir: Path = field(default_factory=lambda: _env_path("PACKS_ASSETS_DIR", REPO_DIR / "app" / "src" / "bundled" / "assets" / "packs"))
    schemas_dir: Path = field(default_factory=lambda: _env_path("PACKS_SCHEMAS_DIR", REPO_DIR / "app" / "schemas"))
    res_dir: Path = field(default_factory=lambda: _env_path("PACKS_RES_DIR", REPO_DIR / "app" / "src" / "main" / "res"))

    # Publishing (packs publish)
    hf_repo: str | None = os.environ.get("PACKS_HF_REPO", "paulbauriegel/pilzscout-pack-de")

    # GBIF
    gbif_min_de_occurrences: int = int(os.environ.get("PACKS_GBIF_MIN_DE", "1"))
    gbif_rps: float = float(os.environ.get("PACKS_GBIF_RPS", "8"))

    # Wikipedia
    wiki_rps: float = float(os.environ.get("PACKS_WIKI_RPS", "30"))
    user_agent: str = os.environ.get(
        "PACKS_USER_AGENT",
        "PilzScoutPackBuilder/0.1 (https://github.com/pilzscout; offline mushroom app data prep)",
    )

    @property
    def classes_json(self) -> Path:
        return self.training_dir / "outputs" / "prepared" / self.prepared_dir_name / "classes.json"

    @property
    def export_dir(self) -> Path:
        return self.training_dir / "outputs" / self.run_name / "export"

    @property
    def metadata_dir(self) -> Path:
        return self.training_dir / "data" / "FungiTastic" / "metadata" / "FungiTastic"

    @property
    def images_dir(self) -> Path:
        return self.training_dir / "data" / "FungiTastic" / "FungiTastic"

    def species_parquet(self) -> Path:
        return self.cache_dir / "species_base.parquet"


CFG = Config()
