# View-type model (planned)

On the Identify tab the view of a photo (cap, underside, stem base, habitat) is optional and set by
hand: with chips in the camera before capture, or in the photo detail sheet. Every photo entering the
draft goes through `PhotoIntake` (`app/src/main/java/de/pilzscout/app/identify/PhotoIntake.kt`). For
photos without a chosen view it asks a `ViewTypeDetector` for a guess, which is applied only while the
photo is still untyped, so a guess never overrides the user. Today the only binding is
`NoOpViewTypeDetector` (`di/MlModule.kt`). This document is the plan for the real detector.

## Goal

Classify a single mushroom photo into the four views the identification flow cares about
(`CAP`, `UNDERSIDE`, `STEM_BASE`, `HABITAT`) with a confidence, on device, in well under 100 ms,
so the tab can tick a coverage checklist automatically and guide the user to the missing view.

## Training data

FungiTastic ships instance segmentation masks for cap, gills/pores, stem and background context.
Derive view labels from the masks rather than hand-labelling:

- `UNDERSIDE`: gills/pores mask covers a large share of the fruit body area.
- `STEM_BASE`: stem mask reaches the bottom of the fruit body and the base region is visible.
- `CAP`: cap mask dominates and gills/pores are (almost) absent.
- `HABITAT`: fruit body occupies a small fraction of the frame, context dominates.

Photos that fit several rules get multi-label targets; the detector reports the strongest one.

## Model

Train RF-DETR seg nano on the FungiTastic masks (part segmentation: cap, gills/pores, stem, base).
The view label is then a small rule set on the predicted masks, the same rules as above, which keeps
the label semantics identical between training data and inference. Export the segmentation backbone
to LiteRT (`.tflite`, int8 where accuracy allows), matching the species classifier's preprocessing
(`ml/ImagePreprocessor.kt`).

Evaluation: hold out by observation, report per-view precision/recall and the share of photos with
no confident view (the future "Prüfen" state).

## Packaging

Add a `view` component to the offline pack next to the species model (`model/view.json` +
`model/view.tflite`), loaded lazily by a `LiteRtViewTypeDetector` that implements
`ViewTypeDetector.detect(file)` and returns `ViewTypeGuess(viewType, confidence)` or `null` below a
confidence threshold. Bind it in `MlModule` instead of the no-op.

## UI once the detector exists

- Recognised views fill the badges on untyped photos automatically; low-confidence guesses show as
  „Prüfen“ and are confirmed or corrected with the existing chips in the photo detail sheet.
- A compact coverage checklist (Hut, Unterseite, Stiel und Basis, Umgebung) can then be derived from
  the badges.
- `CaptureGuidance` keys off the covered views instead of the photo count, and the camera hint asks
  for the most useful missing view; tapping a checklist item makes it the next requested view.
- The "identify" hint reports the missing important view („Die Stielbasis ist noch nicht sichtbar“).
