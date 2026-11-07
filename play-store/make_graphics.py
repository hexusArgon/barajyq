"""Regenerates Play Store graphics from branding/ and graphics/phone/*.png (raw adb screenshots)."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import glob, os
ROOT = os.path.dirname(os.path.abspath(__file__))
G = os.path.join(ROOT, "graphics")
ICON = os.path.join(ROOT, "..", "branding", "qaida-bary-icon-1024.png")
FONT = "/usr/share/fonts/truetype/sand-box/google/Inter/Inter-VariableFont_opsz,wght.ttf"
TL, BR = (6, 95, 93), (46, 42, 128)

def font(size, weight="Bold"):
    f = ImageFont.truetype(FONT, size)
    try: f.set_variation_by_name(weight)
    except Exception: pass
    return f

def gradient(w, h):
    im = Image.new("RGB", (w, h))
    px = im.load()
    for y in range(h):
        for x in range(w):
            t = (x / w + y / h) / 2
            px[x, y] = tuple(int(TL[i] + (BR[i] - TL[i]) * t) for i in range(3))
    return im

src = Image.open(ICON).convert("RGB")
# 1) Play icon 512x512: full-bleed square (Play applies its own mask), corners filled with gradient.
bg = gradient(1024, 1024)
mask = Image.new("L", (1024, 1024), 0)
ImageDraw.Draw(mask).rounded_rectangle([6, 6, 1017, 1017], radius=180, fill=255)
mask = mask.filter(ImageFilter.GaussianBlur(2))
full = Image.composite(src, bg, mask)
full.resize((512, 512), Image.LANCZOS).save(os.path.join(G, "icon-512.png"), optimize=True)

# 2) Feature graphic 1024x500 per language.
TAG = {
    "ru": "Лучшие события и места вашего города",
    "kk": "Қалаңыздағы ең қызықты іс-шаралар мен орындар",
    "en": "The best events and places in your city",
}
SUB = {"ru": "Казахстан · kk / ru / en", "kk": "Қазақстан · kk / ru / en", "en": "Kazakhstan · kk / ru / en"}
icon_r = Image.open(ICON).convert("RGBA").resize((300, 300), Image.LANCZOS)
imask = Image.new("L", (300, 300), 0)
ImageDraw.Draw(imask).rounded_rectangle([0, 0, 299, 299], radius=56, fill=255)
for lang, tag in TAG.items():
    fg = gradient(1024, 500)
    d = ImageDraw.Draw(fg)
    # soft shadow + icon
    sh = Image.new("L", (1024, 500), 0)
    ImageDraw.Draw(sh).rounded_rectangle([72, 112, 372, 412], radius=56, fill=110)
    sh = sh.filter(ImageFilter.GaussianBlur(14))
    fg.paste((10, 20, 40), (0, 0), sh)
    fg.paste(icon_r.convert("RGB"), (64, 100), imask)
    x = 420
    d.text((x, 120), "Barajyq", font=font(112), fill="white")
    # wrap tagline
    f = font(36, "SemiBold"); words = tag.split(); lines = []; cur = ""
    for w_ in words:
        t = (cur + " " + w_).strip()
        if d.textlength(t, font=f) > 560 and cur: lines.append(cur); cur = w_
        else: cur = t
    lines.append(cur)
    y = 270
    for ln in lines:
        d.text((x, y), ln, font=f, fill=(255, 225, 150)); y += 48
    d.text((x, y + 14), SUB[lang], font=font(24, "Medium"), fill=(220, 230, 240))
    fg.save(os.path.join(G, f"feature-graphic-{lang}.png"), optimize=True)

# 3) Phone screenshots: Play requires long side <= 2x short side -> 1080x2160 (exactly 2:1), letterboxed.
os.makedirs(os.path.join(G, "phone-play"), exist_ok=True)
for p in sorted(glob.glob(os.path.join(G, "phone", "*.png"))):
    im = Image.open(p).convert("RGB")
    h = 2160; w = round(im.width * h / im.height)
    canvas = Image.new("RGB", (1080, 2160), im.getpixel((5, 5)))
    canvas.paste(im.resize((w, h), Image.LANCZOS), ((1080 - w) // 2, 0))
    canvas.save(os.path.join(G, "phone-play", os.path.basename(p)), optimize=True)
print("ok")
