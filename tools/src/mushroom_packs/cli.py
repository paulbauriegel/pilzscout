"""`packs` command line. Each step caches its work under tools/cache and is safe to rerun."""
from __future__ import annotations

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


if __name__ == "__main__":
    app()
