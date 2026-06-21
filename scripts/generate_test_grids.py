#!/usr/bin/env python3
"""Generate synthetic JPEG grid images for RouteSnap instrumented tests.

Each image is a numbered color grid with GPS EXIF tags embedded, so tests can
verify Ken Burns pan/zoom direction, portrait crop framing, and location
extraction without relying on real photos.

Usage:
    python3 scripts/generate_test_grids.py

Requires:
    pip install Pillow piexif
"""

import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
import piexif

OUTPUT_DIR = Path(__file__).parent.parent / "app/src/androidTest/assets/grids"
JPEG_QUALITY = 95

IMAGES = [
    {
        "filename": "landscape_4080x1792.jpg",
        "width": 4080,
        "height": 1792,
        "cols": 8,
        "rows": 4,
        "gps_lat": 35.6264,   # Tokyo — Odaiba
        "gps_lon": 139.7752,
        "location": "Tokyo, Odaiba",
    },
    {
        "filename": "portrait_1792x4080.jpg",
        "width": 1792,
        "height": 4080,
        "cols": 4,
        "rows": 8,
        "gps_lat": 34.9671,   # Kyoto — Fushimi Inari
        "gps_lon": 135.7727,
        "location": "Kyoto, Fushimi Inari",
    },
    {
        "filename": "square_2048x2048.jpg",
        "width": 2048,
        "height": 2048,
        "cols": 4,
        "rows": 4,
        "gps_lat": 34.9671,   # Kyoto — Fushimi Inari
        "gps_lon": 135.7727,
        "location": "Kyoto, Fushimi Inari",
    },
]

COLUMN_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"


def hsv_to_rgb(h, s, v):
    h6 = h * 6
    i = int(h6)
    f = h6 - i
    p = v * (1 - s)
    q = v * (1 - f * s)
    t = v * (1 - (1 - f) * s)
    r, g, b = [(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)][i % 6]
    return (int(r * 255), int(g * 255), int(b * 255))


def contrasting_color(bg):
    r, g, b = bg
    return (0, 0, 0) if (0.299 * r + 0.587 * g + 0.114 * b) > 128 else (255, 255, 255)


def decimal_to_dms_rational(value):
    degrees = int(value)
    minutes_f = (value - degrees) * 60
    minutes = int(minutes_f)
    seconds = (minutes_f - minutes) * 60
    return [(degrees, 1), (minutes, 1), (int(seconds * 1000), 1000)]


def make_exif(lat, lon):
    gps_ifd = {
        piexif.GPSIFD.GPSLatitudeRef: b"N" if lat >= 0 else b"S",
        piexif.GPSIFD.GPSLatitude: decimal_to_dms_rational(abs(lat)),
        piexif.GPSIFD.GPSLongitudeRef: b"E" if lon >= 0 else b"W",
        piexif.GPSIFD.GPSLongitude: decimal_to_dms_rational(abs(lon)),
    }
    return piexif.dump({"GPS": gps_ifd})


def load_font(size):
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
        "/Library/Fonts/Arial Bold.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def generate_image(spec):
    w, h = spec["width"], spec["height"]
    cols, rows = spec["cols"], spec["rows"]
    cell_w = w // cols
    cell_h = h // rows
    total_cells = cols * rows

    big_size = min(cell_w, cell_h) // 3
    small_size = max(16, min(cell_w, cell_h) // 9)
    big_font = load_font(big_size)
    small_font = load_font(small_size)

    img = Image.new("RGB", (w, h), (20, 20, 20))
    draw = ImageDraw.Draw(img)

    for row in range(rows):
        for col in range(cols):
            cell_index = row * cols + col
            bg = hsv_to_rgb(cell_index / total_cells, 0.72, 0.88)
            fg = contrasting_color(bg)

            x0, y0 = col * cell_w, row * cell_h
            x1, y1 = x0 + cell_w, y0 + cell_h

            # Fill cell
            draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=bg)

            # Large cell number, centered
            number = str(cell_index + 1)
            bbox = draw.textbbox((0, 0), number, font=big_font)
            tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
            draw.text(
                (x0 + (cell_w - tw) // 2, y0 + (cell_h - th) // 2 - bbox[1]),
                number,
                fill=fg,
                font=big_font,
            )

            # Coordinate label bottom-left corner
            label = f"{COLUMN_LETTERS[col]}{row + 1}"
            lbbox = draw.textbbox((0, 0), label, font=small_font)
            lh = lbbox[3] - lbbox[1]
            draw.text(
                (x0 + small_size // 2, y1 - lh - small_size // 2 - lbbox[1]),
                label,
                fill=fg,
                font=small_font,
            )

            # Cell border
            draw.rectangle([x0, y0, x1 - 1, y1 - 1], outline=(0, 0, 0), width=3)

    # Full-image crosshair at center
    cx, cy = w // 2, h // 2
    arm = min(w, h) // 16
    thick = max(6, min(w, h) // 200)
    draw.line([(cx - arm, cy), (cx + arm, cy)], fill=(255, 255, 255), width=thick)
    draw.line([(cx, cy - arm), (cx, cy + arm)], fill=(255, 255, 255), width=thick)
    half = max(2, thick // 3)
    draw.line([(cx - arm, cy), (cx + arm, cy)], fill=(0, 0, 0), width=half)
    draw.line([(cx, cy - arm), (cx, cy + arm)], fill=(0, 0, 0), width=half)

    out_path = OUTPUT_DIR / spec["filename"]
    exif_bytes = make_exif(spec["gps_lat"], spec["gps_lon"])
    img.save(out_path, "JPEG", quality=JPEG_QUALITY, exif=exif_bytes)

    size_kb = out_path.stat().st_size // 1024
    print(f"  {spec['filename']:35s}  {w}×{h}  {cols}×{rows} grid  {size_kb:4d} KB  ({spec['location']})")


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Output → {OUTPUT_DIR}\n")
    for spec in IMAGES:
        generate_image(spec)
    print("\nDone.")


if __name__ == "__main__":
    main()
