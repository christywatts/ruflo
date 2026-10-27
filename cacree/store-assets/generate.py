#!/usr/bin/env python3
"""
CACREE App Store Asset Generator
Generates professional Google Play Store screenshots using Pillow + Inter fonts.

Usage:  python3 generate.py
Output: ./  (same directory, PNG files)
"""

from PIL import Image, ImageDraw, ImageFont
import numpy as np
import os, math

# ─── Paths ────────────────────────────────────────────────────────────────────
FONT_DIR = "/usr/share/fonts/opentype/inter"
OUT_DIR  = os.path.dirname(os.path.abspath(__file__))

# ─── Canvas: standard Android FHD portrait ───────────────────────────────────
W, H   = 1080, 1920
PAD    = 64     # side gutter

# ─── Palette ──────────────────────────────────────────────────────────────────
BG       = ( 4,  14,  28)
SURFACE  = (11,  25,  48)
SURFACE2 = ( 7,  18,  34)
BORDER   = (20,  44,  82)
FG       = (255, 255, 255)
FG2      = (123, 164, 203)
FG3      = ( 55,  85, 115)
ACCENT   = ( 59, 130, 246)
GREEN    = ( 16, 185, 129)
RED      = (248, 113, 113)
PURPLE   = (139,  92, 246)
YELLOW   = (251, 191,  36)

def blend(fg, bg=SURFACE, a=0.18):
    """Alpha-blend fg over bg without RGBA mode."""
    return tuple(int(bg[i] + (fg[i] - bg[i]) * a) for i in range(3))

# ─── Font helpers ─────────────────────────────────────────────────────────────
_font_cache = {}

def fnt(style, size):
    key = (style, size)
    if key not in _font_cache:
        files = {
            "black":     "InterDisplay-Black.otf",
            "extrabold": "InterDisplay-ExtraBold.otf",
            "bold":      "Inter-Bold.otf",
            "semibold":  "Inter-SemiBold.otf",
            "medium":    "Inter-Medium.otf",
            "regular":   "Inter-Regular.otf",
            "light":     "Inter-Light.otf",
        }
        _font_cache[key] = ImageFont.truetype(f"{FONT_DIR}/{files[style]}", size)
    return _font_cache[key]

# ─── Drawing helpers ──────────────────────────────────────────────────────────
def rr(draw, xy, r, fill=None, outline=None, w=1):
    draw.rounded_rectangle(xy, radius=r, fill=fill, outline=outline, width=w)

def txt(draw, x, y, text, style, size, color=FG, anchor="lt"):
    draw.text((x, y), text, font=fnt(style, size), fill=color, anchor=anchor)

# ─── Vertical gradient background ────────────────────────────────────────────
def make_bg():
    arr = np.zeros((H, W, 3), dtype=np.uint8)
    c1, c2 = np.array(BG, dtype=float), np.array([2, 8, 18], dtype=float)
    for y in range(H):
        t = y / H
        arr[y] = (c1 * (1-t) + c2 * t).astype(np.uint8)
    return Image.fromarray(arr)

# ─── Area chart with RGBA overlay fill ───────────────────────────────────────
def draw_chart(img, data, rect, color=GREEN, lw=4, fill_alpha=40):
    x0, y0, x1, y1 = rect
    cw, ch = x1-x0, y1-y0
    n = len(data)
    mn, mx = min(data), max(data)
    rng = mx - mn or 1

    pts = []
    for i, v in enumerate(data):
        px = x0 + int(i * cw / (n - 1))
        py = y1 - 8 - int((v - mn) / rng * (ch - 24))
        pts.append((px, py))

    # Fill polygon
    ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    poly = pts + [(x1, y1), (x0, y1)]
    od.polygon(poly, fill=(*color, fill_alpha))
    img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")

    d = ImageDraw.Draw(img)
    # Line
    for i in range(len(pts) - 1):
        d.line([pts[i], pts[i+1]], fill=color, width=lw)
    # Dots
    for pt in pts:
        d.ellipse([pt[0]-5, pt[1]-5, pt[0]+5, pt[1]+5], fill=color)
    # Last dot highlighted
    lx, ly_ = pts[-1]
    d.ellipse([lx-9, ly_-9, lx+9, ly_+9], fill=FG, outline=color, width=3)
    return img

# ─── Donut chart ──────────────────────────────────────────────────────────────
def draw_donut(img, cx, cy, outer_r, inner_r, slices, gap=0.025):
    """slices: list of (label, value, color)"""
    total = sum(v for _, v, _ in slices)
    angle = -math.pi / 2
    ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    for label, val, color in slices:
        sweep = 2 * math.pi * val / total - gap
        steps = max(40, int(sweep * 20))
        pts_out, pts_in = [], []
        for j in range(steps + 1):
            a = angle + sweep * j / steps
            pts_out.append((cx + outer_r * math.cos(a), cy + outer_r * math.sin(a)))
            pts_in.append( (cx + inner_r * math.cos(a), cy + inner_r * math.sin(a)))
        poly = pts_out + list(reversed(pts_in))
        od.polygon([(int(x), int(y)) for x, y in poly], fill=(*color, 220))
        angle += sweep + gap
    img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")
    return img

# ─── Progress bar ─────────────────────────────────────────────────────────────
def progress_bar(draw, x, y, w, h, pct, color, bg=SURFACE):
    rr(draw, [x, y, x+w, y+h], h//2, fill=SURFACE2)
    rr(draw, [x, y, x+max(h, int(w*pct)), y+h], h//2, fill=color)

# ─── Status bar ──────────────────────────────────────────────────────────────
def draw_status(draw, time="9:41 AM"):
    y = 58
    txt(draw, PAD, y, time, "semibold", 36, FG, anchor="lm")
    # Battery
    bx = W - PAD
    draw.rounded_rectangle([bx-76, y-14, bx-8, y+14], radius=4, outline=FG, width=2)
    draw.rounded_rectangle([bx-8,  y-7,  bx-2, y+7],  radius=2, fill=FG)
    draw.rounded_rectangle([bx-73, y-11, bx-26, y+11], radius=3, fill=FG)
    # Signal bars
    sx = bx - 100
    for i in range(3):
        bh = 8 + i * 6
        draw.rounded_rectangle([sx+i*14, y-bh//2, sx+i*14+8, y+bh//2], radius=2, fill=FG)

# ─── Bottom nav ───────────────────────────────────────────────────────────────
NAV_H  = 150
NAV_Y  = H - NAV_H
NAV_ITEMS = [("Home", "⊞"), ("History", "≡"), ("Stats", "◎"), ("Budget", "⊡")]

def draw_nav(draw, active=0):
    draw.rectangle([0, NAV_Y, W, H], fill=SURFACE2)
    draw.line([0, NAV_Y, W, NAV_Y], fill=BORDER, width=1)
    cx = W // len(NAV_ITEMS)
    for i, (label, icon) in enumerate(NAV_ITEMS):
        x = cx * i + cx // 2
        color = ACCENT if i == active else FG3
        draw.text((x, NAV_Y+34),  icon,  font=fnt("regular", 40), fill=color, anchor="mt")
        draw.text((x, NAV_Y+88), label, font=fnt("regular", 26), fill=color, anchor="mt")

# ─── Section header helper ────────────────────────────────────────────────────
def section_header(draw, y, left, right=None):
    txt(draw, PAD, y, left, "semibold", 38, FG)
    if right:
        txt(draw, W-PAD, y+2, right, "regular", 30, ACCENT, anchor="rt")
    return y + 54

# ═════════════════════════════════════════════════════════════════════════════
#  SCREEN 1 — DASHBOARD
# ═════════════════════════════════════════════════════════════════════════════
def screen_dashboard():
    img = make_bg()
    draw = ImageDraw.Draw(img)
    draw_status(draw)

    # Greeting
    txt(draw, PAD, 122, "Good morning,",  "regular",  40, FG2)
    txt(draw, PAD, 174, "Christy",         "extrabold", 62, FG)

    # ── Balance card ──────────────────────────────────────────────────────────
    cy = 290
    rr(draw, [PAD, cy, W-PAD, cy+440], 28, fill=SURFACE, outline=BORDER, w=1)

    txt(draw, PAD+40, cy+40, "MONTHLY CASH FLOW", "semibold", 26, FG3)
    txt(draw, PAD+40, cy+100, "+₦ 247,800", "extrabold", 78, FG)

    # Green percentage pill
    rr(draw, [PAD+40, cy+188, PAD+192, cy+234], 24, fill=blend(GREEN, SURFACE, 0.18))
    txt(draw, PAD+116, cy+211, "▲ 12.4%", "semibold", 32, GREEN, anchor="mm")

    # Mini chart
    chart_data = [52000, 68000, 61000, 75000, 71000, 82000, 91000, 82400]
    img = draw_chart(img, chart_data, [PAD+40, cy+248, W-PAD-40, cy+420])
    draw = ImageDraw.Draw(img)

    # ── Income / Expenses tiles ───────────────────────────────────────────────
    tw = (W - PAD*2 - 24) // 2
    ty = 770
    for i, (label, amount, color, arrow, pct) in enumerate([
        ("INCOME",   "₦412,000", GREEN, "▲", "+8.2%"),
        ("EXPENSES", "₦164,200", RED,   "▼", "-3.1%"),
    ]):
        tx = PAD + i * (tw + 24)
        rr(draw, [tx, ty, tx+tw, ty+220], 22, fill=SURFACE, outline=BORDER, w=1)
        txt(draw, tx+32, ty+36, label,  "semibold", 24, FG3)
        txt(draw, tx+32, ty+114, amount, "bold",     48, FG,    anchor="lm")
        txt(draw, tx+32, ty+164, f"{arrow} {pct}", "regular", 28, color)

    # ── Recent transactions ───────────────────────────────────────────────────
    ry = section_header(draw, 1038, "Recent", "See all →")
    txns = [
        ("UBA",      "Transfer Received", "+₦50,000", GREEN),
        ("MTN",      "Data Bundle",       "-₦3,000",  FG3),
        ("Shoprite", "Groceries",         "-₦8,200",  FG3),
        ("DSTV",     "Subscription",      "-₦4,900",  FG3),
    ]
    for name, desc, amt, color in txns:
        rr(draw, [PAD, ry, W-PAD, ry+108], 18, fill=SURFACE, outline=BORDER, w=1)
        ix, iy = PAD+60, ry+54
        draw.ellipse([ix-30, iy-30, ix+30, iy+30], fill=blend(ACCENT, SURFACE))
        txt(draw, ix, iy, name[0], "bold", 32, ACCENT, anchor="mm")
        txt(draw, ix+52, ry+26, name, "semibold", 32, FG)
        txt(draw, ix+52, ry+66, desc, "regular",  26, FG2)
        txt(draw, W-PAD-28, ry+54, amt, "semibold", 34, color, anchor="rm")
        ry += 118

    draw_nav(draw, 0)
    return img

# ═════════════════════════════════════════════════════════════════════════════
#  SCREEN 2 — TRANSACTIONS
# ═════════════════════════════════════════════════════════════════════════════
def screen_transactions():
    img = make_bg()
    draw = ImageDraw.Draw(img)
    draw_status(draw)

    txt(draw, PAD, 130, "Transactions", "extrabold", 64, FG)

    # Month summary bar
    rr(draw, [PAD, 220, W-PAD, 328], 20, fill=SURFACE, outline=BORDER, w=1)
    txt(draw, PAD+36, 274, "October 2026", "semibold", 34, FG2, anchor="lm")
    txt(draw, W-PAD-36, 274, "₦247,800 net", "bold", 34, GREEN, anchor="rm")

    # Transaction groups
    groups = [
        ("Today", [
            ("UBA",      "Salary Credit",   "+₦280,000", GREEN, "08:00"),
            ("Konga",    "Electronics",     "-₦45,000",  RED,   "14:32"),
            ("Bolt",     "Ride",            "-₦1,500",   FG3,   "19:11"),
        ]),
        ("Yesterday", [
            ("GTBank",   "Transfer In",     "+₦50,000",  GREEN, "11:20"),
            ("DSTV",     "Subscription",    "-₦4,900",   FG3,   "12:00"),
            ("Shoprite", "Groceries",       "-₦8,200",   FG3,   "17:45"),
        ]),
        ("Mon 20 Oct", [
            ("MTN",      "Data Bundle",     "-₦3,000",   FG3,   "09:05"),
            ("Jumia",    "Shopping",        "-₦12,400",  FG3,   "10:18"),
        ]),
    ]

    ty = 368
    for group_label, txns in groups:
        if ty > H - NAV_H - 160:
            break
        txt(draw, PAD, ty, group_label, "semibold", 30, FG3)
        ty += 44
        for name, desc, amt, color, time_str in txns:
            if ty > H - NAV_H - 120:
                break
            rr(draw, [PAD, ty, W-PAD, ty+112], 18, fill=SURFACE, outline=BORDER, w=1)
            ix, iy = PAD+62, ty+56
            draw.ellipse([ix-32, iy-32, ix+32, iy+32], fill=blend(ACCENT, SURFACE))
            txt(draw, ix, iy,     name[0],  "bold",    36, ACCENT, anchor="mm")
            txt(draw, ix+52, ty+28, name,   "semibold", 34, FG)
            txt(draw, ix+52, ty+68, desc,   "regular",  28, FG2)
            txt(draw, W-PAD-30, ty+30, amt, "semibold", 34, color, anchor="rt")
            txt(draw, W-PAD-30, ty+72, time_str, "regular", 26, FG3, anchor="rt")
            ty += 124
        ty += 18

    draw_nav(draw, 1)
    return img

# ═════════════════════════════════════════════════════════════════════════════
#  SCREEN 3 — ANALYTICS
# ═════════════════════════════════════════════════════════════════════════════
def screen_analytics():
    img = make_bg()
    draw = ImageDraw.Draw(img)
    draw_status(draw)

    txt(draw, PAD, 130, "Spending", "extrabold", 64, FG)
    txt(draw, PAD, 206, "October 2026 · ₦164,200", "regular", 36, FG2)

    slices = [
        ("Food & Dining",  32, GREEN),
        ("Bills",          24, ACCENT),
        ("Transport",      18, PURPLE),
        ("Shopping",       14, YELLOW),
        ("Entertainment",   8, RED),
        ("Others",          4, FG3),
    ]
    cx, cy = W//2, 650
    img = draw_donut(img, cx, cy, 240, 148, slices)
    draw = ImageDraw.Draw(img)

    # Donut center label
    txt(draw, cx, cy-26, "Expenses", "regular", 32, FG2, anchor="mm")
    txt(draw, cx, cy+30, "₦164,200", "bold", 52, FG, anchor="mm")

    # Category list
    amounts = [52544, 39408, 29556, 22988, 13136, 6568]
    ly = section_header(draw, 966, "By Category")
    for (label, pct, color), amt in zip(slices, amounts):
        rr(draw, [PAD, ly, W-PAD, ly+148], 18, fill=SURFACE, outline=BORDER, w=1)
        # Color swatch
        draw.rounded_rectangle([PAD+22, ly+20, PAD+30, ly+72], radius=4, fill=color)
        txt(draw, PAD+50, ly+24, label,        "semibold", 32, FG)
        txt(draw, PAD+50, ly+66, f"₦{amt:,}", "regular",  28, FG2)
        txt(draw, W-PAD-28, ly+44, f"{pct}%",  "bold",     40, color, anchor="rm")
        progress_bar(draw, PAD+22, ly+106, W-PAD*2-44, 8, pct/100, color)
        ly += 162

    draw_nav(draw, 2)
    return img

# ═════════════════════════════════════════════════════════════════════════════
#  SCREEN 4 — BUDGET TRACKER
# ═════════════════════════════════════════════════════════════════════════════
def screen_budget():
    img = make_bg()
    draw = ImageDraw.Draw(img)
    draw_status(draw)

    txt(draw, PAD, 130, "Budget Tracker", "extrabold", 60, FG)
    txt(draw, PAD, 208, "October 2026", "regular", 36, FG2)

    # Overall summary card
    rr(draw, [PAD, 276, W-PAD, 452], 24, fill=SURFACE, outline=BORDER, w=1)
    rr(draw, [PAD+28, 304, W-PAD-28, 424], 16, fill=SURFACE2)
    tw = W - PAD*2 - 56
    spent_pct = 0.40
    rr(draw, [PAD+28, 304, PAD+28+max(16, int(tw*spent_pct)), 424], 16, fill=blend(GREEN, SURFACE2, 0.6))
    txt(draw, PAD+50, 364, "Monthly budget", "semibold", 30, FG2, anchor="lm")
    txt(draw, W-PAD-50, 364, "40% used", "bold", 30, GREEN, anchor="rm")
    txt(draw, PAD+50, 408, "₦164,200 of ₦410,000", "regular", 28, FG2, anchor="lm")

    # Budget categories
    budgets = [
        ("Food & Dining",   52544,  80000,  GREEN),
        ("Bills & Utilities",39408, 45000,  YELLOW),
        ("Transport",       29556,  40000,  ACCENT),
        ("Shopping",        22988,  30000,  PURPLE),
        ("Entertainment",   13136,  20000,  RED),
        ("Savings goal",        0, 100000,  GREEN),
    ]

    by = section_header(draw, 492, "Category Limits")
    for label, spent, limit, color in budgets:
        if by > H - NAV_H - 150:
            break
        pct = min(1.0, spent / limit) if limit > 0 else 0
        over = pct >= 1.0
        tile_color = blend(RED, SURFACE, 0.12) if over else SURFACE
        rr(draw, [PAD, by, W-PAD, by+148], 18, fill=tile_color, outline=BORDER, w=1)
        txt(draw, PAD+30, by+22, label, "semibold", 32, FG)
        remain = limit - spent
        status_color = RED if over else (YELLOW if pct > 0.8 else FG2)
        status_text  = "OVER BUDGET" if over else (f"₦{remain:,} left")
        txt(draw, PAD+30, by+66, status_text, "regular", 28, status_color)
        # Bar
        progress_bar(draw, PAD+30, by+100, W-PAD*2-60, 10, pct, color)
        # Amounts on right
        txt(draw, W-PAD-28, by+34,  f"₦{spent:,}",  "semibold", 30, FG,  anchor="rm")
        txt(draw, W-PAD-28, by+72,  f"of ₦{limit:,}", "regular", 26, FG3, anchor="rm")
        by += 158

    draw_nav(draw, 3)
    return img

# ─── Run ──────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    screens = [
        ("screenshot-1-dashboard.png",    screen_dashboard),
        ("screenshot-2-transactions.png", screen_transactions),
        ("screenshot-3-analytics.png",    screen_analytics),
        ("screenshot-4-budget.png",       screen_budget),
    ]
    for fname, fn in screens:
        print(f"  Generating {fname}...", end=" ", flush=True)
        img = fn()
        path = os.path.join(OUT_DIR, fname)
        img.save(path, "PNG", optimize=True)
        size_kb = os.path.getsize(path) // 1024
        print(f"done  ({size_kb} KB)")

    print(f"\nAll 4 screenshots saved to:\n  {OUT_DIR}")
