#!/usr/bin/env python3
"""Generate Stardust Industry's electrical pixel art and model mappings.

The artwork deliberately stays on Minecraft's 16 px grid.  Every generated
texture is RGBA, uses hard pixel edges, and is deterministic: any tiny surface
variation is seeded from sha256(resource_name), never process-global RNG state.

Run from anywhere:

    python3 tools/generate_electrical_art.py

Preview sheets and a sha256 manifest are written below
``build/reports/textures``.  Game resources are written in place.
"""

from __future__ import annotations

import hashlib
import json
import math
import random
import textwrap
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
ASSETS = (
    ROOT
    / "StardustIndustry-Core"
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "stardustindustry"
)
BLOCK_TEXTURES = ASSETS / "textures" / "block"
ITEM_TEXTURES = ASSETS / "textures" / "item"
BLOCK_MODELS = ASSETS / "models" / "block"
ITEM_MODELS = ASSETS / "models" / "item"
BLOCKSTATES = ASSETS / "blockstates"
REPORTS = ROOT / "build" / "reports" / "textures"


STEEL = {
    "black": "#14191B",
    "shadow": "#22292C",
    "dark": "#343E42",
    "mid": "#536065",
    "light": "#7B8789",
    "highlight": "#AAB3B2",
    "white": "#D1D6D3",
}
COPPER = ("#6F3A1E", "#A85C2E", "#D18449", "#F0B56B")
CERAMIC = ("#5F6461", "#92958D", "#D1D0C0", "#F0EBD7")
STATUS_ON = ("#1D603B", "#4FCF78", "#A4F7B7")
STATUS_OFF = ("#5B2528", "#C84B52", "#F08A83")

COPPER_AMPACITIES = (50, 150, 500, 1500, 5000)
ACSR_AMPACITIES = (800, 1600)
CABLE_ICON_WIDTHS = {
    50: 1,
    150: 2,
    500: 3,
    800: 3,
    1500: 4,
    1600: 4,
    5000: 5,
}

TIERS = {
    "lv": {
        "rank": 1,
        "dark": "#176B58",
        "mid": "#2FCF99",
        "light": "#75F0C9",
    },
    "mv": {
        "rank": 2,
        "dark": "#856C22",
        "mid": "#E8C85C",
        "light": "#FFE58A",
    },
    "hv": {
        "rank": 3,
        "dark": "#8C3036",
        "mid": "#ED6262",
        "light": "#FF9B92",
    },
    "ehv": {
        "rank": 4,
        "dark": "#743D82",
        "mid": "#D684EC",
        "light": "#F0B2FF",
    },
}


@dataclass(frozen=True)
class Device:
    name: str
    kind: str
    primary: str
    secondary: str | None = None


DEVICES = (
    Device("fuel_generator", "generator", "lv"),
    Device("creative_power_source", "creative", "lv"),
    Device("transformer_lv_mv", "transformer", "lv", "mv"),
    Device("transformer_mv_hv", "transformer", "mv", "hv"),
    Device("transformer_hv_ehv", "transformer", "hv", "ehv"),
    Device("lv_breaker", "breaker", "lv"),
    Device("mv_breaker", "breaker", "mv"),
    Device("hv_breaker", "breaker", "hv"),
    Device("ehv_breaker", "breaker", "ehv"),
    Device("lv_fuse_box", "fuse", "lv"),
    Device("mv_fuse_box", "fuse", "mv"),
    Device("hv_fuse_box", "fuse", "hv"),
    Device("ehv_fuse_box", "fuse", "ehv"),
    Device("lv_fe_converter", "converter", "lv"),
    Device("mv_fe_converter", "converter", "mv"),
    Device("hv_fe_converter", "converter", "hv"),
    Device("ehv_fe_converter", "converter", "ehv"),
)


GENERATED_TEXTURES: list[Path] = []
PREVIEW_GROUPS: dict[str, list[Path]] = {
    "devices": [],
    "infrastructure": [],
    "terminals-and-cables": [],
    "items": [],
}


def seeded_rng(name: str) -> random.Random:
    digest = hashlib.sha256(name.encode("utf-8")).digest()
    return random.Random(int.from_bytes(digest[:8], "big"))


def rgba_image(size: tuple[int, int], fill: str = "#00000000") -> Image.Image:
    return Image.new("RGBA", size, fill)


def draw_panel(name: str, inner: str | None = None) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = rgba_image((16, 16), STEEL["black"])
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 14), fill=STEEL["dark"])
    draw.line((1, 1, 14, 1), fill=STEEL["highlight"])
    draw.line((1, 1, 1, 14), fill=STEEL["light"])
    draw.line((1, 14, 14, 14), fill=STEEL["shadow"])
    draw.line((14, 1, 14, 14), fill=STEEL["black"])
    draw.rectangle((3, 3, 12, 11), fill=inner or STEEL["shadow"])
    for point in ((2, 2), (13, 2), (2, 13), (13, 13)):
        draw.point(point, fill=STEEL["white"])

    # A single seeded tooling mark keeps panels from looking cloned while staying
    # deterministic and intentionally unlike noisy generated art.
    rng = seeded_rng(name)
    draw.point((rng.choice((4, 11)), rng.choice((2, 13))), fill=STEEL["mid"])
    return image, draw


def tier_bars(draw: ImageDraw.ImageDraw, tier: str, y: int = 12) -> None:
    palette = TIERS[tier]
    rank = int(palette["rank"])
    bar_width = 2
    gap = 1
    total = rank * bar_width + (rank - 1) * gap
    start = (16 - total) // 2
    for index in range(rank):
        x = start + index * (bar_width + gap)
        draw.rectangle((x, y, x + bar_width - 1, y + 1), fill=palette["mid"])
        draw.point((x, y), fill=palette["light"])


def status_lamp(draw: ImageDraw.ImageDraw, powered: bool, x: int = 11, y: int = 3) -> None:
    colors = STATUS_ON if powered else STATUS_OFF
    draw.rectangle((x, y, x + 1, y + 1), fill=colors[1])
    draw.point((x, y), fill=colors[2])


def draw_bolt(draw: ImageDraw.ImageDraw, x: int, y: int, color: str) -> None:
    draw.point((x, y), fill=color)
    draw.point((x - 1, y), fill=STEEL["black"])
    draw.point((x + 1, y), fill=STEEL["black"])


def front_texture(device: Device, powered: bool) -> Image.Image:
    tier = TIERS[device.primary]
    image, draw = draw_panel(f"{device.name}:front:{powered}")
    tier_bars(draw, device.primary)

    if device.kind == "breaker":
        draw_bolt(draw, 7, 4, COPPER[3])
        draw_bolt(draw, 7, 10, COPPER[2])
        if powered:
            draw.line((7, 5, 7, 9), fill=tier["light"], width=2)
            draw.point((8, 7), fill=tier["mid"])
        else:
            draw.line((7, 9, 4, 6), fill=COPPER[2], width=2)
            draw.point((4, 5), fill=COPPER[3])
        status_lamp(draw, powered)
    elif device.kind == "fuse":
        draw.rectangle((3, 6, 4, 8), fill=COPPER[2])
        draw.rectangle((11, 6, 12, 8), fill=COPPER[2])
        draw.rectangle((5, 5, 10, 9), fill=CERAMIC[1])
        draw.rectangle((6, 6, 9, 8), fill=CERAMIC[3])
        if powered:
            draw.line((5, 7, 10, 7), fill=tier["light"])
        else:
            draw.point((7, 7), fill=STEEL["black"])
            draw.point((8, 6), fill=STEEL["black"])
            draw.point((8, 8), fill=STEEL["black"])
        status_lamp(draw, powered)
    elif device.kind == "converter":
        active = tier["light"] if powered else STEEL["light"]
        draw.rectangle((3, 5, 6, 9), outline=active)
        draw.rectangle((9, 5, 12, 9), outline=active)
        draw.point((4, 7), fill=active)
        draw.point((11, 7), fill=active)
        draw.line((6, 5, 9, 5), fill=COPPER[2])
        draw.point((8, 4), fill=COPPER[3])
        draw.line((9, 9, 6, 9), fill=COPPER[2])
        draw.point((7, 10), fill=COPPER[3])
        status_lamp(draw, powered)
    elif device.kind == "transformer":
        other = TIERS[device.secondary or device.primary]
        first = tier["light"] if powered else tier["mid"]
        second = other["light"] if powered else other["mid"]
        draw.rectangle((3, 4, 6, 10), fill=STEEL["black"])
        draw.rectangle((9, 4, 12, 10), fill=STEEL["black"])
        for y in (5, 7, 9):
            draw.line((3, y, 6, y), fill=first)
            draw.line((9, y, 12, y), fill=second)
        draw.rectangle((7, 4, 8, 10), fill=STEEL["light"])
        draw.point((7, 4), fill=STEEL["white"])
        status_lamp(draw, powered)
        tier_bars(draw, device.primary, 12)
        draw.rectangle((12, 12, 13, 13), fill=other["mid"])
        draw.point((12, 12), fill=other["light"])
    elif device.kind == "generator":
        blade = tier["light"] if powered else STEEL["light"]
        draw.rectangle((4, 4, 10, 10), outline=STEEL["light"])
        draw.rectangle((6, 6, 8, 8), fill=COPPER[2])
        draw.line((7, 4, 7, 6), fill=blade)
        draw.line((8, 8, 10, 8), fill=blade)
        draw.line((7, 8, 7, 10), fill=blade)
        draw.line((4, 7, 6, 7), fill=blade)
        if powered:
            draw.rectangle((4, 10, 10, 10), fill="#F09A3E")
            draw.point((7, 9), fill="#FFE07A")
        status_lamp(draw, powered)
    elif device.kind == "creative":
        colors = [TIERS[key]["mid"] for key in ("lv", "mv", "hv", "ehv")]
        draw.polygon(((7, 3), (9, 6), (12, 7), (9, 8), (7, 11), (6, 8), (3, 7), (6, 6)),
                     fill=STEEL["white"] if powered else STEEL["light"])
        for point, color in zip(((7, 3), (12, 7), (7, 11), (3, 7)), colors):
            draw.point(point, fill=color)
        draw.rectangle((7, 6, 8, 8), fill="#FFFFFF" if powered else STEEL["highlight"])
        status_lamp(draw, powered)
    else:
        raise ValueError(f"Unknown device kind: {device.kind}")

    return image


def back_texture(device: Device) -> Image.Image:
    image, draw = draw_panel(f"{device.name}:back")
    tier = TIERS[device.primary]
    if device.kind == "transformer":
        other = TIERS[device.secondary or device.primary]
        draw.rectangle((3, 4, 7, 10), fill=STEEL["black"])
        draw.rectangle((8, 4, 12, 10), fill=STEEL["black"])
        draw.rectangle((4, 5, 6, 9), fill=tier["dark"])
        draw.rectangle((9, 5, 11, 9), fill=other["dark"])
        draw.point((5, 7), fill=tier["light"])
        draw.point((10, 7), fill=other["light"])
        tier_bars(draw, device.primary)
        draw.rectangle((12, 12, 13, 13), fill=other["mid"])
    elif device.kind == "generator":
        draw.rectangle((4, 4, 11, 10), fill=STEEL["black"])
        draw.rectangle((5, 5, 10, 7), fill=COPPER[0])
        draw.line((5, 9, 10, 9), fill=STEEL["light"])
        tier_bars(draw, device.primary)
    elif device.kind == "creative":
        for index, key in enumerate(("lv", "mv", "hv", "ehv")):
            x = 4 + (index % 2) * 6
            y = 5 + (index // 2) * 4
            draw.rectangle((x, y, x + 1, y + 1), fill=TIERS[key]["mid"])
        tier_bars(draw, device.primary)
    else:
        draw.rectangle((4, 4, 11, 10), fill=STEEL["black"])
        draw.rectangle((5, 5, 10, 9), outline=tier["dark"])
        draw.rectangle((7, 6, 8, 8), fill=COPPER[2])
        draw.point((7, 6), fill=COPPER[3])
        tier_bars(draw, device.primary)
    return image


def side_texture(device: Device) -> Image.Image:
    image, draw = draw_panel(f"{device.name}:side")
    primary = TIERS[device.primary]
    if device.kind == "transformer":
        secondary = TIERS[device.secondary or device.primary]
        for y, color in ((4, primary["light"]), (6, primary["mid"]),
                         (8, secondary["light"]), (10, secondary["mid"])):
            draw.line((4, y, 11, y), fill=color)
        draw.rectangle((7, 3, 8, 11), fill=STEEL["mid"])
    elif device.kind in ("breaker", "fuse"):
        draw.rectangle((4, 4, 11, 10), outline=STEEL["light"])
        draw.line((6, 4, 6, 10), fill=STEEL["black"])
        draw.line((9, 4, 9, 10), fill=primary["dark"])
    elif device.kind == "generator":
        for y in (4, 6, 8, 10):
            draw.line((4, y, 11, y), fill=STEEL["light"] if y % 4 else STEEL["mid"])
        draw.line((11, 4, 11, 10), fill=COPPER[1])
    elif device.kind == "creative":
        for y, key in zip((4, 6, 8, 10), ("lv", "mv", "hv", "ehv")):
            draw.line((4, y, 11, y), fill=TIERS[key]["mid"])
    else:
        for y in (4, 6, 8, 10):
            draw.line((4, y, 11, y), fill=STEEL["light"])
        draw.rectangle((11, 4, 12, 10), fill=primary["dark"])
    tier_bars(draw, device.primary)
    return image


def top_texture(device: Device) -> Image.Image:
    image, draw = draw_panel(f"{device.name}:top", STEEL["dark"])
    primary = TIERS[device.primary]
    draw.rectangle((4, 4, 11, 10), fill=STEEL["shadow"])
    if device.kind == "transformer":
        secondary = TIERS[device.secondary or device.primary]
        draw.rectangle((4, 5, 6, 9), fill=primary["mid"])
        draw.rectangle((9, 5, 11, 9), fill=secondary["mid"])
        draw.line((7, 5, 8, 9), fill=STEEL["light"])
    elif device.kind in ("generator", "converter"):
        draw.rectangle((5, 5, 10, 9), outline=primary["mid"])
        draw.line((6, 7, 9, 7), fill=primary["light"])
    else:
        draw.rectangle((6, 5, 9, 9), fill=primary["dark"])
        draw.rectangle((7, 6, 8, 8), fill=primary["light"])
    tier_bars(draw, device.primary)
    return image


def chassis_bottom() -> Image.Image:
    image, draw = draw_panel("electrical_chassis_bottom", STEEL["black"])
    draw.rectangle((5, 5, 10, 10), outline=STEEL["mid"])
    draw.rectangle((7, 7, 8, 8), fill=STEEL["dark"])
    return image


def energy_port_texture(tier_name: str, face: str) -> Image.Image:
    tier = TIERS[tier_name]
    image, draw = draw_panel(f"{tier_name}:energy-port:{face}")
    rank = int(tier["rank"])
    if face == "top":
        radius = 3 + (rank >= 3)
        box = (8 - radius, 8 - radius, 7 + radius, 7 + radius)
        draw.ellipse(box, fill=STEEL["black"], outline=tier["dark"])
        draw.rectangle((7, 6, 8, 9), fill=COPPER[2])
        draw.point((7, 6), fill=COPPER[3])
        for index in range(rank):
            angle = (math.tau * index / rank) if rank > 1 else -math.pi / 2
            x = round(7.5 + math.cos(angle) * (radius - 1))
            y = round(7.5 + math.sin(angle) * (radius - 1))
            draw.point((x, y), fill=tier["light"])
        tier_bars(draw, tier_name)
    elif face == "bottom":
        draw.rectangle((4, 4, 11, 11), fill=STEEL["shadow"])
        draw.rectangle((5, 5, 10, 10), outline=tier["dark"])
        draw.rectangle((7, 7, 8, 8), fill=STEEL["black"])
        tier_bars(draw, tier_name)
    elif face == "side":
        draw.rectangle((3, 4, 12, 10), fill=STEEL["shadow"])
        draw.rectangle((3, 6, 12, 8), fill=tier["dark"])
        draw.line((4, 6, 11, 6), fill=tier["light"])
        for x in (4, 7, 10):
            draw.point((x, 9), fill=STEEL["light"])
        tier_bars(draw, tier_name)
    else:
        raise ValueError(face)
    return image


def storage_cell_texture(tier_name: str, face: str) -> Image.Image:
    tier = TIERS[tier_name]
    image, draw = draw_panel(f"{tier_name}:storage-cell:{face}")
    if face == "side":
        for y in (4, 7, 10):
            draw.rectangle((4, y, 11, y + 1), fill=tier["dark"])
            draw.line((5, y, 10, y), fill=tier["light"])
        draw.line((3, 3, 3, 12), fill=COPPER[1])
        draw.line((12, 3, 12, 12), fill=COPPER[2])
        tier_bars(draw, tier_name)
    elif face == "top":
        draw.rectangle((3, 3, 12, 12), fill=STEEL["shadow"])
        draw.line((4, 5, 11, 5), fill=COPPER[2], width=2)
        draw.line((4, 10, 11, 10), fill=COPPER[1], width=2)
        draw.rectangle((4, 7, 5, 8), fill=tier["light"])
        draw.rectangle((10, 7, 11, 8), fill=tier["dark"])
    elif face == "bottom":
        draw.rectangle((3, 3, 12, 12), fill=STEEL["black"])
        draw.rectangle((5, 5, 10, 10), outline=STEEL["mid"])
        tier_bars(draw, tier_name)
    else:
        raise ValueError(face)
    return image


def controller_texture(tier_name: str, face: str) -> Image.Image:
    tier = TIERS[tier_name]
    image, draw = draw_panel(f"{tier_name}:controller:{face}")
    if face == "front":
        draw.rectangle((3, 3, 12, 9), fill="#091315")
        draw.rectangle((4, 4, 10, 5), fill=tier["dark"])
        charge = int(tier["rank"])
        for index in range(4):
            color = tier["light"] if index < charge else STEEL["dark"]
            draw.rectangle((4 + index * 2, 7, 5 + index * 2, 8), fill=color)
        draw.rectangle((11, 4, 11, 5), fill=STATUS_ON[1])
        tier_bars(draw, tier_name)
    elif face == "back":
        draw.rectangle((4, 4, 11, 10), fill=STEEL["black"])
        for x in (5, 8, 11):
            draw.point((x, 6), fill=COPPER[2])
            draw.point((x, 8), fill=tier["mid"])
        tier_bars(draw, tier_name)
    elif face == "side":
        for y in (4, 6, 8, 10):
            draw.line((4, y, 11, y), fill=STEEL["light"])
        draw.rectangle((11, 4, 12, 10), fill=tier["dark"])
        tier_bars(draw, tier_name)
    elif face == "top":
        draw.rectangle((4, 4, 11, 10), fill=STEEL["shadow"])
        draw.line((5, 5, 10, 5), fill=STEEL["light"])
        draw.line((5, 7, 10, 7), fill=tier["mid"])
        draw.line((5, 9, 10, 9), fill=STEEL["light"])
        tier_bars(draw, tier_name)
    elif face == "bottom":
        draw.rectangle((4, 4, 11, 11), fill=STEEL["black"])
        draw.rectangle((6, 6, 9, 9), outline=STEEL["mid"])
        tier_bars(draw, tier_name)
    else:
        raise ValueError(face)
    return image


def station_structure_texture(kind: str) -> Image.Image:
    image, draw = draw_panel(f"storage-station:{kind}", STEEL["dark"])
    if kind == "casing":
        draw.rectangle((3, 3, 12, 12), fill=STEEL["mid"])
        draw.line((3, 3, 12, 3), fill=STEEL["highlight"])
        draw.line((3, 12, 12, 12), fill=STEEL["shadow"])
        draw.line((5, 5, 10, 10), fill=STEEL["light"])
        draw.line((10, 5, 5, 10), fill=STEEL["dark"])
        for point in ((4, 4), (11, 4), (4, 11), (11, 11)):
            draw.point(point, fill="#2FCF99")
    elif kind == "frame":
        draw.rectangle((3, 3, 12, 12), fill=STEEL["black"])
        draw.rectangle((3, 3, 5, 12), fill=STEEL["mid"])
        draw.rectangle((10, 3, 12, 12), fill=STEEL["light"])
        draw.rectangle((3, 3, 12, 5), fill=STEEL["highlight"])
        draw.rectangle((3, 10, 12, 12), fill=STEEL["dark"])
        draw.line((5, 10, 10, 5), fill=STEEL["mid"], width=2)
        draw.point((4, 4), fill="#2FCF99")
        draw.point((11, 11), fill="#2FCF99")
    else:
        raise ValueError(kind)
    return image


def terminal_plate_texture(tier_name: str) -> Image.Image:
    tier = TIERS[tier_name]
    image, draw = draw_panel(f"{tier_name}:terminal-plate", STEEL["dark"])
    rank = int(tier["rank"])
    if rank == 1:
        for y in (4, 6, 9, 11):
            for x in (4, 6, 9, 11):
                draw.point((x, y), fill=tier["mid"])
                draw.point((x + 1, y), fill=STEEL["black"])
    elif rank == 2:
        for y in (5, 9):
            for x in (5, 9):
                draw.rectangle((x, y, x + 1, y + 1), fill=tier["mid"])
                draw.point((x, y), fill=tier["light"])
    elif rank == 3:
        draw.rectangle((4, 4, 11, 11), outline=tier["dark"])
        draw.rectangle((6, 6, 9, 9), fill=STEEL["black"])
        draw.rectangle((7, 7, 8, 8), fill=tier["light"])
    else:
        draw.rectangle((3, 3, 12, 12), outline=tier["dark"])
        draw.polygon(((7, 4), (11, 7), (8, 11), (4, 8)), fill=STEEL["black"])
        draw.rectangle((7, 7, 8, 8), fill=tier["light"])
    tier_bars(draw, tier_name)
    return image


def terminal_surface_texture(tier_name: str, material: str) -> Image.Image:
    tier = TIERS[tier_name]
    image = rgba_image((16, 16))
    draw = ImageDraw.Draw(image)
    if material == "body":
        colors = (CERAMIC[1], CERAMIC[2], CERAMIC[3])
        for y in range(16):
            shade = colors[(y // 3) % len(colors)]
            draw.line((0, y, 15, y), fill=shade)
        draw.rectangle((0, 6, 15, 8), fill=tier["dark"])
        draw.line((0, 6, 15, 6), fill=tier["light"])
    elif material == "metal":
        colors = COPPER if tier_name in ("lv", "hv") else (
            STEEL["shadow"], STEEL["mid"], STEEL["highlight"], STEEL["white"]
        )
        for x in range(16):
            draw.line((x, 0, x, 15), fill=colors[(x // 4) % len(colors)])
        draw.line((0, 3, 15, 3), fill=tier["mid"])
        draw.line((0, 12, 15, 12), fill=tier["dark"])
    else:
        raise ValueError(material)
    return image


def generic_terminal_texture(kind: str) -> Image.Image:
    image, draw = draw_panel(f"terminal:{kind}", STEEL["dark"])
    if kind == "plate":
        draw.rectangle((4, 4, 11, 11), outline=STEEL["light"])
        draw.rectangle((7, 7, 8, 8), fill=COPPER[2])
    elif kind == "seat":
        draw.rectangle((4, 5, 11, 10), fill=STEEL["mid"])
        draw.rectangle((6, 6, 9, 9), fill=STEEL["black"])
    elif kind == "stud":
        for x, color in enumerate(COPPER):
            draw.rectangle((x * 4, 0, x * 4 + 3, 15), fill=color)
    else:
        raise ValueError(kind)
    return image


def cable_texture(kind: str) -> Image.Image:
    image = rgba_image((32, 128), "#A0A0A0")
    pixels = image.load()
    if kind == "bare":
        ramp = (72, 96, 132, 184, 226, 184, 132, 96)
        for y in range(128):
            for x in range(32):
                value = ramp[(x + y // 2) % len(ramp)]
                pixels[x, y] = (value, value, value, 255)
    elif kind == "acsr":
        ramp = (82, 112, 158, 216, 242, 216, 158, 112)
        for y in range(128):
            for x in range(32):
                value = ramp[(x - y // 2) % len(ramp)]
                if (x + 16) % 32 in (0, 1):
                    value = 68
                pixels[x, y] = (value, value, value, 255)
    elif kind in ("class_1", "class_2"):
        for y in range(128):
            for x in range(32):
                value = 176 + (14 if (x // 4) % 2 else 0)
                if x in (0, 1, 30, 31):
                    value = 104
                if kind == "class_2" and x in (15, 16):
                    value = 118
                if y % 32 in (7, 8) and 5 <= x <= 12:
                    value = 226
                if kind == "class_2" and y % 32 in (19, 20) and 19 <= x <= 26:
                    value = 226
                pixels[x, y] = (value, value, value, 255)
    else:
        raise ValueError(kind)
    return image


def cable_item_texture(ampacity: int, insulation: str, acsr: bool = False) -> Image.Image:
    image = rgba_image((16, 16))
    draw = ImageDraw.Draw(image)
    # Ampacity owns the silhouette: the five copper products deliberately use
    # every integer stroke width from one through five. Insulation is encoded by
    # surface marks below, never by making a lower-rated cable look thicker.
    width = CABLE_ICON_WIDTHS[ampacity]
    if acsr:
        shadow = (55, 64, 72, 255)
        base = (225, 232, 238, 255)
        highlight = (255, 255, 255, 255)
    elif insulation == "bare":
        shadow = (105, 49, 23, 255)
        base = (222, 132, 63, 255)
        highlight = (255, 190, 104, 255)
    else:
        shadow = (43, 49, 53, 255)
        base = (98, 107, 113, 255) if insulation == "class_1" else (75, 83, 89, 255)
        highlight = (178, 190, 196, 255)

    draw.arc((1, 1, 12, 12), 15, 345, fill=shadow, width=min(5, width + 1))
    draw.arc((2, 1, 12, 11), 20, 330, fill=base, width=width)
    draw.line((9, 9, 12, 12), fill=shadow, width=min(5, width + 1))
    draw.line((9, 9, 12, 12), fill=base, width=min(4, width))
    draw.point((13, 13), fill=highlight)

    if insulation == "class_1":
        # One broad printed band: readable even on the one-pixel 50 A sheath.
        draw.rectangle((6, 1, 7, 4), fill=highlight)
        draw.rectangle((11, 11, 12, 12), fill=highlight)
    elif insulation == "class_2":
        # Two separated bands remain distinct from Class 1 after GUI scaling.
        draw.rectangle((4, 1, 5, 4), fill=highlight)
        draw.rectangle((9, 1, 10, 4), fill=highlight)
        draw.point((10, 10), fill=highlight)
        draw.rectangle((12, 12, 13, 13), fill=highlight)
    elif acsr:
        # The dark centre wire is the quickest inventory-level cue that this is
        # steel-cored aluminium rather than another copper ampacity.
        core = (38, 43, 48, 255)
        draw.rectangle((6, 1, 7, 3), fill=core)
        draw.point((8, 1), fill=highlight)
        draw.rectangle((2, 7, 3, 8), fill=core)
    else:
        # A single glint reads as exposed metal rather than insulation printing.
        draw.point((8, 1), fill=highlight)
    return image


def terminal_item_texture(tier_name: str) -> Image.Image:
    tier = TIERS[tier_name]
    rank = int(tier["rank"])
    image = rgba_image((16, 16))
    draw = ImageDraw.Draw(image)
    width = 2 + min(rank, 3)
    left = 8 - width // 2
    right = left + width - 1
    top = max(1, 7 - rank)
    draw.rectangle((left - 2, 12, right + 2, 14), fill=STEEL["dark"])
    draw.line((left - 1, 12, right + 1, 12), fill=tier["mid"])
    draw.rectangle((left, top, right, 11), fill=CERAMIC[2])
    draw.line((left, top, right, top), fill=CERAMIC[3])
    for index in range(rank):
        y = top + 2 + index * 2
        if y < 11:
            draw.line((left - 1, y, right + 1, y), fill=tier["dark"])
            draw.point((left - 1, y), fill=tier["light"])
    draw.rectangle((7, max(0, top - 1), 8, top), fill=COPPER[2])
    return image


def save_texture(path: Path, image: Image.Image, group: str) -> None:
    if image.mode != "RGBA":
        raise AssertionError(f"{path.name}: expected RGBA, got {image.mode}")
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)
    GENERATED_TEXTURES.append(path)
    PREVIEW_GROUPS[group].append(path)


def write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")


def model_id(name: str) -> str:
    return f"stardustindustry:block/{name}"


def texture_id(name: str) -> str:
    return f"stardustindustry:block/{name}"


def generate_device_resources() -> None:
    save_texture(
        BLOCK_TEXTURES / "electrical_chassis_bottom.png",
        chassis_bottom(),
        "devices",
    )
    for device in DEVICES:
        for suffix, image in (
            ("front", front_texture(device, False)),
            ("front_powered", front_texture(device, True)),
            ("back", back_texture(device)),
            ("side", side_texture(device)),
            ("top", top_texture(device)),
        ):
            save_texture(BLOCK_TEXTURES / f"{device.name}_{suffix}.png", image, "devices")

        write_json(
            BLOCK_MODELS / f"{device.name}.json",
            {
                "parent": "stardustindustry:block/electrical_orientable",
                "textures": {
                    "bottom": texture_id("electrical_chassis_bottom"),
                    "top": texture_id(f"{device.name}_top"),
                    "front": texture_id(f"{device.name}_front"),
                    "back": texture_id(f"{device.name}_back"),
                    "side": texture_id(f"{device.name}_side"),
                },
            },
        )
        write_json(
            BLOCK_MODELS / f"{device.name}_powered.json",
            {
                "parent": model_id(device.name),
                "textures": {"front": texture_id(f"{device.name}_front_powered")},
            },
        )

        variants: dict[str, object] = {}
        for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for powered in (False, True):
                entry: dict[str, object] = {
                    "model": model_id(device.name + ("_powered" if powered else ""))
                }
                if rotation:
                    entry["y"] = rotation
                variants[f"facing={facing},powered={str(powered).lower()}"] = entry
        write_json(BLOCKSTATES / f"{device.name}.json", {"variants": variants})
        write_json(ITEM_MODELS / f"{device.name}.json", {"parent": model_id(device.name)})


def generate_ports_and_storage() -> None:
    for tier_name in TIERS:
        for face in ("top", "bottom", "side"):
            save_texture(
                BLOCK_TEXTURES / f"{tier_name}_energy_port_{face}.png",
                energy_port_texture(tier_name, face),
                "infrastructure",
            )

        for face in ("side", "top", "bottom"):
            image = storage_cell_texture(tier_name, face)
            save_texture(
                BLOCK_TEXTURES / f"{tier_name}_storage_cell_{face}.png",
                image,
                "infrastructure",
            )
            if face == "side":
                save_texture(
                    BLOCK_TEXTURES / f"{tier_name}_storage_cell.png",
                    image.copy(),
                    "infrastructure",
                )
        write_json(
            BLOCK_MODELS / f"{tier_name}_storage_cell.json",
            {
                "parent": "minecraft:block/cube_bottom_top",
                "textures": {
                    "bottom": texture_id(f"{tier_name}_storage_cell_bottom"),
                    "top": texture_id(f"{tier_name}_storage_cell_top"),
                    "side": texture_id(f"{tier_name}_storage_cell_side"),
                },
            },
        )

        for face in ("front", "back", "side", "top", "bottom"):
            save_texture(
                BLOCK_TEXTURES / f"{tier_name}_storage_station_controller_{face}.png",
                controller_texture(tier_name, face),
                "infrastructure",
            )
        write_json(
            BLOCK_MODELS / f"{tier_name}_storage_station_controller.json",
            {
                "parent": "stardustindustry:block/electrical_orientable",
                "textures": {
                    "bottom": texture_id(f"{tier_name}_storage_station_controller_bottom"),
                    "top": texture_id(f"{tier_name}_storage_station_controller_top"),
                    "front": texture_id(f"{tier_name}_storage_station_controller_front"),
                    "back": texture_id(f"{tier_name}_storage_station_controller_back"),
                    "side": texture_id(f"{tier_name}_storage_station_controller_side"),
                },
            },
        )

    # Neutral compatibility textures remain useful to older resource packs and
    # make the generator cover every pre-existing storage-station PNG.
    save_texture(
        BLOCK_TEXTURES / "storage_station_controller_top.png",
        controller_texture("lv", "top"),
        "infrastructure",
    )
    save_texture(
        BLOCK_TEXTURES / "storage_station_controller_side.png",
        controller_texture("lv", "side"),
        "infrastructure",
    )
    for kind in ("casing", "frame"):
        save_texture(
            BLOCK_TEXTURES / f"storage_station_{kind}.png",
            station_structure_texture(kind),
            "infrastructure",
        )


def generate_terminals() -> None:
    for tier_name in TIERS:
        save_texture(
            BLOCK_TEXTURES / f"terminal_{tier_name}_plate.png",
            terminal_plate_texture(tier_name),
            "terminals-and-cables",
        )
        for material in ("body", "metal"):
            save_texture(
                BLOCK_TEXTURES / f"terminal_{tier_name}_{material}.png",
                terminal_surface_texture(tier_name, material),
                "terminals-and-cables",
            )
        save_texture(
            ITEM_TEXTURES / f"terminal_{tier_name}.png",
            terminal_item_texture(tier_name),
            "items",
        )

        for direction in ("north", "south", "east", "west", "up", "down"):
            model_path = BLOCK_MODELS / f"{tier_name}_terminal_mount_{direction}.json"
            payload = json.loads(model_path.read_text(encoding="utf-8"))
            payload["textures"]["particle"] = texture_id(f"terminal_{tier_name}_plate")
            payload["textures"]["plate"] = texture_id(f"terminal_{tier_name}_plate")
            write_json(model_path, payload)

    for kind in ("plate", "seat", "stud"):
        save_texture(
            BLOCK_TEXTURES / f"terminal_{kind}.png",
            generic_terminal_texture(kind),
            "terminals-and-cables",
        )


def generate_cables() -> None:
    for kind in ("bare", "class_1", "class_2", "acsr"):
        save_texture(
            BLOCK_TEXTURES / f"cable_{kind}.png",
            cable_texture(kind),
            "terminals-and-cables",
        )

    combinations: list[tuple[int, str, bool]] = []
    for ampacity in COPPER_AMPACITIES:
        for insulation in ("bare", "class_1", "class_2"):
            combinations.append((ampacity, insulation, False))
    for ampacity in ACSR_AMPACITIES:
        combinations.append((ampacity, "bare", True))

    for ampacity, insulation, acsr in combinations:
        name = f"cable_{ampacity}a_{insulation}"
        save_texture(
            ITEM_TEXTURES / f"{name}.png",
            cable_item_texture(ampacity, insulation, acsr),
            "items",
        )
        write_json(
            ITEM_MODELS / f"{name}.json",
            {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"stardustindustry:item/{name}"},
            },
        )

    # Legacy four-way icons remain deterministic for downstream packs even though
    # this project now gives every ampacity its own silhouette.
    for kind, sample in (
        ("bare", (500, "bare", False)),
        ("class_1", (500, "class_1", False)),
        ("class_2", (500, "class_2", False)),
        ("acsr", (800, "bare", True)),
    ):
        save_texture(
            ITEM_TEXTURES / f"cable_{kind}.png",
            cable_item_texture(*sample),
            "items",
        )


def assert_grayscale(path: Path) -> None:
    image = Image.open(path).convert("RGBA")
    for red, green, blue, alpha in image.getdata():
        if not (red == green == blue and alpha == 255):
            raise AssertionError(f"{path.name}: cable textures must be opaque grayscale")


def color_jump(first: tuple[int, ...], second: tuple[int, ...]) -> int:
    return max(abs(first[index] - second[index]) for index in range(3))


def assert_soft_wrap(path: Path) -> None:
    image = Image.open(path).convert("RGBA")
    width, height = image.size
    pixels = image.load()
    horizontal_internal = max(
        color_jump(pixels[x, y], pixels[x + 1, y])
        for y in range(height)
        for x in range(width - 1)
    )
    vertical_internal = max(
        color_jump(pixels[x, y], pixels[x, y + 1])
        for x in range(width)
        for y in range(height - 1)
    )
    horizontal_seam = max(color_jump(pixels[width - 1, y], pixels[0, y]) for y in range(height))
    vertical_seam = max(color_jump(pixels[x, height - 1], pixels[x, 0]) for x in range(width))
    if horizontal_seam > horizontal_internal or vertical_seam > vertical_internal:
        raise AssertionError(
            f"{path.name}: wrap seam ({horizontal_seam}, {vertical_seam}) exceeds "
            f"internal transitions ({horizontal_internal}, {vertical_internal})"
        )


def validate_textures() -> None:
    for path in GENERATED_TEXTURES:
        with Image.open(path) as image:
            if image.mode != "RGBA":
                raise AssertionError(f"{path}: expected RGBA, got {image.mode}")
            expected = (32, 128) if path.parent == BLOCK_TEXTURES and path.stem.startswith("cable_") else (16, 16)
            if image.size != expected:
                raise AssertionError(f"{path}: expected {expected}, got {image.size}")

    for kind in ("bare", "class_1", "class_2", "acsr"):
        path = BLOCK_TEXTURES / f"cable_{kind}.png"
        assert_grayscale(path)
        assert_soft_wrap(path)

    def item_bytes(name: str) -> bytes:
        path = ITEM_TEXTURES / f"{name}.png"
        with Image.open(path) as image:
            rgba = image.convert("RGBA")
            alpha_values = set(rgba.getchannel("A").getdata())
            if not alpha_values.issubset({0, 255}):
                raise AssertionError(f"{path.name}: item edges must use binary alpha")
            if any((red or green or blue) for red, green, blue, alpha in rgba.getdata() if alpha == 0):
                raise AssertionError(f"{path.name}: transparent pixels must have zero RGB")
            left, top, right, bottom = rgba.getchannel("A").getbbox() or (0, 0, 0, 0)
            if left < 1 or top < 1 or right > 15 or bottom > 15:
                raise AssertionError(f"{path.name}: item art must keep a one-pixel transparent border")
            return rgba.tobytes()

    def item_area(name: str) -> int:
        path = ITEM_TEXTURES / f"{name}.png"
        with Image.open(path) as image:
            return sum(alpha == 255 for alpha in image.convert("RGBA").getchannel("A").getdata())

    # Each copper rating must remain visibly distinct within every insulation
    # family. This specifically guards the former 50 A / 150 A duplicate icons.
    for insulation in ("bare", "class_1", "class_2"):
        variants = [
            item_bytes(f"cable_{ampacity}a_{insulation}")
            for ampacity in COPPER_AMPACITIES
        ]
        if len(set(variants)) != len(variants):
            raise AssertionError(f"duplicate copper item icon in {insulation}")
        areas = [
            item_area(f"cable_{ampacity}a_{insulation}")
            for ampacity in COPPER_AMPACITIES
        ]
        if any(first >= second for first, second in zip(areas, areas[1:])):
            raise AssertionError(f"copper item silhouettes must grow with ampacity in {insulation}")

    # Insulation markings are a second visual axis and must not collapse into
    # one another at any copper rating.
    for ampacity in COPPER_AMPACITIES:
        variants = [
            item_bytes(f"cable_{ampacity}a_{insulation}")
            for insulation in ("bare", "class_1", "class_2")
        ]
        if len(set(variants)) != len(variants):
            raise AssertionError(f"duplicate insulation item icon at {ampacity} A")

    acsr_variants = [item_bytes(f"cable_{ampacity}a_bare") for ampacity in ACSR_AMPACITIES]
    if len(set(acsr_variants)) != len(acsr_variants):
        raise AssertionError("800 A and 1600 A ACSR item icons must differ")


def make_contact_sheet(name: str, paths: Iterable[Path], columns: int = 5) -> None:
    paths = list(dict.fromkeys(paths))
    if not paths:
        return
    font = ImageFont.load_default()
    cell_width, cell_height = 128, 132
    rows = math.ceil(len(paths) / columns)
    sheet = Image.new("RGB", (columns * cell_width, rows * cell_height), "#202428")
    draw = ImageDraw.Draw(sheet)
    for index, path in enumerate(paths):
        column = index % columns
        row = index // columns
        left = column * cell_width
        top = row * cell_height
        with Image.open(path) as source:
            preview = source.convert("RGBA")
            if preview.width <= 96 and preview.height <= 96:
                scale = max(1, min(96 // preview.width, 96 // preview.height))
                preview = preview.resize(
                    (preview.width * scale, preview.height * scale),
                    Image.Resampling.NEAREST,
                )
            else:
                preview.thumbnail((96, 96), Image.Resampling.NEAREST)
            px = left + (cell_width - preview.width) // 2
            py = top + 4 + (96 - preview.height) // 2
            backdrop = Image.new("RGBA", preview.size, "#30363B")
            backdrop.alpha_composite(preview)
            sheet.paste(backdrop.convert("RGB"), (px, py))
        lines = textwrap.wrap(path.stem, width=19)[:2]
        for line_index, line in enumerate(lines):
            box = draw.textbbox((0, 0), line, font=font)
            text_width = box[2] - box[0]
            draw.text(
                (left + (cell_width - text_width) // 2, top + 103 + line_index * 11),
                line,
                fill="#E7EAE8",
                font=font,
            )
    REPORTS.mkdir(parents=True, exist_ok=True)
    sheet.save(REPORTS / f"{name}.png", format="PNG", compress_level=9)


def write_manifest() -> None:
    REPORTS.mkdir(parents=True, exist_ok=True)
    lines = []
    for path in sorted(dict.fromkeys(GENERATED_TEXTURES)):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        lines.append(f"{digest}  {path.relative_to(ROOT)}")
    (REPORTS / "manifest.sha256").write_text("\n".join(lines) + "\n", encoding="ascii")


def main() -> None:
    generate_device_resources()
    generate_ports_and_storage()
    generate_terminals()
    generate_cables()
    validate_textures()
    for group, paths in PREVIEW_GROUPS.items():
        make_contact_sheet(group, paths)
    write_manifest()
    print(
        f"Generated {len(GENERATED_TEXTURES)} textures, {len(DEVICES) * 2} device models, "
        f"and previews in {REPORTS.relative_to(ROOT)}"
    )


if __name__ == "__main__":
    main()
