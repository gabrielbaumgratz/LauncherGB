"""Draws the app icon, the TV banner and the site's images (Pillow). Usage: python3 tools/make_art.py"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOGO_PATH = os.path.join(ROOT, "docs/assets/logo-white.png")
BG_COLOR = (20, 20, 22, 255)  # #141416: GB HomeTV dark theme background


def get_logo():
    logo = Image.open(LOGO_PATH).convert("RGBA")
    bbox = logo.getbbox()
    return logo.crop(bbox)


def make_icon(size):
    s = size * 4
    bg = Image.new("RGBA", (s, s), BG_COLOR)
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, s - 1, s - 1), radius=int(s * 0.22), fill=255)
    bg.putalpha(mask)

    crop = get_logo()
    tw = int(s * 0.68)
    th = int(tw * (crop.height / crop.width))
    scaled = crop.resize((tw, th), Image.LANCZOS)
    x = (s - tw) // 2
    y = (s - th) // 2
    bg.alpha_composite(scaled, (x, y))
    return bg.resize((size, size), Image.LANCZOS)


def make_banner(w=640, h=360):
    s = 4
    bg = Image.new("RGBA", (w * s, h * s), BG_COLOR)
    crop = get_logo()
    th = int(h * s * 0.62)
    tw = int(th * (crop.width / crop.height))
    scaled = crop.resize((tw, th), Image.LANCZOS)
    x = (w * s - tw) // 2
    y = (h * s - th) // 2
    bg.alpha_composite(scaled, (x, y))
    return bg.resize((w, h), Image.LANCZOS)


if __name__ == "__main__":
    res = os.path.join(ROOT, "app/res/drawable-nodpi")
    os.makedirs(res, exist_ok=True)
    make_icon(192).save(os.path.join(res, "icon.png"))
    make_banner(640, 360).save(os.path.join(res, "banner.png"))
    site = os.path.join(ROOT, "docs/assets")
    os.makedirs(site, exist_ok=True)
    make_icon(512).save(os.path.join(site, "icon-512.png"))
    make_icon(180).save(os.path.join(site, "apple-touch-icon.png"))
    make_icon(64).save(os.path.join(site, "favicon-64.png"))
    print("Art generated successfully.")
