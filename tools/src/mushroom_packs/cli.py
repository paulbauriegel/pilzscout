"""`packs` command line. Each step caches its work under tools/cache and is safe to rerun."""
from __future__ import annotations

from pathlib import Path

import typer

from .config import CFG

app = typer.Typer(no_args_is_help=True, add_completion=False)


@app.command()
def species() -> None:
    """Build the species base table from classes.json + labels.txt + FungiTastic taxonomy."""
    from .species import build_species

    df = build_species(CFG)
    typer.echo(f"{len(df)} species -> {CFG.species_parquet()}")


@app.command()
def gbif(limit: int = typer.Option(None, help="Only process the first N species (for testing).")) -> None:
    """Look up GBIF keys and German occurrence counts."""
    from .gbif import run

    df = run(CFG, limit=limit)
    typer.echo(f"{int(df['in_germany'].sum())} of {len(df)} species recorded in Germany")


@app.command()
def wiki(limit: int = typer.Option(None), lang: str = typer.Option("de,en")) -> None:
    """Fetch Wikipedia (Wikidata sitelinks, summaries, sections, thumbnails)."""
    from .wiki import run

    run(CFG, langs=lang.split(","), limit=limit)


@app.command()
def edibility() -> None:
    """Reference edibility from the English Wikipedia mushroom infobox (+ FungiTastic poisonous flag)."""
    from .edibility import run

    run(CFG)


@app.command()
def fungitastic(hd: bool = typer.Option(False), masks: str = typer.Option(None)) -> None:
    """Aggregate FungiTastic metadata, sample observations, generate thumbnails."""
    from .fungitastic import run

    run(CFG, hd=hd, masks=masks)


@app.command()
def traits() -> None:
    """Derive structured trait rows from Wikipedia sections and FungiTastic stats."""
    from .traits import run

    run(CFG)


@app.command()
def places(country: str = typer.Option("DE")) -> None:
    """Offline gazetteer of populated places from GeoNames (needs cache/geonames/<CC>.txt)."""
    from .places import run

    run(CFG, country=country)


@app.command()
def sqlite() -> None:
    """Build core/species.db from the Room schema JSON and the cached step outputs."""
    from .sqlite import run

    path = run(CFG)
    typer.echo(f"wrote {path} ({path.stat().st_size / 1e6:.1f} MB)")


@app.command()
def package(
    version: str = typer.Option(..., help="Pack version, e.g. 2026.09.1"),
    assets: bool = typer.Option(True, help="Also copy into app/src/main/assets/packs"),
    fp16: bool = typer.Option(False, help="Prefer the fp16 model file if present"),
) -> None:
    """Assemble packs/<version>/ with manifest.json and copy into the app assets."""
    from .package import run

    run(CFG, version=version, copy_to_assets=assets, prefer_fp16=fp16)


@app.command()
def backdrop(
    out: Path = typer.Option(None, help="Output directory (default: app/src/main/res/drawable-nodpi)"),
    width: int = typer.Option(2400, help="Tile width in pixels"),
    height: int = typer.Option(600, help="Tile height in pixels (170 dp at the Pixel 7 Pro density)"),
    seed: int = typer.Option(20260913, help="Seed for the hill and tree geometry"),
    preview: bool = typer.Option(False, help="Also write composited previews under cache/build/backdrop/"),
) -> None:
    """Render the parallax forest backdrop (far/mid/near x light/dark, lossless WebP) into the app drawables."""
    from .backdrop import run

    for path in run(CFG, out=out, width=width, height=height, seed=seed, write_preview=preview):
        typer.echo(f"wrote {path} ({path.stat().st_size / 1e3:.0f} kB)")


if __name__ == "__main__":
    app()
