#!/usr/bin/env python
"""Generate Android launcher icon set + adaptive foreground from the approved logos."""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..")
RES = os.path.join(ROOT, "mobile", "android", "app", "src", "main", "res")
MOBILE = os.path.join(ROOT, "docs", "logo-mobile.png")

SIGNAL = (0x5E, 0xE0, 0x7A, 255)
FONT_CANDIDATES = [
    r"C:\Windows\Fonts\consolab.ttf",
    r"C:\Windows\Fonts\arialbd.ttf",
    r"C:\Windows\Fonts\seguisb.ttf",
]

def load_font(size):
    for p in FONT_CANDIDATES:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()

# 1) Legacy launcher icons (rounded-square logo) at standard densities
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
base = Image.open(MOBILE).convert("RGBA")
for dens, px in sizes.items():
    d = os.path.join(RES, "mipmap-" + dens)
    os.makedirs(d, exist_ok=True)
    base.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"), "PNG")
    print("wrote mipmap-%s/ic_launcher.png" % dens)

# 2) Adaptive foreground: green "AT" on transparent, fitted to safe zone
fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
d = ImageDraw.Draw(fg)
cx = cy = 216
limit = int(432 * 0.30)
f = load_font(320)
while True:
    bb = d.textbbox((cx, cy), "AT", font=f, anchor="mm")
    hw = max(abs(bb[0] - cx), abs(bb[2] - cx))
    hh = max(abs(bb[1] - cy), abs(bb[3] - cy))
    if max(hw, hh) <= limit:
        break
    f = load_font(int(f.size * 0.94))
d.text((cx, cy), "AT", font=f, fill=SIGNAL, anchor="mm")
os.makedirs(os.path.join(RES, "drawable"), exist_ok=True)
fg.save(os.path.join(RES, "drawable", "ic_launcher_foreground.png"), "PNG")
print("wrote drawable/ic_launcher_foreground.png")
