#!/usr/bin/env python
"""Round the corners of the user's logo and emit icon assets for PC + Android."""
import os
from PIL import Image, ImageDraw

Image.MAX_IMAGE_PIXELS = None

ROOT = os.path.join(os.path.dirname(__file__), "..")
SRC = os.path.join(ROOT, "airtype-logo.png")
RES = os.path.join(ROOT, "mobile", "android", "app", "src", "main", "res")
PC_ASSETS = os.path.join(ROOT, "pc", "AirTypePC", "Assets")
OUT_DOCS = os.path.join(ROOT, "docs")
os.makedirs(PC_ASSETS, exist_ok=True)
os.makedirs(OUT_DOCS, exist_ok=True)

BASE = 1024
RADIUS = int(BASE * 0.20)   # rounded corner radius

def load_base():
    im = Image.open(SRC).convert("RGBA")
    return im.resize((BASE, BASE), Image.LANCZOS)

def rounded(base):
    mask = Image.new("L", (BASE, BASE), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, BASE - 1, BASE - 1], radius=RADIUS, fill=255)
    out = Image.new("RGBA", (BASE, BASE), (0, 0, 0, 0))
    out.paste(base, (0, 0), mask)
    return out

def green_foreground(base):
    # key out near-black to transparent (keep green content), then fit to adaptive safe zone
    px = base.load()
    fg = Image.new("RGBA", (BASE, BASE), (0, 0, 0, 0))
    fp = fg.load()
    for y in range(BASE):
        for x in range(BASE):
            r, g, b, a = px[x, y]
            if max(r, g, b) <= 40:
                fp[x, y] = (r, g, b, 0)
            else:
                fp[x, y] = (r, g, b, a)
    # content bbox
    bbox = fg.getbbox()
    if bbox is None:
        bbox = (0, 0, BASE, BASE)
    crop = fg.crop(bbox)
    cw, ch = crop.size
    # scale so the larger side fits within the adaptive safe zone (~58% of 432)
    target = int(432 * 0.58)
    scale = target / max(cw, ch)
    crop = crop.resize((int(cw * scale), int(ch * scale)), Image.LANCZOS)
    canvas = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    canvas.paste(crop, ((432 - crop.size[0]) // 2, (432 - crop.size[1]) // 2), crop)
    return canvas

def key_transparent(base):
    """green logo on transparent (black bg removed), full size."""
    px = base.load()
    fg = Image.new("RGBA", (BASE, BASE), (0, 0, 0, 0))
    fp = fg.load()
    for y in range(BASE):
        for x in range(BASE):
            r, g, b, a = px[x, y]
            fp[x, y] = (r, g, b, 0) if max(r, g, b) <= 40 else (r, g, b, a)
    return fg

base = load_base()
rnd = rounded(base)
rnd.save(os.path.join(OUT_DOCS, "airtype-rounded.png"), "PNG")
print("wrote docs/airtype-rounded.png")

# Android legacy mipmaps (rounded icon)
for dens, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    d = os.path.join(RES, "mipmap-" + dens)
    os.makedirs(d, exist_ok=True)
    rnd.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"), "PNG")
    print("wrote mipmap-%s/ic_launcher.png" % dens)

# Android adaptive foreground (green only, transparent)
fg = green_foreground(base)
os.makedirs(os.path.join(RES, "drawable"), exist_ok=True)
fg.save(os.path.join(RES, "drawable", "ic_launcher_foreground.png"), "PNG")
print("wrote drawable/ic_launcher_foreground.png")

# PC asset: green logo on transparent background (no black bg)
pc_clear = key_transparent(base)
pc = os.path.join(PC_ASSETS, "logo-pc.png")
pc_clear.save(pc, "PNG")
print("wrote pc Assets/logo-pc.png (transparent)")

ico = os.path.join(PC_ASSETS, "airtype.ico")
pc_clear.save(ico, format="ICO", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print("wrote pc Assets/airtype.ico (transparent)")
