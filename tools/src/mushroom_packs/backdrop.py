"""Layered forest backdrop artwork for the app, rendered with Pillow.

Produces three transparent layers (far, mid, near) for the light and the dark theme as lossless
WebP files under app/src/main/res/drawable-nodpi/. The Compose side (ParallaxForestBackdrop)
tiles every layer horizontally and shifts it with the home pager, so each layer must wrap
seamlessly: hills are sums of sines with integer harmonics over the full width (periodic by
construction) and every shape placed along a ridge is drawn at x, x - W and x + W.

Each parallax layer holds several ridges of increasing darkness with clusters of firs along the
crests, haze between the far ridges, and a dense tree line with bushes and a few mushroom
silhouettes in front. Geometry depends only on the seed, never on the theme, so the light and
dark sets line up exactly.
"""
from __future__ import annotations

import math
import random
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

from PIL import Image, ImageChops, ImageDraw

from .config import Config

SUPERSAMPLE = 4
DEFAULT_WIDTH = 2800
DEFAULT_HEIGHT = 700
DEFAULT_SEED = 20260913
LAYERS = ("far", "mid", "near")
THEMES = ("light", "dark")

RGB = tuple[int, int, int]

STYLE = {
    "light": dict(
        ridge_far=(0xBC, 0xCE, 0xB3), ridge_near=(0x2C, 0x4F, 0x34), mist=(255, 255, 255), mist_alpha=80,
        mushroom=(0xEF, 0xE6, 0xCF), page=(0xF6, 0xF2, 0xE8),
    ),
    "dark": dict(
        ridge_far=(0x3C, 0x4E, 0x3F), ridge_near=(0x18, 0x2B, 0x1E), mist=(170, 190, 172), mist_alpha=26,
        mushroom=(0xB9, 0xB1, 0x98), page=(0x17, 0x14, 0x0F),
    ),
}

RIDGE_COUNT = 6


@dataclass(frozen=True)
class Ridge:
    depth: int  # 0 = farthest .. RIDGE_COUNT - 1 = nearest, drives the colour
    base: float  # crest line as a fraction of the height
    amp: float
    harmonics: tuple[int, ...]
    tree_height: tuple[float, float]  # fraction of the height
    tree_spacing: tuple[float, float]  # pixels at the final scale, inside a cluster
    clusters: tuple[int, int]  # number of tree clusters along the tile
    cluster_width: float  # fraction of the width
    stray: float  # probability of a lone tree between clusters
    mist: bool = False  # haze band over the crest
    dense: bool = False  # continuous tree line instead of clusters


@dataclass(frozen=True)
class LayerSpec:
    ridges: tuple[Ridge, ...]
    fade: tuple[float, float] | None = None  # alpha ramp (start, end) fractions from the top
    ground: bool = False  # solid ground to the bottom edge with bushes and mushrooms


SPECS = {
    "far": LayerSpec(
        ridges=(
            Ridge(0, 0.30, 0.06, (1, 2, 3), (0.05, 0.08), (6, 11), (4, 7), 0.04, 0.03, mist=True),
            Ridge(1, 0.38, 0.05, (1, 3, 4), (0.06, 0.10), (7, 12), (4, 7), 0.045, 0.04, mist=True),
            Ridge(2, 0.46, 0.05, (2, 3, 5), (0.07, 0.12), (8, 14), (4, 6), 0.045, 0.04, mist=True),
        ),
        fade=(0.16, 0.34),
    ),
    "mid": LayerSpec(
        ridges=(
            Ridge(3, 0.55, 0.05, (2, 4, 5), (0.10, 0.17), (10, 17), (4, 6), 0.05, 0.05),
            Ridge(4, 0.63, 0.045, (3, 4, 7), (0.13, 0.21), (12, 20), (4, 6), 0.055, 0.06),
        ),
    ),
    "near": LayerSpec(
        ridges=(Ridge(5, 0.74, 0.02, (3, 5, 8), (0.16, 0.32), (24, 44), (0, 0), 0.0, 0.0, dense=True),),
        ground=True,
    ),
}

Profile = Callable[[float], float]


def lerp(a: RGB, b: RGB, t: float) -> RGB:
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))  # type: ignore[return-value]


def shade(rgb: RGB, factor: float) -> RGB:
    """Lighten (factor > 0) or darken (factor < 0) a colour by a fraction."""
    return tuple(max(0, min(255, round(c * (1 + factor)))) for c in rgb)  # type: ignore[return-value]


def ridge_color(style: dict, depth: int) -> RGB:
    return lerp(style["ridge_far"], style["ridge_near"], depth / (RIDGE_COUNT - 1))


def hill_profile(rng: random.Random, width: float, base: float, amp: float, harmonics: tuple[int, ...]) -> Profile:
    """Rolling ridge line y(x) that is exactly periodic in `width` (integer harmonics only)."""
    parts = [(amp * rng.uniform(0.45, 1.0) / (i + 1), k, rng.uniform(0, 2 * math.pi)) for i, k in enumerate(harmonics)]
    return lambda x: base - sum(a * math.sin(2 * math.pi * k * x / width + p) for a, k, p in parts)


def fir_polygons(x: float, y_base: float, h: float, tiers: int, rng: random.Random) -> list[list[tuple[float, float]]]:
    """A fir silhouette: short trunk plus stacked tiers with drooping branch tips, widening downwards."""
    trunk = max(h * 0.03, 2.0)
    polys = [[(x - trunk, y_base + h * 0.02), (x + trunk, y_base + h * 0.02), (x + trunk, y_base - h * 0.3), (x - trunk, y_base - h * 0.3)]]
    top = y_base - h
    step = h * 0.88 / tiers
    lean = rng.uniform(-0.04, 0.04) * h
    for i in range(tiers):
        ty = top + i * step
        half = h * (0.11 + 0.075 * i) * rng.uniform(0.85, 1.15)
        cx = x + lean * (i / tiers)
        polys.append([
            (cx, ty),
            (cx - half, ty + step * 1.55),
            (cx - half * 0.5, ty + step * 1.3),
            (cx, ty + step * 1.5),
            (cx + half * 0.5, ty + step * 1.3),
            (cx + half, ty + step * 1.55),
        ])
    return polys


def _draw_hill(draw: ImageDraw.ImageDraw, profile: Profile, width: int, height: int, color: RGB) -> None:
    step = 2 * SUPERSAMPLE
    points = [(x, profile(x)) for x in range(0, width + step, step)]
    points[-1] = (width, profile(width))
    draw.polygon([(0, height), *points, (width, height)], fill=color)


def _wrapped(draw: ImageDraw.ImageDraw, poly: list[tuple[float, float]], width: int, color: RGB) -> None:
    for dx in (0, -width, width):
        draw.polygon([(px + dx, py) for px, py in poly], fill=color)


def _wrapped_ellipse(draw: ImageDraw.ImageDraw, box: tuple[float, float, float, float], width: int, color: RGB) -> None:
    x0, y0, x1, y1 = box
    for dx in (0, -width, width):
        draw.ellipse((x0 + dx, y0, x1 + dx, y1), fill=color)


def _cluster_density(rng: random.Random, ridge: Ridge, width: int) -> Callable[[float], float]:
    """Periodic 0..1 tree density along the tile: bumps around cluster centres, `stray` elsewhere."""
    if ridge.dense:
        return lambda x: 1.0
    centres = [rng.uniform(0, width) for _ in range(rng.randint(*ridge.clusters))]
    sigma = ridge.cluster_width * width / 2

    def density(x: float) -> float:
        best = 0.0
        for c in centres:
            d = abs(x - c) % width
            d = min(d, width - d)
            best = max(best, math.exp(-(d * d) / (2 * sigma * sigma)))
        return max(ridge.stray, best)

    return density


def _draw_trees(draw: ImageDraw.ImageDraw, profile: Profile, ridge: Ridge, width: int, height: int, color: RGB, rng: random.Random) -> None:
    lo, hi = (s * SUPERSAMPLE for s in ridge.tree_spacing)
    density = _cluster_density(rng, ridge, width)
    x = rng.uniform(0, hi)
    while x < width:
        if rng.random() < density(x):
            h = rng.uniform(*ridge.tree_height) * height
            tint = shade(color, rng.uniform(-0.05, 0.05))
            y_base = profile(x) + h * 0.06
            if ridge.dense and rng.random() < 0.12:
                _draw_round_tree(draw, x, y_base, h * 0.7, width, tint)
            else:
                for poly in fir_polygons(x, y_base, h, rng.randint(4, 6) if ridge.dense else rng.randint(3, 5), rng):
                    _wrapped(draw, poly, width, tint)
        x += rng.uniform(lo, hi)


def _draw_round_tree(draw: ImageDraw.ImageDraw, x: float, y_base: float, h: float, width: int, color: RGB) -> None:
    trunk = max(h * 0.04, 2.0)
    _wrapped(draw, [(x - trunk, y_base), (x + trunk, y_base), (x + trunk, y_base - h * 0.5), (x - trunk, y_base - h * 0.5)], width, color)
    r = h * 0.32
    cy = y_base - h * 0.62
    _wrapped_ellipse(draw, (x - r, cy - r * 1.1, x + r, cy + r * 0.9), width, color)
    _wrapped_ellipse(draw, (x - r * 0.75, cy - r * 1.5, x + r * 0.55, cy - r * 0.1), width, color)


def _draw_bushes(draw: ImageDraw.ImageDraw, profile: Profile, width: int, height: int, color: RGB, rng: random.Random) -> None:
    x = rng.uniform(0, 40 * SUPERSAMPLE)
    while x < width:
        y = profile(x) + 2 * SUPERSAMPLE
        r = rng.uniform(0.02, 0.045) * height
        for k in range(rng.randint(2, 4)):
            bx = x + (k - 1) * r * 0.9
            br = r * rng.uniform(0.7, 1.0)
            _wrapped_ellipse(draw, (bx - br, y - br * 1.2, bx + br, y + br * 0.6), width, shade(color, rng.uniform(-0.04, 0.04)))
        x += rng.uniform(40, 110) * SUPERSAMPLE


def _draw_mushrooms(draw: ImageDraw.ImageDraw, profile: Profile, width: int, height: int, color: RGB, rng: random.Random) -> None:
    """A few small groups of mushroom silhouettes standing on the ground in front of the tree line."""
    for _ in range(rng.randint(2, 3)):
        gx = rng.uniform(0, width)
        for k in range(rng.randint(2, 3)):
            h = rng.uniform(0.035, 0.06) * height
            x = gx + k * h * 0.9 + rng.uniform(-h * 0.2, h * 0.2)
            y = profile(x) + 2 * SUPERSAMPLE
            cap_w = h * rng.uniform(0.75, 0.95)
            cap_h = h * 0.5
            stem = h * 0.09
            _wrapped(draw, [(x - stem, y), (x + stem, y), (x + stem * 0.8, y - h * 0.6), (x - stem * 0.8, y - h * 0.6)], width, color)
            for dx in (0, -width, width):
                draw.chord((x + dx - cap_w / 2, y - h, x + dx + cap_w / 2, y - h + cap_h * 2), 180, 360, fill=color)


def _vertical_ramp(width: int, height: int, value_at: Callable[[float], float]) -> Image.Image:
    """An L image whose rows follow value_at(y / height) in 0..1."""
    rows = bytes(max(0, min(255, round(255 * value_at(y / height)))) for y in range(height))
    return Image.frombytes("L", (1, height), rows).resize((width, height), Image.NEAREST)


def _apply_mist(img: Image.Image, width: int, height: int, color: RGB, alpha: int, top: float, bottom: float) -> None:
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
    w, h = width * SUPERSAMPLE, height * SUPERSAMPLE
    rng = random.Random(f"{seed}:{layer}")
    # Transparent pixels carry the layer colour so the downsampling filter never blends towards black.
    img = Image.new("RGBA", (w, h), (*ridge_color(style, spec.ridges[0].depth), 0))
    draw = ImageDraw.Draw(img)

    last_profile: Profile | None = None
    last_color: RGB = ridge_color(style, spec.ridges[-1].depth)
    for ridge in spec.ridges:
        color = ridge_color(style, ridge.depth)
        if ridge.mist:
            _apply_mist(img, w, h, style["mist"], style["mist_alpha"], ridge.base - 0.12, ridge.base + 0.05)
        profile = hill_profile(rng, w, ridge.base * h, ridge.amp * h, ridge.harmonics)
        _draw_hill(draw, profile, w, h, color)
        _draw_trees(draw, profile, ridge, w, h, color, rng)
        last_profile, last_color = profile, color

    if spec.ground and last_profile is not None:
        _draw_bushes(draw, last_profile, w, h, last_color, rng)
        _draw_mushrooms(draw, last_profile, w, h, style["mushroom"], rng)

    out = img.resize((width, height), Image.LANCZOS)
    if spec.fade:
        _apply_fade(out, *spec.fade)
    return out


def preview(layers: dict[str, Image.Image], page: RGB) -> Image.Image:
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
