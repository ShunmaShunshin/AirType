#!/usr/bin/env python
"""Generate AirType logo assets (1:1, transparent) matching the Terminal theme."""
import math
import os
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "docs")
os.makedirs(OUT_DIR, exist_ok=True)

SIZE = 1024          # final edge
SS = 4               # supersample for anti-aliasing
C = SIZE * SS        # working canvas

# Theme palette
DARK = (0x0B, 0x0E, 0x14, 255)
SIGNAL = (0x5E, 0xE0, 0x7A, 255)
DEEP = (0x2F, 0xA8, 0x5C, 255)
TEXT = (0xD8, 0xEA, 0xE0, 255)

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\consolab.ttf",   # Consolas Bold
    r"C:\Windows\Fonts\arialbd.ttf",
    r"C:\Windows\Fonts\seguisb.ttf",
]

def load_font(size_px):
    for p in FONT_CANDIDATES:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size_px)
            except Exception:
                continue
    return ImageFont.load_default()

def px(d):  # a 0..1 units -> canvas px
    return int(d * C)

def rgba(c, alpha=255):
    return (c[0], c[1], c[2], alpha)

def draw_square(canvas, draw):
    # 手机端：圆角实心方块（暗底）+ 大号 AT（几乎占满，无表盘装饰）
    inset = px(0.02)
    radius = px(0.22)
    draw.rounded_rectangle(
        [inset, inset, C - inset, C - inset], radius=radius, fill=rgba(DARK))
    f = load_font(int(C * 0.68))
    draw.text((C / 2, C / 2), "AT", font=f, fill=rgba(SIGNAL), anchor="mm")

def draw_pc(canvas, draw):
    # PC 端：透明背景 + 圆形 + 中间 AT
    cx = cy = C / 2
    R = px(0.45)
    ring_w = px(0.03)
    ring = [cx - R, cy - R, cx + R, cy + R]
    draw.arc(ring, start=0, end=360, fill=rgba(SIGNAL), width=ring_w)
    # 自适应缩放：让 AT 尽量大，但不越出圆环内侧（留 4% 安全边距）
    limit = R - ring_w / 2 - px(0.04)
    f = load_font(int(C * 0.70))
    while True:
        bb = draw.textbbox((cx, cy), "AT", font=f, anchor="mm")
        hw = max(abs(bb[0] - cx), abs(bb[2] - cx))
        hh = max(abs(bb[1] - cy), abs(bb[3] - cy))
        if max(hw, hh) <= limit:
            break
        f = load_font(int(f.size * 0.94))
    draw.text((cx, cy), "AT", font=f, fill=rgba(SIGNAL), anchor="mm")

def finalize(canvas, path):
    out = canvas.resize((SIZE, SIZE), Image.LANCZOS)
    out.save(path, "PNG")
    print("wrote", path)

for name, fn in [("logo-mobile", draw_square), ("logo-pc", draw_pc)]:
    canvas = Image.new("RGBA", (C, C), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    fn(canvas, draw)
    finalize(canvas, os.path.join(OUT_DIR, name + ".png"))
