#!/usr/bin/env python3
"""Render the Cyberspace app icon, splash icon and README icon.

Writes Android resources into android/app/src/main/res and media/icon.png.
Requires Pillow. Run from the repository root:  python3 tools/render_icons.py
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android/app/src/main/res"

CREAM = (239, 230, 204)
BG_EDGE = (10, 10, 9)
BG_CENTER = (30, 30, 26)
SS = 4  # supersampling factor

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def globe_mask(size, diameter, stroke):
    """Antialiased line-art globe (circle, lens-shaped meridian, equator) as an L mask.

    `diameter` and `stroke` are fractions of `size` and of the diameter respectively.
    """
    s = size * SS
    m = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(m)
    r = s * diameter / 2
    w = max(1, round(2 * r * stroke))
    x = y = s / 2

    # Meridian: two arcs meeting at the poles, clipped to the globe.
    a = r * 0.42
    big = (r * r + a * a) / (2 * a)
    for side in (-1, 1):
        cx = x - side * (big - a)
        d.ellipse((cx - big, y - big, cx + big, y + big), outline=255, width=w)
    inside = Image.new("L", (s, s), 0)
    ImageDraw.Draw(inside).ellipse((x - r + 1, y - r + 1, x + r - 1, y + r - 1), fill=255)
    m = ImageChops.multiply(m, inside)

    ring = Image.new("L", (s, s), 0)
    ImageDraw.Draw(ring).ellipse((x - r, y - r, x + r, y + r), outline=255, width=w)
    m = ImageChops.lighter(m, ring)
    ImageDraw.Draw(m).rectangle((x - r + w / 2, y - w / 2, x + r - w / 2, y + w / 2), fill=255)
    return m.resize((size, size), Image.LANCZOS)


def glowing_globe(size, diameter, stroke=0.08, glow=0.6):
    """Cream globe with a soft glow on a transparent background."""
    lines = globe_mask(size, diameter, stroke)
    halo = lines.filter(ImageFilter.GaussianBlur(size * diameter * 0.07)).point(lambda v: int(v * glow))
    img = Image.new("RGBA", (size, size), CREAM + (0,))
    img.putalpha(ImageChops.lighter(lines, halo))
    return img


def background(size):
    vignette = Image.radial_gradient("L").resize((size, size), Image.LANCZOS)  # 0 centre -> 255 edge
    return Image.composite(Image.new("RGB", (size, size), BG_EDGE), Image.new("RGB", (size, size), BG_CENTER), vignette)


def full_icon(size):
    img = background(size).convert("RGBA")
    img.alpha_composite(glowing_globe(size, 0.50))
    return img


def rounded(img, radius_frac):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius=int(img.width * radius_frac), fill=255)
    out = img.copy()
    out.putalpha(ImageChops.multiply(img.getchannel("A"), mask))
    return out


def circle(img):
    mask = Image.new("L", (img.width * SS, img.height * SS), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, mask.width - 1, mask.height - 1), fill=255)
    mask = mask.resize(img.size, Image.LANCZOS)
    out = img.copy()
    out.putalpha(ImageChops.multiply(img.getchannel("A"), mask))
    return out


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)


def main():
    for name, scale in DENSITIES.items():
        layer = round(108 * scale)   # adaptive icon layers are 108dp
        legacy = round(48 * scale)   # pre-Android 8 launcher icons are 48dp
        splash = round(288 * scale)  # Android 12 splash icon canvas (no icon background)

        mipmap = RES / f"mipmap-{name}"
        save(background(layer), mipmap / "ic_launcher_background.png")
        save(glowing_globe(layer, 0.46), mipmap / "ic_launcher_foreground.png")
        mono = Image.new("RGBA", (layer, layer), (255, 255, 255, 0))
        mono.putalpha(globe_mask(layer, 0.46, 0.08))
        save(mono, mipmap / "ic_launcher_monochrome.png")

        # Legacy icons: the full artwork, inset slightly like other launcher icons.
        art = full_icon(legacy * 4).resize((legacy, legacy), Image.LANCZOS)
        inset = round(legacy * 0.04)
        sq = Image.new("RGBA", (legacy, legacy), (0, 0, 0, 0))
        sq.alpha_composite(rounded(art.resize((legacy - 2 * inset,) * 2, Image.LANCZOS), 0.18), (inset, inset))
        save(sq, mipmap / "ic_launcher.png")
        save(circle(art), mipmap / "ic_launcher_round.png")

        # The splash shows the icon inside a 192dp circle of a 288dp canvas.
        save(glowing_globe(splash, 0.36), RES / f"drawable-{name}" / "splash_icon.png")

    save(full_icon(512).convert("RGB"), ROOT / "media/icon.png")
    print("icons written")


if __name__ == "__main__":
    main()
