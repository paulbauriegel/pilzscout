# Remote packs on Hugging Face

**Status (2026-10-01):**

- Phases 1–3 are implemented. Tested on the Pixel 7 Pro against a local mirror: update by reuse,
  a full archive download, and a resume after an interrupted download (HTTP 206).
- Nothing has been uploaded to the Hub yet.
- Phase 4 (the Play release) and the licence decisions below are still open.
- The commands are in the README ("Publishing a pack to Hugging Face", "Publishing to Google Play").

Today every pack component ships inside the APK (`app/src/main/assets/packs/`, 176 MB) and
`AssetPackSource` copies it into `filesDir/packs/` on first launch. The debug APK is 306 MB. This plan
moves the pack data to the Hugging Face Hub, so the app becomes a small download from Google Play and
fetches its data on first launch. Pack data can then be updated without an app release.

## Is this allowed on Google Play?

Yes, with conditions.

- **Downloading data is allowed. Downloading code is not.** The Device and Network Abuse policy
  forbids loading executable code (dex, native `.so`, scripts) from outside Play. A LiteRT
  `.tflite` model, a SQLite database and WebP images are data that the shipped code interprets.
  Many Play apps download ML models this way. The manifest must never carry code.
- **The size limit is a reason to switch.** Play limits the compressed download of the base module
  plus config splits to 200 MB. The bundled pack is about 168 MB of mostly incompressible data
  (`noCompress` for tflite, db, webp), so a release AAB is close to the limit, and any larger model
  or the `images-hd` component would go over it. Without the assets the app is roughly 15–25 MB.
- **What the Play Console will ask for:**
  - A privacy policy URL. The app needs one anyway for camera and precise location.
  - The Data safety form. Photos, locations and history stay on the device, so nothing is
    "collected". The policy should still say that pack files are downloaded from huggingface.co,
    which sees the device's IP address and a user agent but no personal data.
  - A foreground service declaration. `PackInstallWorker` uses `FOREGROUND_SERVICE_DATA_SYNC`, and
    from targetSdk 34 on Play asks you to justify each FGS type in the Console. Downloading
    user-requested content is an accepted use for `dataSync`. On API 34+ a user-initiated data
    transfer job is the newer alternative.
  - A content rating and a target audience. The edibility disclaimers already in the app, plus
    "never eat a mushroom based on this app" in the store listing, cover the health and safety
    angle. Other mushroom identification apps are on Play.
- **Data licences (checked 2026-10-01).** Publishing on HF counts as redistribution.
  - **FungiTastic is CC BY-NC-SA 4.0** (the Kaggle dataset licence). The Danish Fungi README says
    "non-commercial research purposes only". The paper and the project page do not state a data
    licence, and the earlier "CC BY 4.0" in this repo was wrong.
  - Consequence: the app, the `fungitastic` and `images-hd` components, the FungiTastic-derived
    parts of `core` and, to be safe, the fine-tuned model must stay non-commercial (free, no ads,
    no in-app purchases) and be shared under CC BY-NC-SA 4.0. Play allows free non-commercial apps.
  - The backbone is **DINOv3**, under the DINOv3 License. Redistribution and derivatives are
    allowed if the licence text goes along with them and "Built with DINOv3" is shown. The About
    screen and the dataset card now do both.
  - Wikipedia text is CC BY-SA 4.0. Commons thumbnails carry per-file licences, which are not yet
    recorded. GeoNames is CC BY 4.0. Every manifest component has `license` and `attribution`.
  - Open: `core/species.db` mixes CC BY-SA text with CC BY-NC-SA data, two ShareAlike licences that
    are not formally compatible. Settle this, or split the component, before the repo is made
    public. `packs publish` creates the repo as private by default.

## Target architecture

```
tools/  packs package  ──►  packs/<version>/        (as today)
        packs publish  ──►  HF repo  paulbauriegel/pilzscout-pack-de
                              catalog.json                      (on main, the only mutable file)
                              <version>/manifest.json
                              <version>/model/mushroom_model_*.tflite, labels.txt, model.json
                              <version>/core/species.db
                              <version>/wiki.tar                (one archive instead of 1847 files)
                              <version>/fungitastic.tar         (one archive instead of 4476 files)
                              <version>/images-hd.tar           (optional, never bundled)
                            + git tag  pack-<version>

app     RemotePackSource ──► https://huggingface.co/<repo>/resolve/<commit-sha>/<version>/...
        PackRepository   ──► picks the newest compatible pack from catalog.json
        PackInstaller    ──► downloads (resumable), verifies SHA-256, unpacks into a staging dir, swaps it in
```

### Design decisions

1. **Use one HF repo as a dataset repo.** HF serves files through
   `/resolve/<revision>/<path>`, which redirects to a CDN that supports HTTP `Range`, so downloads
   can resume. The app always resolves files by **commit SHA**, never by `main`. The bytes behind a
   manifest therefore never change, and the SHA-256 in the manifest stays valid. Only
   `catalog.json` is read from `main`.
2. **Use archives for the thumbnail components.** About 6,300 small files would mean 6,300 HTTP
   round trips, and anonymous `resolve` calls are rate-limited per IP. Package `wiki` and
   `fungitastic` as one uncompressed `.tar` each (WebP is already compressed). The manifest keeps
   the per-file list and hashes, so verification after unpacking works as it does today.
3. **Check compatibility in the catalog.** `species.db` must match the Room schema
   (`SpeciesDatabase.VERSION`, currently 4) and the model must match what `MushroomClassifier`
   understands. `catalog.json` lists packs with `schemaVersion`, `modelFormat` and `minAppVersion`.
   The app installs the newest pack it can read. An old app version keeps working on an older pack
   instead of breaking on a newer database.
4. **Swap packs atomically.** Install an update into `filesDir/packs/.staging/<component>/` and
   rename it over the old directory only when every file has passed verification. A failed or
   cancelled update then leaves the working pack in place. Today `PackInstaller` overwrites files
   in place, which is fine for local assets but not for a flaky network.
5. **Halve the model download.** Ship the fp16 export. It is about half the size, and on the
   Pixel 7 Pro GPU it is about 1.7 times faster. `package.py` already supports `prefer_fp16`.
   Before switching, check accuracy against `test_vectors.json`.

## Work plan

### Phase 1: Publishing (tools/)

- `packs package`: write `wiki.tar`, `fungitastic.tar` and `images-hd.tar` next to the loose files.
  Add to each component a `download` entry `{path, sha256, bytes, kind: "file"|"tar"}`. Keep
  `files[]` for verification after unpacking. Raise the manifest `schemaVersion` of the format,
  not the Room one, and use a separate `formatVersion` field.
- `packs publish --version X [--repo paulbauriegel/pilzscout-pack-de]`: use `huggingface_hub.upload_folder` to
  upload `packs/X/`, tag the commit `pack-X`, then update `catalog.json` on `main` with the new
  entry and its commit SHA. The token comes from `HF_TOKEN`, never from the repo.
- Generate a dataset card (`README.md` in the HF repo) with the licence of each component, the
  sources and the attribution.
- Tests: a manifest round trip, and a check that the tar contents match `files[]`.

### Phase 2: App download path (app/pack/)

- `PackManifest` (core): add `formatVersion`, a per-component `download` and `minAppVersion`. Add
  a `PackCatalog` model. Keep it backwards compatible with the bundled manifest.
- `RemotePackSource`: use `HttpURLConnection` or OkHttp (there is no HTTP client dependency yet,
  and OkHttp is the usual choice). It fetches `catalog.json`, then the pinned manifest. `download()`
  streams to `cacheDir/packs-dl/<sha>.part`, sends a `Range` header to resume, and verifies the
  SHA-256 at the end.
- `PackInstaller`: unpack tar archives (a minimal ustar reader is about 60 lines, or use
  commons-compress), install through the staging directory and swap atomically, then verify
  `files[]`.
- `PackInstallWorker`: add a `NetworkType.CONNECTED` constraint (`UNMETERED` when "Wi-Fi only" is
  set), retry with exponential backoff, and show byte progress in the notification instead of an
  indeterminate bar.
- `PackRepository`: on start, read what is installed (it works offline as today). At most once a
  day, and only with a network connection, check the catalog for updates. Required components
  (model, core) install automatically on first launch after the user agrees. Optional ones (wiki,
  fungitastic, images-hd) are offered with their sizes. Updates of installed components are
  offered, not forced over mobile data.
- UI: a first-launch screen ("download Germany pack, 140 MB, Wi-Fi recommended"), a Wi-Fi-only
  setting, an update badge in Settings, and an offline error state with a retry button.

### Phase 3: Build variants

- Add the product flavours `bundled` (`AssetPackSource`, as today, for offline development and
  the emulator) and `play` (`RemotePackSource`, no pack assets). Only the `play` AAB goes to the
  Play Console. Have `packs package --copy-to-assets` write into `app/src/bundled/assets/`.
- Keep `applicationId`, signing and Room versions unchanged for the build that goes onto the
  Pixel (see below).

### Phase 4: Play release

- Create an upload key and enrol in Play App Signing. Bump `versionCode` with every upload.
- Write the privacy policy, fill in the Data safety form and the FGS `dataSync` declaration, add
  the licence and attribution screen, and set up the store listing with disclaimers.
- Run internal testing, then closed testing (new personal developer accounts need 12 testers for
  14 days before production), then production.

## Risk: the Pixel 7 Pro's existing install

The current install on the Pixel is signed with the debug key. A build installed from Play is
signed with the Play key, and Android refuses to update one signature over another. Switching that
phone to the Play version would therefore mean uninstalling, which deletes the observation history.
Options, in order of preference:

1. Keep the debug install as is, and install the Play build next to it under a distinct
   `applicationId` suffix while testing.
2. Before any switch, back up the data with
   `adb shell run-as de.pilzscout.app tar cf - files/observations databases datastore > backup.tar`.
   A history import would need to exist first, because the ZIP export has no import yet.

Moving pack files from assets to downloads does not touch `files/observations`, `databases/` or
`datastore/`. An in-place update of the debug build onto the `bundled` flavour stays safe.

## Open questions

- Does the FungiTastic licence, and the licence of the base model, allow public redistribution?
- Should the repo be public, or gated behind an HF token baked into the app? Public is simpler,
  and an embedded token protects nothing.
- Is HF a good enough CDN in the long run, or should the catalog later point to a second mirror
  (Cloudflare R2, GitHub Releases)? A `baseUrls` list in `catalog.json` keeps that open.
