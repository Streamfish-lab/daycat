# -*- coding: utf-8 -*-
"""复刻 WidgetProvider.draw() 新版画法，用来在电脑上看效果。"""
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
FONTB = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
DENS = 3.0

def dp(v): return int(round(v * DENS))
def font(sz, bold=False):
    try:
        return ImageFont.truetype(FONTB if bold else FONT, int(round(sz)))
    except Exception:
        return ImageFont.truetype(FONT, int(round(sz)))
def alpha(c, a): return (c[0], c[1], c[2], a)
def hx(s): return tuple(int(s[i:i+2], 16) for i in (1, 3, 5))

LIGHT = dict(bg="#FFFFFF", fg="#24211C", sub="#9AA0AE", dark=False)
DARK = dict(bg="#16140F", fg="#F2EDE3", sub="#8B8172", dark=True)

courses = [
    ("高数", "东10A-104", 0, 1, "#5B87B8"),
    ("物理", "西2-201", 0, 3, "#4E9E8F"),
    ("英语", "三教302", 1, 1, "#7C6BB0"),
    ("制图", "东10A-208", 1, 5, "#C08A52"),
    ("高数", "东10A-104", 2, 1, "#5B87B8"),
    ("材基", "西2-105", 2, 3, "#A8637F"),
    ("体育", "西操场", 3, 3, "#6E8F76"),
    ("物实", "物理楼B12", 3, 7, "#8C7B5E"),
    ("思政", "三教101", 4, 1, "#5F8AA8"),
    ("计算机", "机房B305", 4, 5, "#9C6B8E"),
    ("英语", "三教302", 5, 1, "#7C6BB0"),
    ("高数", "东10A-104", 6, 3, "#5B87B8"),
]
WD = ["一", "二", "三", "四", "五", "六", "日"]
TODAY = 4

def blend(c, t, k):
    return (int(c[0] + (t[0] - c[0]) * k), int(c[1] + (t[1] - c[1]) * k), int(c[2] + (t[2] - c[2]) * k))

def fit(s, maxw, f):
    if f.getlength(s) <= maxw: return s
    n = len(s)
    while n > 1 and f.getlength(s[:n] + "…") > maxw:
        n -= 1
    return s[:1] if n <= 1 else s[:n] + "…"

def mid(d, cx, cy, text, f, fill):
    w = f.getlength(text)
    d.text((cx - w / 2, cy - f.size * 0.62), text, font=f, fill=fill)

def draw(wdp, hdp, pal, name, week=11, date="11/14 周四", off=0):
    W, H = dp(wdp), dp(hdp)
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=dp(20), fill=pal["bg"])

    pad, headh, weekh = dp(10), dp(20), dp(15)
    ay = pad + dp(10.5)
    d.text((pad, ay - dp(11)), "‹", font=font(dp(13)), fill=pal["sub"])
    d.text((pad + dp(92), ay - dp(11)), "›", font=font(dp(13)), fill=pal["sub"])
    d.text((pad + dp(16), ay - dp(9)), "第 %d 周" % week, font=font(dp(12), True), fill=pal["fg"])
    fr = font(dp(10.5))
    d.text((W - pad - fr.getlength(date), ay - dp(8)), date, font=fr, fill=pal["sub"])

    segs = sorted(set((j1 - 1) // 2 for (_, _, _, j1, _) in courses))
    rows = len(segs)
    grid_top = pad + headh + weekh
    grid_h = H - grid_top - pad
    colw = (W - pad * 2) / 7.0
    rowh = grid_h / float(rows)

    fh = font(dp(9.5))
    for i in range(7):
        cx = pad + i * colw + colw / 2
        cy = pad + headh + dp(6)
        isToday = (i == TODAY and off == 0)
        if isToday:
            mid(d, cx, cy, WD[i], font(dp(9.5), True), pal["fg"])
            d.ellipse([cx - dp(1.6), cy + dp(5) - dp(1.6), cx + dp(1.6), cy + dp(5) + dp(1.6)], fill=pal["fg"])
        else:
            mid(d, cx, cy, WD[i], fh, pal["sub"])

    showPlace = rowh >= dp(23)
    for si, sg in enumerate(segs):
        top = grid_top + si * rowh
        for (nm, pl, wd, j1, col) in courses:
            if (j1 - 1) // 2 != sg: continue
            c = hx(col)
            l = pad + wd * colw + dp(1.5)
            r = pad + (wd + 1) * colw - dp(1.5)
            t = top + dp(1.5)
            b = top + rowh - dp(1.5)
            soft = blend(c, (255, 255, 255), 0.72) if not pal.get("dark") else blend(c, (0, 0, 0), 0.72)
            d.rounded_rectangle([l, t, r, b], radius=dp(8), fill=soft)
            tc = blend(c, (0, 0, 0), 0.55) if not pal.get("dark") else blend(c, (255, 255, 255), 0.78)
            avail = (r - l) - dp(1.5)
            fs = min(dp(10), rowh * (0.34 if showPlace else 0.48))
            fs = max(dp(7), fs)
            fn = font(fs, True)
            show = fit(nm, avail, fn)
            if showPlace and pl:
                fp = font(max(dp(6), fs * 0.62))
                cm = (t + b) / 2
                mid(d, (l + r) / 2, cm - dp(1), show, fn, tc)
                mid(d, (l + r) / 2, cm + dp(8), fit(pl, avail, fp), fp, alpha(tc, 205))
            else:
                mid(d, (l + r) / 2, (t + b) / 2, show, fn, tc)
    im.convert("RGB").save("/tmp/wn_%s.png" % name)

draw(250, 180, LIGHT, "4x3_light")
draw(250, 180, DARK, "4x3_dark")
draw(320, 220, LIGHT, "5x4_light")
draw(250, 110, LIGHT, "4x2_light")
print("done")
