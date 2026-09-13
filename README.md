# PilzScout

Offline-first, bilingual (DE/EN) mushroom identification and classifier-evaluation app for Germany.
Android, Jetpack Compose with Material 3 Expressive, Navigation 3, Room, CameraX and LiteRT.

The app combines one to four photographs (cap, underside, stem and base, habitat) plus date and
approximate location into one result, explains it with an offline similar-species comparison, lets you
browse FungiTastic observations and Wikipedia extracts without a network, and keeps every
identification in a local history with ZIP export.

## Repository layout

| Path | Purpose |
|------|---------|
| `app/` | Android application (`de.pilzscout.app`) |
| `core/` | Pure Kotlin module: fusion of per-photo predictions, confidence descriptors, comparison engine, export JSON models, pack manifest |
| `tools/` | Python (uv) pack builder: species table, GBIF presence, Wikipedia extraction, FungiTastic aggregation, traits, SQLite, packaging |
| `packs/<version>/` | Built offline packs (gitignored) |
| `app/src/main/assets/packs/` | Pack copied into the APK by `packs package` (gitignored) |

## Prerequisites

- Android Studio 2026.1 (bundled JDK 21) and an Android SDK with platform 37 and build-tools 36.
- `source scripts/env.sh` before running `./gradlew` from a terminal (sets `JAVA_HOME` to the Studio JDK).
- Python 3.12+ and [uv](https://docs.astral.sh/uv/) for `tools/`.
- The training project `../mushroom-hunter/training` with the fine-tuned model export
  (`outputs/full_vits16_v3_320/export/`), `outputs/prepared/full_500p/classes.json` and the
  FungiTastic metadata CSVs and 500p images under `data/FungiTastic/`. Paths can be overridden with
  `PACKS_TRAINING_DIR`, `PACKS_RUN_NAME`, `PACKS_PREPARED`.

## Building the offline pack

```bash
cd tools
uv sync --extra dev
uv run pytest
uv run packs species          # classes.json + labels.txt + FungiTastic taxonomy -> cache/species_base.parquet
uv run packs gbif             # GBIF backbone key + German occurrence count per species (cached per species)
uv run packs wiki             # Wikidata sitelinks, DE/EN summaries, sections, thumbnails, common names
uv run packs fungitastic      # per-species stats, sampled observations, 224 px thumbnails (add --hd for 640 px)
uv run packs traits           # rule-based feature rows from Wikipedia sections + FungiTastic aggregates
uv run packs sqlite           # core/species.db built from the exported Room schema (app/schemas)
uv run packs package --version 2026.09.3   # packs/<version>/ + copy into app/src/main/assets/packs
```

Every step caches under `tools/cache/` and is safe to rerun. `packs sqlite` needs the Room schema
JSON, which `./gradlew :app:kspDebugKotlin` exports to `app/schemas/`; bump
`SpeciesDatabase.VERSION` whenever the entities change and rebuild the pack.

Pack components: `model` (LiteRT model, labels, `model.json`), `core` (`species.db`), `wiki`
(thumbnails), `fungitastic` (observation thumbnails), `images-hd` (optional, not bundled).
`manifest.json` lists every file with its SHA-256; the app verifies checksums while installing the
bundled pack into `filesDir/packs/`.

## Building and running the app

```bash
source scripts/env.sh
./gradlew :core:test :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch the app installs the bundled "Germany offline pack" component by component;
identification is available once the model and the core species data are installed.

## Forest backdrop artwork

The layered forest behind the three tabs is generated, not hand-drawn. `packs backdrop` renders
three horizontally tileable layers (far mountains with mist, middle and near tree lines) for the
light and the dark theme with Pillow and writes them as lossless WebP to
`app/src/main/res/drawable-nodpi/forest_{far,mid,near}_{light,dark}.webp`. The app tiles the layers
and shifts them at different speeds while the home pager is swiped.

```bash
cd tools
uv run packs backdrop --preview   # add --seed/--width/--height to vary; previews land in cache/build/backdrop/
```

Colours, hill shapes and tree density are constants at the top of `tools/src/mushroom_packs/backdrop.py`;
edit, rerun, then `./gradlew :app:installDebug`.

## How identification works

1. Each photo is resized (shorter edge 366 px), center-cropped to 320 px and fed to the model as
   NHWC float in the 0–255 range (normalisation is baked into the exported graph).
2. Per-photo log-probabilities are averaged (geometric mean of the softmax), species without GBIF
   records in Germany are masked out, and the month of the observation adds a mild prior derived from
   FungiTastic month histograms.
3. The descriptor is *strong candidate* (top-1 ≥ 60 % and margin ≥ 25 %), *several plausible
   matches* (margin < 10 % and top-3 ≥ 50 %) or *uncertain*.
4. The offline comparison compares the primary candidate with an alternative feature by feature
   (cap, gills or pores, stem, ring, stem base or volva, surface and texture, habitat and substrate,
   region and season) using normalised trait keys; a differing feature is *not visible* unless the
   corresponding photo view was captured. Every statement carries a basis (observed in your photos,
   typical reference characteristic, common in FungiTastic observations, Wikipedia, AI-generated).

## Data and licences

- FungiTastic (Picek et al., CVPRW 2025), Atlas of Danish Fungi — CC BY 4.0.
- Wikipedia extracts and thumbnails — CC BY-SA 4.0; each article row stores title, URL, revision and retrieval date.
- GBIF occurrence counts for the Germany filter.
- Classifier: DINOv3 ViT-S/16 fine-tuned on FungiTastic in `mushroom-hunter/training`; see that
  project's README for the model and dataset licence notes before publishing.

## Follow-ups

- fp16 model export (`litert-torch` 0.9 has no fp16 option; the onnx2tf path would need TensorFlow).
- Real online explanation provider behind `ExplanationProvider` (currently a stub).
- Play Asset Delivery or a remote `PackSource` for the image components to shrink the APK.
- FungiTastic-Mini segmentation masks (`packs fungitastic --masks <parquet dir>`).
