#!/usr/bin/env python3
"""
CACREE App Store Asset Generator — Light Theme (matches real app)
Generates 4 Google Play screenshots at 1080×1920 px using Pillow + Inter fonts.

Usage:  python3 generate.py
Output: ./  (same directory, PNG files)
"""

from PIL import Image, ImageDraw, ImageFont
import os, math

FONT_DIR = "/usr/share/fonts/opentype/inter"
OUT_DIR  = os.path.dirname(os.path.abspath(__file__))
W, H     = 1080, 1920
PAD      = 60

# ── Palette (light theme, matching real app) ──────────────────────────────────
BG         = (247, 248, 250)   # page background
SURFACE    = (255, 255, 255)   # card white
SURFACE2   = (242, 244, 247)   # inner tile / alt surface
BORDER     = (229, 231, 235)   # card border / divider
FG         = ( 17,  24,  39)   # primary text
FG2        = (107, 114, 128)   # secondary text
FG3        = (156, 163, 175)   # tertiary / placeholder
GREEN      = ( 21, 128,  61)   # Money In card background (green-700)
GREEN_LIGHT= ( 34, 197,  94)   # lighter green accent
RED        = (185,  28,  28)   # Money Out card background (red-700)
RED_LIGHT  = (239,  68,  68)   # lighter red
ORANGE     = (234,  88,  12)   # Transfers / donut orange
ORANGE_BG  = (255, 247, 237)   # orange tint background
BLUE       = ( 37,  99, 235)   # Bank Debits blue
YELLOW     = (202, 138,   4)   # Airtime & Data yellow
GRAY_DOT   = (156, 163, 175)   # Other (small) gray
NAV_BG     = (255, 255, 255)
NAV_ACTIVE = ( 22, 163,  74)   # active tab green
NAV_INACT  = (156, 163, 175)
STATUS_BAR = ( 17,  24,  39)

# ── Font loader ───────────────────────────────────────────────────────────────
_font_cache = {}
def fnt(weight, size):
    key = (weight, size)
    if key not in _font_cache:
        mapping = {
            "regular": "Inter-Regular.otf",
            "medium":  "Inter-Medium.otf",
            "semibold":"Inter-SemiBold.otf",
            "bold":    "Inter-Bold.otf",
            "extrabold":"Inter-ExtraBold.otf",
        }
        path = os.path.join(FONT_DIR, mapping.get(weight, "Inter-Regular.otf"))
        try:
            _font_cache[key] = ImageFont.truetype(path, size)
        except Exception:
            _font_cache[key] = ImageFont.load_default()
    return _font_cache[key]

# ── Drawing helpers ───────────────────────────────────────────────────────────
def rounded_rect(draw, xy, radius, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = xy
    r = radius
    if fill:
        draw.rounded_rectangle([x0, y0, x1, y1], radius=r, fill=fill,
                                outline=outline, width=width)
    else:
        draw.rounded_rectangle([x0, y0, x1, y1], radius=r,
                                outline=outline, width=width)

def text_c(draw, text, cx, y, font, fill):
    """Draw text centred at cx."""
    bb = draw.textbbox((0, 0), text, font=font)
    w = bb[2] - bb[0]
    draw.text((cx - w // 2, y), text, font=font, fill=fill)

def shadow_rect(img, xy, radius=20, fill=SURFACE, shadow_blur=12):
    """White card with soft shadow using alpha compositing."""
    from PIL import ImageFilter
    x0, y0, x1, y1 = xy
    # shadow layer
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(sh)
    sd.rounded_rectangle([x0+4, y0+4, x1+4, y1+4], radius=radius,
                          fill=(0, 0, 0, 40))
    sh = sh.filter(ImageFilter.GaussianBlur(shadow_blur))
    img.paste(sh, mask=sh)
    # card
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill)
    return d

def status_bar(draw):
    draw.text((PAD, 36), "10:05", font=fnt("semibold", 36), fill=STATUS_BAR)
    draw.text((W - PAD - 80, 36), "56%  ▶", font=fnt("medium", 28), fill=FG2)

def nav_bar(img, active):
    """Bottom navigation bar.  active: 0=Home 1=Txns 2=Budgets 3=Insights 4=Profile"""
    d = ImageDraw.Draw(img)
    bar_h = 130
    bar_y = H - bar_h
    d.rectangle([0, bar_y, W, H], fill=NAV_BG)
    d.line([0, bar_y, W, bar_y], fill=BORDER, width=1)
    labels = ["Home", "Txns", "Budgets", "Insights", "Profile"]
    icons  = ["⌂", "≡", "◎", "∿", "👤"]
    step = W // 5
    for i, (label, icon) in enumerate(zip(labels, icons)):
        cx = step * i + step // 2
        col = NAV_ACTIVE if i == active else NAV_INACT
        text_c(d, label, cx, bar_y + 70, fnt("medium", 26), col)
        # simple dot for active
        if i == active:
            d.ellipse([cx-4, bar_y+18, cx+4, bar_y+26], fill=NAV_ACTIVE)

def app_header(draw, title_right=None):
    """CACREE logo + optional right element."""
    # green C= logo circle
    draw.ellipse([PAD, 90, PAD+68, 158], fill=GREEN)
    draw.text((PAD+10, 103), "C=", font=fnt("extrabold", 34), fill=(255,255,255))
    draw.text((PAD+80, 97), "CACREE", font=fnt("bold", 38), fill=FG)
    draw.text((PAD+80, 138), "Track Every Shilling", font=fnt("regular", 26), fill=FG2)
    if title_right:
        bb = draw.textbbox((0,0), title_right, font=fnt("semibold", 26))
        tw = bb[2]-bb[0]
        # pill button
        rx = W - PAD - tw - 40
        draw.rounded_rectangle([rx-10, 102, W-PAD+4, 148], radius=22,
                                fill=SURFACE2, outline=BORDER, width=1)
        draw.text((rx+6, 114), title_right, font=fnt("semibold", 26), fill=FG)
    # bell
    bx = W - PAD - (120 if title_right else 0) - 60
    draw.ellipse([bx, 100, bx+48, 148], fill=SURFACE2)
    draw.text((bx+9, 110), "🔔", font=fnt("regular", 26), fill=FG2)

# ── Screen 1 — Home Dashboard ────────────────────────────────────────────────
def screen_dashboard():
    img = Image.new("RGB", (W, H), BG)
    d   = ImageDraw.Draw(img)

    status_bar(d)
    app_header(d, "This Month ▾")

    y = 180
    # On-device badge
    d.rounded_rectangle([PAD, y, PAD+210, y+46], radius=23,
                         fill=None, outline=GREEN, width=2)
    d.text((PAD+14, y+8), "🔒 On-device", font=fnt("medium", 24), fill=GREEN)
    y += 70

    # Money In / Money Out cards
    cw = (W - PAD*2 - 20) // 2
    ch = 190
    # Money In
    d.rounded_rectangle([PAD, y, PAD+cw, y+ch], radius=22, fill=GREEN)
    d.text((PAD+24, y+22), "MONEY IN", font=fnt("semibold", 22), fill=(187,247,208))
    d.text((PAD+24, y+58), "TSh 70.5k", font=fnt("bold", 46), fill=(255,255,255))
    d.text((PAD+24, y+118), "Oct 2026", font=fnt("regular", 24), fill=(187,247,208))
    # Money Out
    ox = PAD + cw + 20
    d.rounded_rectangle([ox, y, ox+cw, y+ch], radius=22, fill=RED)
    d.text((ox+24, y+22), "MONEY OUT", font=fnt("semibold", 22), fill=(254,202,202))
    d.text((ox+24, y+58), "TSh 47.0M", font=fnt("bold", 46), fill=(255,255,255))
    d.text((ox+24, y+118), "12 payments", font=fnt("regular", 24), fill=(254,202,202))
    y += ch + 36

    # Spending Overview card
    d.text((PAD, y), "Spending Overview", font=fnt("bold", 34), fill=FG)
    d.text((W-PAD-120, y+6), "Oct 2026", font=fnt("regular", 24), fill=FG2)
    y += 48
    shadow_rect(img, [PAD, y, W-PAD, y+240], radius=22)
    d2 = ImageDraw.Draw(img)

    # Donut chart (simplified)
    cx_d, cy_d, r_outer, r_inner = PAD+130, y+120, 100, 58
    # Transfers 100% → orange full ring
    d2.ellipse([cx_d-r_outer, cy_d-r_outer, cx_d+r_outer, cy_d+r_outer],
               fill=ORANGE)
    d2.ellipse([cx_d-r_inner, cy_d-r_inner, cx_d+r_inner, cy_d+r_inner],
               fill=SURFACE)
    d2.text((cx_d-30, cy_d-20), "TSh", font=fnt("regular", 20), fill=FG2)
    d2.text((cx_d-52, cy_d+2), "47.0M", font=fnt("bold", 28), fill=FG)

    # Legend
    lx, ly = PAD + 270, y + 28
    items = [
        (ORANGE, "Transfers", "100%", "TSh 47.0M"),
        (GRAY_DOT, "Other (small)", "<1%", "TSh 4.1k"),
    ]
    for col, label, pct, amount in items:
        d2.ellipse([lx, ly+6, lx+18, ly+24], fill=col)
        d2.text((lx+28, ly), label, font=fnt("medium", 26), fill=FG)
        d2.text((lx+28, ly+32), pct, font=fnt("regular", 22), fill=FG2)
        d2.text((W-PAD-20, ly), amount,
                font=fnt("semibold", 26), fill=FG,
                anchor="ra" if hasattr(d2, "anchor") else None)
        # fallback right-align
        bb = d2.textbbox((0,0), amount, font=fnt("semibold", 26))
        d2.rectangle([W-PAD-20-(bb[2]-bb[0])-2, ly-2, W-PAD, ly+40], fill=SURFACE)
        d2.text((W-PAD-20-(bb[2]-bb[0]), ly), amount, font=fnt("semibold", 26), fill=FG)
        ly += 74
    y += 260

    # Biggest Spending Category card
    shadow_rect(img, [PAD, y, W-PAD, y+220], radius=22, fill=ORANGE_BG)
    d3 = ImageDraw.Draw(img)
    d3.text((PAD+24, y+20), "BIGGEST SPENDING CATEGORY",
            font=fnt("semibold", 20), fill=ORANGE)
    # badge
    bw = 320
    d3.rounded_rectangle([W-PAD-bw-10, y+14, W-PAD-10, y+52], radius=16,
                          fill=(254,226,226))
    d3.text((W-PAD-bw+6, y+20),
            "↗ +47016432% vs last month",
            font=fnt("medium", 20), fill=RED)
    d3.text((PAD+24, y+58), "Transfers", font=fnt("bold", 30), fill=FG)
    d3.text((PAD+24, y+96), "TSh 47.0M", font=fnt("extrabold", 62), fill=ORANGE)
    d3.text((PAD+24, y+174), "100% of total spending", font=fnt("regular", 24), fill=FG2)
    y += 240

    # Money In vs Out
    d.text((PAD, y), "Money In vs Out", font=fnt("bold", 34), fill=FG)
    y += 48
    shadow_rect(img, [PAD, y, W-PAD, y+180], radius=22)
    d4 = ImageDraw.Draw(img)
    d4.text((PAD+24, y+20), "Money In", font=fnt("semibold", 28), fill=GREEN)
    bb = d4.textbbox((0,0), "TSh 70.5k", font=fnt("bold", 28))
    d4.text((W-PAD-24-(bb[2]-bb[0]), y+20), "TSh 70.5k", font=fnt("bold", 28), fill=FG)
    bar_w = W - PAD*2 - 48
    d4.rounded_rectangle([PAD+24, y+62, PAD+24+int(bar_w*0.30), y+78],
                          radius=4, fill=GREEN_LIGHT)
    d4.text((PAD+24, y+98), "Money Out", font=fnt("semibold", 28), fill=RED)
    bb2 = d4.textbbox((0,0), "TSh 47.0M", font=fnt("bold", 28))
    d4.text((W-PAD-24-(bb2[2]-bb2[0]), y+98), "TSh 47.0M", font=fnt("bold", 28), fill=FG)
    d4.rounded_rectangle([PAD+24, y+140, PAD+24+bar_w, y+156],
                          radius=4, fill=RED_LIGHT)

    nav_bar(img, 0)
    out = os.path.join(OUT_DIR, "screenshot-1-dashboard.png")
    img.save(out, "PNG", optimize=True)
    print(f"  Generating screenshot-1-dashboard.png... done  ({os.path.getsize(out)//1024} KB)")


# ── Screen 2 — Transactions ──────────────────────────────────────────────────
def screen_transactions():
    img = Image.new("RGB", (W, H), BG)
    d   = ImageDraw.Draw(img)

    status_bar(d)

    y = 90
    d.text((PAD, y), "Transactions", font=fnt("extrabold", 52), fill=FG)
    # Export button
    d.rounded_rectangle([W-PAD-160, y+4, W-PAD, y+58], radius=28,
                         fill=None, outline=GREEN, width=2)
    d.text((W-PAD-140, y+12), "Export", font=fnt("semibold", 30), fill=GREEN)
    y += 82

    # Filter tabs
    tabs = [("All", False), ("Income", False), ("Expenses", True)]
    tx = PAD
    for label, active in tabs:
        bb = d.textbbox((0,0), label, font=fnt("semibold", 28))
        tw = bb[2] - bb[0] + 48
        if active:
            d.rounded_rectangle([tx, y, tx+tw, y+52], radius=26, fill=GREEN)
            d.text((tx+24, y+10), label, font=fnt("semibold", 28), fill=(255,255,255))
        else:
            d.rounded_rectangle([tx, y, tx+tw, y+52], radius=26,
                                 fill=SURFACE2, outline=BORDER, width=1)
            d.text((tx+24, y+10), label, font=fnt("semibold", 28), fill=FG2)
        tx += tw + 20
    y += 74

    # Category chips
    chips = ["All Categories", "🍔 Food & Drinks", "🚌 Transport", "🎁 Shopping"]
    cx = PAD
    for chip in chips:
        bb = d.textbbox((0,0), chip, font=fnt("medium", 24))
        cw = bb[2] - bb[0] + 36
        if cx + cw > W - PAD:
            break
        d.rounded_rectangle([cx, y, cx+cw, y+46], radius=23,
                             fill=SURFACE2, outline=BORDER, width=1)
        d.text((cx+18, y+9), chip, font=fnt("medium", 24), fill=FG)
        cx += cw + 16
    y += 68

    # Search bar
    shadow_rect(img, [PAD, y, W-PAD, y+68], radius=16)
    d2 = ImageDraw.Draw(img)
    d2.text((PAD+24, y+14), "🔍  Search merchant, category, amount…",
            font=fnt("regular", 26), fill=FG3)
    y += 88

    # Free trial banner
    d2.rounded_rectangle([PAD, y, W-PAD, y+60], radius=14,
                          fill=None, outline=(251,191,36), width=2)
    d2.text((PAD+20, y+14), "✨ Free trial · 13 days left of full access",
            font=fnt("medium", 26), fill=(161,98,7))
    y += 80

    # Transactions grouped by date
    txn_data = [
        ("2026-10-10", "-TSh 500", [
            ("Mobile Money TZ Transaction", "Transfers · Mobile Money TZ · 2026-10-10", "-TSh 500"),
        ]),
        ("2026-10-08", "-TSh 1.9M", [
            ("Mobile Money TZ Transaction", "Transfers · Mobile Money TZ · 2026-10-08", "-TSh 1.9M"),
        ]),
        ("2026-10-07", "-TSh 45.1M", [
            ("Mobile Money TZ Transaction", "Transfers · Mobile Money TZ · 2026-10-07", "-TSh 105.0k"),
            ("Mobile Money TZ Transaction", "Transfers · Mobile Money TZ · 2026-10-07", "-TSh 8.3k"),
            ("M-Pesa TZ Transaction",       "Loan & Debt · M-Pesa TZ · 2026-10-07",     "-TSh 45.0M"),
        ]),
    ]

    for date, day_total, rows in txn_data:
        if y > H - 200:
            break
        d2.text((PAD, y), date, font=fnt("semibold", 26), fill=FG2)
        bb = d2.textbbox((0,0), day_total, font=fnt("semibold", 26))
        d2.text((W-PAD-(bb[2]-bb[0]), y), day_total, font=fnt("semibold", 26), fill=FG2)
        y += 42

        shadow_rect(img, [PAD, y, W-PAD, y + len(rows)*110 - 10], radius=20)
        d3 = ImageDraw.Draw(img)
        for i, (name, sub, amt) in enumerate(rows):
            ry = y + i*110 + 20
            # orange icon
            d3.rounded_rectangle([PAD+20, ry, PAD+88, ry+68], radius=16, fill=ORANGE_BG)
            d3.text((PAD+28, ry+12), "⇄", font=fnt("bold", 38), fill=ORANGE)
            d3.text((PAD+108, ry+4), name, font=fnt("semibold", 28), fill=FG)
            d3.text((PAD+108, ry+38), sub, font=fnt("regular", 22), fill=FG2)
            bb = d3.textbbox((0,0), amt, font=fnt("bold", 28))
            d3.text((W-PAD-20-(bb[2]-bb[0]), ry+16), amt, font=fnt("bold", 28), fill=RED)
            if i < len(rows)-1:
                d3.line([PAD+20, y+(i+1)*110, W-PAD-20, y+(i+1)*110],
                        fill=BORDER, width=1)
        y += len(rows)*110 + 20

    nav_bar(img, 1)
    out = os.path.join(OUT_DIR, "screenshot-2-transactions.png")
    img.save(out, "PNG", optimize=True)
    print(f"  Generating screenshot-2-transactions.png... done  ({os.path.getsize(out)//1024} KB)")


# ── Screen 3 — Smart Insights ────────────────────────────────────────────────
def screen_insights():
    img = Image.new("RGB", (W, H), BG)
    d   = ImageDraw.Draw(img)

    status_bar(d)

    y = 90
    d.text((PAD, y), "Smart Insights", font=fnt("extrabold", 52), fill=FG)
    y += 80

    # Spending Breakdown
    d.text((PAD, y), "Spending Breakdown", font=fnt("bold", 34), fill=FG)
    d.text((W-PAD-100, y+6), "All Time", font=fnt("regular", 24), fill=FG2)
    y += 48

    shadow_rect(img, [PAD, y, W-PAD, y+340], radius=22)
    d2 = ImageDraw.Draw(img)

    # Donut chart — 4 categories
    cx_d, cy_d = W//2, y+170
    r_o, r_i = 120, 70
    slices = [
        (ORANGE, 0.72, "Transfers"),
        (BLUE,   0.14, "Bank Debits"),
        (YELLOW, 0.12, "Airtime & Data"),
        (GRAY_DOT,0.02,"Other"),
    ]
    start = -math.pi/2
    for col, frac, _ in slices:
        end = start + frac * 2 * math.pi
        # polygon approximation
        steps = max(3, int(60 * frac))
        pts = []
        for s in range(steps+1):
            a = start + (end-start)*s/steps
            pts.append((cx_d + r_o*math.cos(a), cy_d + r_o*math.sin(a)))
        for s in range(steps, -1, -1):
            a = start + (end-start)*s/steps
            pts.append((cx_d + r_i*math.cos(a), cy_d + r_i*math.sin(a)))
        if len(pts) >= 3:
            d2.polygon(pts, fill=col)
        start = end

    d2.text((cx_d-26, cy_d-22), "TSh", font=fnt("regular", 22), fill=FG2)
    d2.text((cx_d-70, cy_d+2),  "228.8M", font=fnt("bold", 34), fill=FG)

    # Legend right side
    lx, ly = PAD + 24, y + 28
    leg = [
        (ORANGE,   "Transfers",      "72%", "TSh 164.1M"),
        (BLUE,     "Bank Debits",    "14%", "TSh 30.9M"),
        (YELLOW,   "Airtime & Data", "12%", "TSh 26.6M"),
        (GRAY_DOT, "Other (small)", " 3%", "TSh 7.2M"),
    ]
    for col, label, pct, amount in leg:
        d2.ellipse([lx, ly+8, lx+20, ly+28], fill=col)
        d2.text((lx+32, ly), label, font=fnt("medium", 26), fill=FG)
        d2.text((lx+32+200, ly), pct, font=fnt("regular", 26), fill=FG2)
        bb = d2.textbbox((0,0), amount, font=fnt("semibold", 26))
        d2.text((W-PAD-24-(bb[2]-bb[0]), ly), amount, font=fnt("semibold", 26), fill=FG)
        ly += 72
    y += 360

    # Cash Flow Timeline
    d.text((PAD, y), "Cash Flow Timeline", font=fnt("bold", 34), fill=FG)
    d.text((W-PAD-110, y+6), "Monthly", font=fnt("regular", 24), fill=FG2)
    y += 48

    shadow_rect(img, [PAD, y, W-PAD, y+340], radius=22)
    d3 = ImageDraw.Draw(img)

    # Stat tiles
    tw2 = (W - PAD*2 - 20) // 2
    d3.rounded_rectangle([PAD+16, y+16, PAD+16+tw2, y+100], radius=14, fill=SURFACE2)
    d3.text((PAD+36, y+26), "AVG DAILY SPEND", font=fnt("semibold", 20), fill=FG2)
    d3.text((PAD+36, y+54), "TSh 23.4k", font=fnt("bold", 30), fill=FG)

    ox2 = PAD+16+tw2+20
    d3.rounded_rectangle([ox2, y+16, ox2+tw2, y+100], radius=14, fill=SURFACE2)
    d3.text((ox2+20, y+26), "SAVINGS RATE", font=fnt("semibold", 20), fill=FG2)
    d3.text((ox2+20, y+54), "-228%", font=fnt("bold", 30), fill=RED)

    # Bar chart: months Nov–Oct
    months = ["Nov", "Dec", "Jan", "Feb", "Mar", "Aug", "Sep", "Oct"]
    income_h = [0,  0,  60,  80,  70,  0,  0,   20]
    expense_h= [0,  0,  80, 100,  90,  0,  0,  180]
    chart_y  = y + 280
    chart_bot= y + 320
    bw       = 18
    spacing  = (W - PAD*2 - 32) // len(months)
    for i, (m, ih, eh) in enumerate(zip(months, income_h, expense_h)):
        bx = PAD + 24 + i*spacing + spacing//2
        if ih:
            d3.rounded_rectangle([bx-bw-4, chart_bot-ih, bx-4, chart_bot],
                                  radius=4, fill=GREEN_LIGHT)
        if eh:
            d3.rounded_rectangle([bx+4, chart_bot-eh, bx+4+bw, chart_bot],
                                  radius=4, fill=RED_LIGHT)
        d3.text((bx-12, chart_bot+8), m, font=fnt("regular", 20), fill=FG2)

    # Legend
    lx2 = PAD + 80
    d3.ellipse([lx2, chart_bot+42, lx2+18, chart_bot+60], fill=GREEN_LIGHT)
    d3.text((lx2+26, chart_bot+40), "Income", font=fnt("medium", 24), fill=FG2)
    lx2 += 170
    d3.ellipse([lx2, chart_bot+42, lx2+18, chart_bot+60], fill=RED_LIGHT)
    d3.text((lx2+26, chart_bot+40), "Expenses", font=fnt("medium", 24), fill=FG2)
    y += 360

    # Electricity LUKU
    d.text((PAD, y), "Electricity · LUKU", font=fnt("bold", 34), fill=FG)
    y += 48
    shadow_rect(img, [PAD, y, W-PAD, y+160], radius=22)
    d4 = ImageDraw.Draw(img)
    cols3 = [
        ("SPENT",     "TSh 129.8k"),
        ("TOKENS",    "12"),
        ("AVG TOKEN", "TSh 10.8k"),
    ]
    step3 = (W - PAD*2) // 3
    for i, (label, val) in enumerate(cols3):
        cx3 = PAD + i*step3 + step3//2
        bb = d4.textbbox((0,0), label, font=fnt("semibold", 20))
        d4.text((cx3-(bb[2]-bb[0])//2, y+20), label, font=fnt("semibold", 20), fill=FG2)
        bb2 = d4.textbbox((0,0), val, font=fnt("bold", 30))
        d4.text((cx3-(bb2[2]-bb2[0])//2, y+52), val, font=fnt("bold", 30), fill=FG)
    d4.text((PAD+24, y+104),
            "⚡ Next LUKU token around Apr 11 (every ~62 days)",
            font=fnt("medium", 24), fill=(161, 98, 7))

    nav_bar(img, 3)
    out = os.path.join(OUT_DIR, "screenshot-3-insights.png")
    img.save(out, "PNG", optimize=True)
    print(f"  Generating screenshot-3-insights.png... done  ({os.path.getsize(out)//1024} KB)")


# ── Screen 4 — Budget Planner ────────────────────────────────────────────────
def screen_budget():
    img = Image.new("RGB", (W, H), BG)
    d   = ImageDraw.Draw(img)

    status_bar(d)

    y = 90
    d.text((PAD, y), "Budget Planner", font=fnt("extrabold", 52), fill=FG)
    # + Add button
    d.rounded_rectangle([W-PAD-140, y+4, W-PAD, y+60], radius=28,
                         fill=None, outline=GREEN, width=2)
    d.text((W-PAD-120, y+14), "+ Add", font=fnt("semibold", 30), fill=GREEN)
    y += 90

    # Monthly budget summary card
    shadow_rect(img, [PAD, y, W-PAD, y+160], radius=22)
    d2 = ImageDraw.Draw(img)
    d2.text((PAD+24, y+20), "OCT 2026 BUDGET", font=fnt("semibold", 22), fill=FG2)
    d2.text((PAD+24, y+54), "TSh 100", font=fnt("extrabold", 54), fill=FG)
    d2.text((PAD+260, y+74), "of TSh 3.5M", font=fnt("regular", 26), fill=FG2)
    d2.text((PAD+24, y+118), "TSh 3.5M remaining", font=fnt("semibold", 28), fill=GREEN)
    y += 180

    # Category rows
    categories = [
        ("🍔", "Food & Drinks",  "TSh 0 spent",    "TSh 500.0k",  0.0),
        ("🚌", "Transport",      "TSh 0 spent",    "TSh 500.0k",  0.0),
        ("📱", "Airtime & Data", "TSh 100 spent",  "TSh 40.0k",   0.003),
        ("🏠", "Housing",        "TSh 0 spent",    "TSh 1.5M",    0.0),
        ("🎁", "Shopping",       "TSh 0 spent",    "TSh 1.0M",    0.0),
    ]

    shadow_rect(img, [PAD, y, W-PAD, y + len(categories)*156 - 10], radius=22)
    d3 = ImageDraw.Draw(img)

    for i, (icon, name, spent, limit, pct) in enumerate(categories):
        ry = y + i*156 + 24
        d3.text((PAD+24, ry), icon + "  " + name, font=fnt("semibold", 30), fill=FG)

        pct_col = RED if pct > 0.8 else (YELLOW if pct > 0.5 else GREEN)
        pct_label = f"{int(pct*100)}%"
        bb = d3.textbbox((0,0), pct_label, font=fnt("semibold", 26))
        d3.text((W-PAD-20-(bb[2]-bb[0]), ry), pct_label,
                font=fnt("semibold", 26), fill=pct_col)

        d3.text((PAD+24, ry+42), spent, font=fnt("regular", 24), fill=FG2)
        bb2 = d3.textbbox((0,0), f"of {limit}", font=fnt("regular", 24))
        d3.text((W-PAD-20-(bb2[2]-bb2[0]), ry+42), f"of {limit}",
                font=fnt("regular", 24), fill=FG2)

        # Progress bar track
        bar_x0, bar_x1 = PAD+24, W-PAD-24
        bar_y0, bar_y1 = ry+80, ry+96
        d3.rounded_rectangle([bar_x0, bar_y0, bar_x1, bar_y1], radius=4,
                              fill=SURFACE2)
        if pct > 0:
            fill_x = bar_x0 + int((bar_x1-bar_x0)*pct)
            d3.rounded_rectangle([bar_x0, bar_y0, fill_x, bar_y1], radius=4,
                                  fill=pct_col)

        # divider
        if i < len(categories)-1:
            d3.line([PAD+20, y+(i+1)*156, W-PAD-20, y+(i+1)*156],
                    fill=BORDER, width=1)

    nav_bar(img, 2)
    out = os.path.join(OUT_DIR, "screenshot-4-budget.png")
    img.save(out, "PNG", optimize=True)
    print(f"  Generating screenshot-4-budget.png... done  ({os.path.getsize(out)//1024} KB)")


# ── Main ──────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    screen_dashboard()
    screen_transactions()
    screen_insights()
    screen_budget()
    print(f"\nAll 4 screenshots saved to:\n  {OUT_DIR}\n")
