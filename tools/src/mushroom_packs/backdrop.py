"""Layered forest backdrop artwork for the app, rendered with Pillow.

Produces three transparent layers (far, mid, near) for the light and the dark theme as lossless
WebP files under app/src/main/res/drawable-nodpi/. The Compose side (ParallaxForestBackdrop)
tiles every layer horizontally and shifts it with the home pager, so each layer must wrap
seamlessly: hills are sums of sines with integer harmonics over the full width (periodic by
construction) and every tree is drawn at x, x - W and x + W.

Geometry depends only on the seed and the layer, never on the theme, so the light and dark sets
line up exactly. The palette mirrors the greens that used to live in the app's ForestColors.
"""
from __future__ import annotations

import math
import random
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from PIL import Image, ImageChops, ImageDraw

from .config import Config

SUPERSAMPLE = 4
DEFAULT_WIDTH = 2400
DEFAULT_HEIGHT = 600
DEFAULT_SEED = 20260913
LAYERS = ("far", "mid", "near")
THEMES = ("light", "dark")

PALETTE = {
    "light": {"far": "B9CBB0", "mid": "8AA983", "near": "4F7A52"},
    "dark": {"far": "2A3A2C", "mid": "223125", "near": "17261A"},
}
STYLE = {
    "light": dict(tree_shade=-0.11, mountain_shade=0.09, ground_shade=-0.07, mist=(255, 255, 255), mist_alpha=120, page=(0xF6, 0xF2, 0xE8)),
    "dark": dict(tree_shade=0.14, mountain_shade=0.12, ground_shade=-0.12, mist=(190, 205, 190), mist_alpha=30, page=(0x17, 0x14, 0x0F)),
}


@dataclass(frozen=True)
class LayerSpec:
    hill_base: float  # fraction of the height
    hill_amp: float
    harmonics: tuple[int, ...]
    tree_height: tuple[float, float]  # fraction of the height
    tree_spacing: tuple[float, float]  # pixels at the final scale
    tiers: tuple[int, int]
    mountains: bool = False  # extra ridge without trees behind the hill
    mist: bool = False
    fade: bool = False  # fade the top edge out so the layer blends into the page background
    ground: bool = False  # darker strip along the bottom edge


SPECS = {
    "far": LayerSpec(0.54, 0.06, (1, 2, 3), (0.08, 0.14), (24, 40), (3, 4), mountains=True, mist=True, fade=True),
    "mid": LayerSpec(0.68, 0.06, (2, 3, 5), (0.16, 0.26), (30, 50), (4, 5)),
    "near": LayerSpec(0.83, 0.04, (3, 5, 8), (0.24, 0.38), (40, 66), (5, 6), ground=True),
}

Profile = Callable[[float], float]


def hex_rgb(value: str) -> tuple[int, int, int]:
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))  # type: ignore[return-value]


def shade(rgb: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    """Lighten (factor > 0) or darken (factor < 0) a colour by a fraction."""
    return tuple(max(0, min(255, round(c * (1 + factor)))) for c in rgb)  # type: ignore[return-value]


def hill_profile(rng: random.Random, width: float, base: float, amp: float, harmonics: tuple[int, ...]) -> Profile:
    """Rolling ridge line y(x) that is exactly periodic in `width` (integer harmonics only)."""
    parts = [(amp * rng.uniform(0.45, 1.0) / (i + 1), k, rng.uniform(0, 2 * math.pi)) for i, k in enumerate(harmonics)]
    return lambda x: base - sum(a * math.sin(2 * math.pi * k * x / width + p) for a, k, p in parts)


def fir_polygons(x: float, y_base: float, h: float, tiers: int, rng: random.Random) -> list[list[tuple[float, float]]]:
    """A fir silhouette: a short trunk plus stacked triangular tiers that widen towards the ground."""
    trunk = max(h * 0.035, 2.0)
    polys = [[(x - trunk, y_base + h * 0.02), (x + trunk, y_base + h * 0.02), (x + trunk, y_base - h * 0.25), (x - trunk, y_base - h * 0.25)]]
    top = y_base - h
    step = h * 0.86 / tiers
    for i in range(tiers):
        ty = top + i * step
        half = h * (0.13 + 0.085 * i) * rng.uniform(0.9, 1.1)
        polys.append([(x, ty), (x - half, ty + step * 1.5), (x + half, ty + step * 1.5)])
    return polys


def _draw_hill(draw: ImageDraw.ImageDraw, profile: Profile, width: int, height: int, color: tuple[int, int, int]) -> None:
    step = 2 * SUPERSAMPLE
    points = [(x, profile(x)) for x in range(0, width + step, step)]
    points[-1] = (width, profile(width))
    draw.polygon([(0, height), *points, (width, height)], fill=color)


def _draw_trees(draw: ImageDraw.ImageDraw, profile: Profile, spec: LayerSpec, width: int, height: int, base: tuple[int, int, int], tree_shade: float, rng: random.Random) -> None:
    lo, hi = (s * SUPERSAMPLE for s in spec.tree_spacing)
    x = rng.uniform(0, hi)
    while x < width:
        h = rng.uniform(*spec.tree_height) * height
        color = shade(base, tree_shade * rng.uniform(0.6, 1.4))
        y_base = profile(x) + h * 0.06  # sunk into the hill so the trunk stays hidden
        for poly in fir_polygons(x, y_base, h, rng.randint(*spec.tiers), rng):
            for dx in (0, -width, width):
                draw.polygon([(px + dx, py) for px, py in poly], fill=color)
        x += rng.uniform(lo, hi)


def _vertical_ramp(width: int, height: int, value_at: Callable[[float], float]) -> Image.Image:
    """An L image whose rows follow value_at(y / height) in 0..1."""
    rows = bytes(max(0, min(255, round(255 * value_at(y / height)))) for y in range(height))
    return Image.frombytes("L", (1, height), rows).resize((width, height), Image.NEAREST)


def _apply_mist(img: Image.Image, width: int, height: int, color: tuple[int, int, int], alpha: int, top: float, bottom: float) -> None:
    y0, y1 = int(top * height), int(bottom * height)
    band = y1 - y0
    mask = _vertical_ramp(width, band, lambda t: (alpha / 255) * math.sin(math.pi * t))
    img.paste(Image.new("RGB", (width, band), color), (0, y0), mask)


def _apply_fade(img: Image.Image, start: float, end: float) -> None:
    w, h = img.size
    ramp = _vertical_ramp(w, h, lambda t: min(1.0, max(0.0, (t - start) / (end - start))))
    img.putalpha(ImageChops.multiply(img.getchannel("A"), ramp))


def render_layer(theme: str, layer: str, width: int = DEFAULT_WIDTH, height: int = DEFAULT_HEIGHT, seed: int = DEFAULT_SEED) -> Image.Image:
    spec = SPECS[layer]
    style = STYLE[theme]
    base = hex_rgb(PALETTE[theme][layer])
    w, h = width * SUPERSAMPLE, height * SUPERSAMPLE
    rng = random.Random(f"{seed}:{layer}")
    # Transparent pixels carry the layer colour so the downsampling filter never blends towards black.
    img = Image.new("RGBA", (w, h), (*base, 0))
    draw = ImageDraw.Draw(img)

    if spec.mountains:
        ridge = hill_profile(rng, w, (spec.hill_base - 0.16) * h, 0.15 * h, (1, 2, 4, 7))
        _draw_hill(draw, ridge, w, h, shade(base, style["mountain_shade"]))
    if spec.mist:
        _apply_mist(img, w, h, style["mist"], style["mist_alpha"], spec.hill_base - 0.16, spec.hill_base + 0.10)
    profile = hill_profile(rng, w, spec.hill_base * h, spec.hill_amp * h, spec.harmonics)
    _draw_hill(draw, profile, w, h, base)
    _draw_trees(draw, profile, spec, w, h, base, style["tree_shade"], rng)
    if spec.ground:
        draw.rectangle([(0, int(0.95 * h)), (w, h)], fill=shade(base, style["ground_shade"]))

    out = img.resize((width, height), Image.LANCZOS)
    if spec.fade:
        _apply_fade(out, 0.16, 0.46)
    return out


def preview(layers: dict[str, Image.Image], page: tuple[int, int, int]) -> Image.Image:
    """The three layers composited over the page colour and tiled twice, to check the seam and the look."""
    width, height = layers["far"].size
    canvas = Image.new("RGBA", (2 * width, height), (*page, 255))
    for name in LAYERS:
        for dx in (0, width):
            canvas.alpha_composite(layers[name], (dx, 0))
    return canvas


def run(cfg: Config, out: Path | None = None, width: int = DEFAULT_WIDTH, height: int = DEFAULT_HEIGHT, seed: int = DEFAULT_SEED, write_preview: bool = False) -> list[Path]:
    out = out or cfg.res_dir / "drawable-nodpi"
    out.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for theme in THEMES:
        layers = {layer: render_layer(theme, layer, width, height, seed) for layer in LAYERS}
        for layer, img in layers.items():
            path = out / f"forest_{layer}_{theme}.webp"
            img.save(path, "WEBP", lossless=True, quality=100, method=6)
            written.append(path)
        if write_preview:
            preview_dir = cfg.cache_dir / "build" / "backdrop"
            preview_dir.mkdir(parents=True, exist_ok=True)
            path = preview_dir / f"preview_{theme}.png"
            preview(layers, STYLE[theme]["page"]).save(path)
            written.append(path)
    return written
