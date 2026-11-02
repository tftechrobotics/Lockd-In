"""Lockd In background scenes (C2), rendered procedurally: no photos, nothing to license.

    python3 design/backgrounds/gen.py OUT_DIR

Writes bg_library.webp and bg_mountains.webp (1080x2400, dark, low-key so light text keeps
AA contrast; the app adds a scrim in the light theme). Deterministic (fixed seeds).
"""
import sys, math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1080, 2400
OUT = sys.argv[1] if len(sys.argv) > 1 else "."

def lum(a):
    c = a / 255.0
    c = np.where(c <= 0.03928, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    return 0.2126 * c[..., 0] + 0.7152 * c[..., 1] + 0.0722 * c[..., 2]

# Where the UI text sits (fractions of W, H). Light text (#F0E8DB, L≈0.81) needs the image under
# it at L ≤ 0.14 for 4.5:1; outside this band highlights (a lamp) may be brighter.
BAND = (0.10, 0.06, 0.90, 0.80)

def finish(img, cap, seed, outside_cap=0.32):
    """Vignette, grain, and luminance caps (inside / outside the text band)."""
    a = np.asarray(img).astype(np.float32)
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt(((xx - W / 2) / (W * 0.75)) ** 2 + ((yy - H * 0.45) / (H * 0.7)) ** 2)
    a *= np.clip(1.15 - 0.55 * d, 0.45, 1.0)[..., None]
    rng = np.random.default_rng(seed)
    a += rng.normal(0, 3.0, a.shape[:2])[..., None]
    a = np.clip(a, 0, 255)
    # Caps: scale the whole image so the band's brightest pixel is at most `cap`, then soft-clip
    # anything outside the band above `outside_cap`.
    L = lum(a)
    x0, y0, x1, y1 = int(BAND[0] * W), int(BAND[1] * H), int(BAND[2] * W), int(BAND[3] * H)
    band_max = np.percentile(L[y0:y1, x0:x1], 99.9)
    if band_max > cap:
        a *= (cap / band_max) ** (1 / 2.2)
    L = lum(a)
    over = L > outside_cap
    if over.any():
        a[over] *= ((outside_cap / L[over]) ** (1 / 2.2))[..., None]
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))

def band_max(img):
    a = np.asarray(img).astype(np.float32)
    x0, y0, x1, y1 = int(BAND[0] * W), int(BAND[1] * H), int(BAND[2] * W), int(BAND[3] * H)
    return float(np.percentile(lum(a)[y0:y1, x0:x1], 99.9))

def library():
    rng = np.random.default_rng(7)
    img = Image.new("RGB", (W, H), (22, 17, 13))
    d = ImageDraw.Draw(img)
    spines = [(70, 38, 30), (58, 44, 30), (84, 60, 38), (46, 40, 32), (96, 74, 48), (62, 30, 26), (52, 50, 36), (110, 86, 58)]
    shelf_h = 250
    top = 120
    # Back shelves fill the left two thirds and continue faintly to the right.
    for row in range(9):
        y0 = top + row * shelf_h
        x = -10
        right = W + 10
        while x < right:
            w = int(rng.integers(14, 42))
            h = int(shelf_h * rng.uniform(0.62, 0.92))
            col = spines[int(rng.integers(0, len(spines)))]
            k = rng.uniform(0.75, 1.15)
            col = tuple(int(c * k) for c in col)
            d.rectangle([x, y0 + shelf_h - 22 - h, x + w - 2, y0 + shelf_h - 22], fill=col)
            if rng.random() < 0.35:  # a band on the spine
                yb = y0 + shelf_h - 22 - int(h * rng.uniform(0.2, 0.8))
                d.rectangle([x + 2, yb, x + w - 4, yb + 6], fill=tuple(min(255, int(c * 1.5)) for c in col))
            x += w + (int(rng.integers(0, 3)))
        d.rectangle([-10, y0 + shelf_h - 22, right, y0 + shelf_h], fill=(44, 32, 22))
    # Depth of field: shelves far away are soft.
    img = img.filter(ImageFilter.GaussianBlur(9))
    a = np.asarray(img).astype(np.float32)
    # Warm lamp on the right, lighting the room with a soft falloff.
    lx, ly = W * 0.80, H * 0.45
    yy, xx = np.mgrid[0:H, 0:W]
    r = np.sqrt((xx - lx) ** 2 + ((yy - ly) * 0.9) ** 2)
    light = 0.55 + 1.9 * np.exp(-(r / 620.0) ** 2)
    a *= light[..., None]
    glow = np.exp(-(r / 230.0) ** 2)[..., None] * np.array([255, 170, 84]) * 0.95
    a += glow
    img = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    # Lamp silhouette: shade and stem.
    d.polygon([(lx - 95, ly - 10), (lx + 95, ly - 10), (lx + 58, ly - 130), (lx - 58, ly - 130)], fill=(196, 142, 78))
    d.rectangle([lx - 7, ly - 10, lx + 7, ly + 380], fill=(46, 33, 22))
    d.ellipse([lx - 75, ly + 368, lx + 75, ly + 400], fill=(40, 29, 20))
    # Bokeh: out-of-focus warm lights.
    bok = Image.new("RGB", (W, H), (0, 0, 0))
    bd = ImageDraw.Draw(bok)
    for _ in range(18):
        bx, by = rng.uniform(W * 0.55, W), rng.uniform(H * 0.25, H * 0.62)
        rr = rng.uniform(14, 34)
        v = rng.uniform(40, 90)
        bd.ellipse([bx - rr, by - rr, bx + rr, by + rr], fill=(int(v), int(v * 0.7), int(v * 0.4)))
    bok = bok.filter(ImageFilter.GaussianBlur(6))
    img = Image.fromarray(np.clip(np.asarray(img).astype(np.float32) + np.asarray(bok).astype(np.float32), 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    # Desk edge and an open book in the foreground (left), slightly soft.
    d.rectangle([0, H * 0.74, W, H], fill=(30, 21, 15))
    d.polygon([(-40, H * 0.74), (W, H * 0.735), (W, H * 0.745), (-40, H * 0.755)], fill=(70, 48, 30))
    bx, by = W * 0.06, H * 0.79
    d.polygon([(bx, by), (bx + 300, by - 30), (bx + 320, by + 70), (bx + 20, by + 110)], fill=(112, 92, 66))
    d.polygon([(bx + 300, by - 30), (bx + 580, by - 10), (bx + 590, by + 95), (bx + 320, by + 70)], fill=(128, 106, 76))
    for i in range(7):
        t = 0.15 + i * 0.1
        d.line([(bx + 30, by + 12 + i * 12), (bx + 290, by - 16 + i * 12)], fill=(92, 76, 56), width=2)
    img = img.filter(ImageFilter.GaussianBlur(3.0))
    return finish(img, cap=0.13, seed=11)

def ridge(rng, n, rough, base, amp):
    pts = np.zeros(n)
    step = n - 1
    scale = amp
    pts[0], pts[-1] = rng.uniform(-1, 1) * amp, rng.uniform(-1, 1) * amp
    while step > 1:
        half = step // 2
        for i in range(half, n - 1, step):
            pts[i] = (pts[i - half] + pts[i + half]) / 2 + rng.uniform(-1, 1) * scale
        scale *= rough
        step = half
    return base + pts

def mountains():
    rng = np.random.default_rng(23)
    yy = np.mgrid[0:H, 0:W][0].astype(np.float32)
    # Sky: dark warm grey, a little lighter toward the horizon.
    t = np.clip(yy / (H * 0.45), 0, 1)[..., None]
    a = (np.array([28, 24, 21]) * (1 - t) + np.array([58, 52, 46]) * t).astype(np.float32)
    a = np.broadcast_to(a, (H, W, 3)).copy()
    n = 1025
    xs = np.linspace(0, W, n)
    layers = [  # base height, amplitude, roughness, colour, haze below
        (H * 0.30, 260, 0.55, (70, 64, 57)),
        (H * 0.38, 220, 0.58, (54, 49, 44)),
        (H * 0.47, 170, 0.60, (40, 36, 32)),
        (H * 0.57, 120, 0.62, (28, 25, 22)),
    ]
    for i, (base, amp, rough, col) in enumerate(layers):
        r = ridge(rng, n, rough, base, amp)
        if i == 0:  # one tall peak, slightly right of centre
            r -= 380 * np.exp(-((xs - W * 0.62) / 170) ** 2)
        top = np.interp(np.arange(W), xs, r)
        mask = yy >= top[None, :]
        a[mask] = col
        # Mist rising from the valley below each ridge.
        smooth = np.convolve(np.pad(top, 60, mode="edge"), np.ones(121) / 121, mode="valid")
        mist = np.exp(-((yy - (smooth[None, :] + 150)) / 110) ** 2) * (0.5 - i * 0.09)
        a = a * (1 - mist[..., None]) + np.array([96, 90, 82]) * mist[..., None]
    img = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
    d = ImageDraw.Draw(img)
    # Pines in the foreground, both sides, darker toward the bottom.
    def pine(x, base_y, h, col):
        w = h * 0.32
        tiers = 7
        for k in range(tiers):
            ty = base_y - h + k * h / tiers * 0.95
            tw = w * (k + 1.5) / (tiers + 1)
            d.polygon([(x, ty), (x - tw, ty + h / tiers * 1.6), (x + tw, ty + h / tiers * 1.6)], fill=col)
        d.rectangle([x - 4, base_y - 10, x + 4, base_y + 30], fill=col)
    for _ in range(26):
        side = rng.random() < 0.5
        x = rng.uniform(-40, W * 0.33) if side else rng.uniform(W * 0.67, W + 40)
        h = rng.uniform(380, 720)
        base_y = rng.uniform(H * 0.80, H * 1.02)
        v = int(rng.uniform(12, 20))
        pine(x, base_y, h, (v, v - 2, v - 4))
    # Ground
    d.rectangle([0, H * 0.93, W, H], fill=(12, 11, 9))
    return finish(img, cap=0.13, seed=29)

def light_print(im):
    """Light-theme version: the same scene as a soft, faded print. Luminance is remapped from the
    dark range to a high-key cream range so dark text (#2B251F, L≈0.02) keeps ≥ 7:1 over it."""
    a = np.asarray(im).astype(np.float32) / 255.0
    L = lum(np.asarray(im).astype(np.float32))
    t = np.clip(L / 0.13, 0, 1) ** 0.8                     # 0 = darkest, 1 = brightest highlight
    target = 0.64 + 0.24 * t                                # relative luminance 0.64 … 0.88
    cream = np.array([243, 236, 225]) / 255.0               # hue of the light theme's ground
    base = cream * (target / 0.84)[..., None] ** (1 / 2.2)  # approx. gamma for the target luminance
    # Keep a hint of the original colour (warm lamp, book spines).
    chroma = a - a.mean(axis=2, keepdims=True)
    out = np.clip(base + chroma * 0.5, 0, 1)
    return Image.fromarray((out * 255).astype(np.uint8))

def band_min(img):
    a = np.asarray(img).astype(np.float32)
    x0, y0, x1, y1 = int(BAND[0] * W), int(BAND[1] * H), int(BAND[2] * W), int(BAND[3] * H)
    return float(np.percentile(lum(a)[y0:y1, x0:x1], 0.1))

for name, fn in (("library", library), ("mountains", mountains)):
    im = fn()
    im.save(f"{OUT}/bg_{name}.webp", "WEBP", quality=80, method=6)
    li = light_print(im)
    li.save(f"{OUT}/bg_{name}_light.webp", "WEBP", quality=80, method=6)
    print(name, "light: text-band min L", round(band_min(li), 3), "→ contrast with #2B251F", round((band_min(li) + 0.05) / (0.02 + 0.05), 2))
    print(name, "text-band L (99.9th pct)", round(band_max(im), 3), "→ contrast with #F0E8DB", round((0.81 + 0.05) / (band_max(im) + 0.05), 2))
