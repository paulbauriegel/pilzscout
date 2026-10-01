# PilzScout

[![Android](https://github.com/paulbauriegel/pilzscout/actions/workflows/android.yml/badge.svg)](https://github.com/paulbauriegel/pilzscout/actions/workflows/android.yml)

Offline-first, bilingual (DE/EN) mushroom identification and classifier-evaluation app for Germany.
Android, Jetpack Compose with Material 3 Expressive, Navigation 3, Room, CameraX and LiteRT.

The app combines one to four photographs (cap, underside, stem and base, habitat) plus date and
location into one result, explains it with an offline similar-species comparison, lets you
browse FungiTastic observations and Wikipedia extracts without a network, and keeps every
identification in a local history with ZIP export.

## Repository layout

| Path | Purpose |
|------|---------|
| `app/` | Android application (`de.pilzscout.app`) |
| `core/` | Pure Kotlin module: fusion of per-photo predictions, confidence descriptors, comparison engine, export JSON models, pack manifest |
| `tools/` | Python (uv) pack builder: species table, GBIF presence, Wikipedia extraction, FungiTastic aggregation, traits, SQLite, packaging |
| `packs/<version>/` | Built offline packs (gitignored) |
| `app/src/bundled/assets/packs/` | Pack copied into the `bundled` APK by `packs package` (gitignored) |
| `docs/remote-packs.md` | Design of the Hugging Face pack hosting and the Play release plan |

## Prerequisites

- Android Studio 2026.1 (bundled JDK 21) and an Android SDK with platform 37 and build-tools 36.
- `source scripts/env.sh` before running `./gradlew` from a terminal (sets `JAVA_HOME` to the Studio JDK).
- Python 3.12+ and [uv](https://docs.astral.sh/uv/) for `tools/`.
- The training project `../mushroom-hunter/training` with the fine-tuned model export
  (`outputs/full_vits16_v4_384/export/`), `outputs/prepared/full_500p/classes.json` and the
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
uv run packs package --version 2026.10.1   # packs/<version>/ + archives + copy into app/src/bundled/assets/packs
uv run packs verify --version 2026.10.1    # re-check every file and archive against manifest.json
```

Every step caches under `tools/cache/` and is safe to rerun. `packs sqlite` needs the Room schema
JSON, which `./gradlew :app:kspDebugKotlin` exports to `app/schemas/`; bump
`SpeciesDatabase.VERSION` whenever the entities change and rebuild the pack.

Pack components: `model` (LiteRT model, labels, `model.json`), `core` (`species.db`), `wiki`
(thumbnails), `fungitastic` (observation thumbnails), `images-hd` (optional, not bundled).
`manifest.json` lists every file with its SHA-256, plus each component's licence and attribution.
The components with many small files (`wiki`, `fungitastic`, `images-hd`) are also packed into one
uncompressed `<component>.tar` each for remote installs. The app verifies every checksum while it
installs into `filesDir/packs/`.

### Reproducibility

`packs package` is deterministic: the same cached inputs produce byte-identical files, archives
(sorted entries, zeroed tar metadata) and `manifest.json` (no timestamps). The manifest's `build`
block records the git commit of the tools, whether `tools/` or `app/schemas/` had uncommitted
changes, and the training run and prepared dataset it used. The inputs themselves are the caches
under `tools/cache/` (GBIF, Wikipedia and FungiTastic responses) plus the training export, so
keep `tools/cache/` if you need to rebuild an already published version exactly. Rebuilding from
scratch refetches Wikipedia and GBIF and will differ.

## Publishing a pack to Hugging Face

The `play` app flavour downloads its pack from one Hugging Face **dataset** repo (default
`paulbauriegel/pilzscout-pack-de`). The layout and the reasons behind it are in
[docs/remote-packs.md](docs/remote-packs.md). In short:

```
catalog.json                 every published pack, newest first (the only file the app reads from main)
README.md                    dataset card with sources and licences (generated)
<version>/manifest.json
<version>/model/…            model files
<version>/core/species.db
<version>/wiki.tar           one archive per thumbnail component
<version>/fungitastic.tar
```

Each version is uploaded in a single commit and tagged `pack-<version>`. The catalog entry
records that commit SHA and the manifest's SHA-256, and the app downloads every file from that
commit. A published version can therefore never change under the app. Republishing an identical
build is a no-op. A different build under an existing version number is refused, so bump the
version instead.

One-time setup:

```bash
cd tools
uv sync --extra dev --extra publish
uv run hf auth login            # or export HF_TOKEN=… (a write token for paulbauriegel)
```

Release a pack:

```bash
git status                       # tools/ and app/schemas/ must be committed (publish refuses dirty builds)
uv run pytest
uv run packs package --version 2026.10.1 --no-assets
uv run packs publish --version 2026.10.1 --repo paulbauriegel/pilzscout-pack-de
```

`packs publish` re-verifies the pack and uploads the manifest, the model and core files and the
archives (not the loose thumbnails). It then tags the commit and rewrites `catalog.json` and
`README.md` in a second commit.

Options:

- `--min-app-version-code N`: hides the pack from apps older than `versionCode` N. Use it when
  a pack needs app changes beyond the Room schema.
- `--private/--no-private`: sets the repo's visibility on first creation (default private). The
  app downloads anonymously, so it can only use the repo once the repo is public. Read the
  licence notes below first.
- `--allow-dirty`: publishes a pack built from uncommitted changes. Use it only for experiments.

Compatibility is checked per catalog entry. The app installs the newest pack whose
`schemaVersion` equals its `SpeciesDatabase.VERSION`, whose manifest format it understands and
whose `minAppVersionCode` it meets. Bumping the Room schema therefore needs a new pack and an app
release. Older apps keep using the last pack built for their schema.

### Testing against a local mirror

`--mirror DIR` writes the Hub's URL layout to disk instead of uploading, without touching the
network:

```bash
uv run packs publish --version 2026.10.1 --mirror /tmp/pack-mirror --allow-dirty
python3 -m http.server -d /tmp/pack-mirror 8000
```

Then point a debug `play` build at it. Debug builds allow plain HTTP to `localhost` and
`10.0.2.2` only.

```bash
./gradlew :app:assemblePlayDebug -Ppilzscout.packBaseUrl=http://localhost:8000
adb reverse tcp:8000 tcp:8000    # the emulator can use http://10.0.2.2:8000 without this
adb install -r app/build/outputs/apk/play/debug/app-play-debug.apk
```

Over wireless adb the reverse tunnel sometimes stalls on new connections. If the app reports
it is offline, run `adb reverse --remove-all` and set up the reverse again.

## Building and running the app

The app has two product flavours. They share the `applicationId` and the signing key, so
switching between them is an in-place update that keeps the installed pack and the history.

| Flavour | Pack source | Use |
|---------|-------------|-----|
| `bundled` (default) | APK assets, copied by `packs package` | offline development, the emulator |
| `play` | Hugging Face dataset repo (`RemotePackSource`) | Google Play; the APK is ~55 MB instead of ~300 MB |

```bash
source scripts/env.sh
./gradlew :core:test :app:assembleBundledDebug
adb install -r app/build/outputs/apk/bundled/debug/app-bundled-debug.apk
```

On first launch the app installs the "Germany offline pack" component by component.
Identification is available once the model and the core species data are installed.

In the `play` flavour the same screen downloads the pack:

- Large files and archives resume after a dropped connection.
- Every file is checked against its SHA-256 and installed through a staging directory, so a
  failed update keeps the old pack working.
- A WorkManager job waits for a network (Wi-Fi only if the user asks) and retries with backoff.

The app checks the catalog for a newer pack at most once a day, or when the user taps *Check for
updates* under Settings → Offline data. Updates are offered, never forced. The repo defaults to
`paulbauriegel/pilzscout-pack-de`; override it with `-Ppilzscout.packRepo=owner/name`, or set a full base URL
with `-Ppilzscout.packBaseUrl=…`.

## Continuous integration

`.github/workflows/android.yml` runs on every push, every pull request and by hand. It:

- runs the pack tool tests (`pytest`), `:core:test` and the app unit tests;
- builds the `play` flavour: a debug APK and a release APK, plus an AAB when signing secrets are
  set;
- uploads them as the `pilzscout-apk-<run>` artifact, kept for 30 days.

Pushing a `v*` tag (e.g. `git tag v0.2.0 && git push origin v0.2.0`) also creates a GitHub
release with the APKs attached. The release takes its `versionName` from the tag.

Notes:

- CI sets `versionCode` to the workflow run number, so later CI builds can update earlier ones.
  Local builds keep `versionCode` 1.
- Only the `play` flavour is built, because the `bundled` pack needs the training data. Its APK
  downloads the pack from Hugging Face on first launch, which works only once the dataset repo
  is public.
- The CI debug APK is signed with a throwaway key, so it cannot update a locally built debug
  install, and vice versa. Android refuses the update; never uninstall to get around that on the
  Pixel.
- Signed release builds come from the repository secrets `ANDROID_KEYSTORE_BASE64` (output of
  `base64 -i upload.jks`), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
  `ANDROID_KEY_PASSWORD`. Without them the release APK is unsigned.

## Publishing to Google Play

- Build the bundle with `./gradlew :app:bundlePlayRelease`, which writes
  `app/build/outputs/bundle/playRelease/app-play-release.aab`.
- Signing: create an upload key once with `keytool -genkeypair -v -keystore upload.jks -alias
  upload -keyalg RSA -keysize 4096 -validity 10000`, and enrol in Play App Signing. Then put
  `storeFile`, `storePassword`, `keyAlias` and `keyPassword` into `keystore.properties` at the
  repo root. That file and `*.jks` are gitignored. Without it, release builds stay unsigned.
- Bump `versionCode` in `app/build.gradle.kts` for every upload.
- Play Console: privacy policy (camera and precise location stay on the device; pack files come
  from huggingface.co), Data safety (no data collected or shared), foreground service type
  `dataSync` declaration (downloading the user-requested offline pack), content rating, and a
  store listing that says never to eat a mushroom based on the app.
- A Play-signed install cannot update a debug-signed one. Never switch the owner's Pixel to the
  Play build without the backup described in `AGENTS.md`, because Android would require an
  uninstall.

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
edit, rerun, then `./gradlew :app:installBundledDebug`.

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

Checked on 2026-10-01. Every component in `manifest.json` carries `license` and `attribution`
fields, generated from `LICENSES` in `tools/src/mushroom_packs/package.py`, and the dataset card
repeats them.

| Source | Licence | Used in |
|--------|---------|---------|
| FungiTastic (Picek et al., CVPRW 2025), photos from the Atlas of Danish Fungi | **CC BY-NC-SA 4.0** (Kaggle dataset licence). The Danish Fungi README states "non-commercial research purposes only". | `fungitastic`, `images-hd`, aggregates and captions in `core`, training data of `model` |
| DINOv3 ViT-S/16 backbone (Meta) | DINOv3 License: redistribution allowed, must include the licence and show "Built with DINOv3" | `model` |
| Wikipedia extracts, Wikimedia Commons thumbnails | CC BY-SA 4.0 for text. Commons files carry their own licences, which are not yet recorded per thumbnail. | `core`, `wiki` |
| GeoNames | CC BY 4.0 | place names in `core` |
| GBIF occurrence counts | per-dataset licences (CC0/CC BY/CC BY-NC); only counts are used | Germany filter in `core` |

Consequences:

- PilzScout, and anything built from these packs, must stay **non-commercial**: a free app with
  no ads and no in-app purchases. The model and the FungiTastic-derived components inherit
  ShareAlike (CC BY-NC-SA 4.0), and the model must also be passed on under the DINOv3 License.
- The Settings → About text and the dataset card give the attribution, including "Built with
  DINOv3".
- `core/species.db` mixes CC BY-SA text with CC BY-NC-SA data. The two ShareAlike licences are
  not formally compatible. Before making the repo public, either get a legal opinion or split
  the Wikipedia text and the FungiTastic data into separate components.
- Earlier versions of this README and of the About screen said FungiTastic was CC BY 4.0. That
  was wrong.

## Follow-ups

- fp16 model export (`litert-torch` 0.9 has no fp16 option; the onnx2tf path would need TensorFlow).
- Real online explanation provider behind `ExplanationProvider` (currently a stub).
- Record per-file Wikimedia Commons licences and authors for the `wiki` thumbnails.
- Decide on the licence questions above, then make the Hugging Face repo public.
- History import from the ZIP export (needed before any device can move to a Play-signed install).
- FungiTastic-Mini segmentation masks (`packs fungitastic --masks <parquet dir>`).
