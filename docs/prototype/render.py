#!/usr/bin/env python3
"""Рендер визуального прототипа витрины Wasat Shop в PNG (Pillow).

Макет на реальных токенах дизайн-системы приложения (core/designsystem/Color.kt).
Три экрана-телефона: Каталог, Карточка товара, Корзина+чекаут.
"""
from PIL import Image, ImageDraw, ImageFont

# --- токены темы ---
PRIMARY = (45, 74, 124)
ON_PRIMARY = (255, 255, 255)
SECONDARY = (91, 107, 130)
BACKGROUND = (252, 252, 255)
SURFACE = (255, 255, 255)
SURFACE_VAR = (238, 241, 246)
ON_BG = (26, 28, 30)
OUTLINE = (154, 160, 171)
ERROR = (186, 26, 26)
PRI_CONTAINER = (215, 227, 255)
GOOD = (46, 125, 50)
STAR = (245, 166, 35)

F = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def font(size, bold=False):
    return ImageFont.truetype(FB if bold else F, size)


# --- геометрия ---
PW, PH = 330, 680      # размер «телефона»
MARGIN = 30
TITLE_H = 90
W = MARGIN * 4 + PW * 3
H = TITLE_H + PH + 70

img = Image.new("RGB", (W, H), (232, 237, 245))
d = ImageDraw.Draw(img)


def rr(box, radius, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def text(xy, s, fnt, fill=ON_BG, anchor="la"):
    d.text(xy, s, font=fnt, fill=fill, anchor=anchor)


def center(x0, x1, y, s, fnt, fill=ON_BG):
    d.text(((x0 + x1) / 2, y), s, font=fnt, fill=fill, anchor="ma")


# --- заголовок ---
center(0, W, 24, "Wasat Shop — прототип витрины", font(30, True), PRIMARY)
center(0, W, 64, "Material 3 · акцент #2D4A7C · макет ключевых экранов покупателя",
       font(15), SECONDARY)


def phone_base(ox):
    """Рамка телефона + статус-бар, возвращает (left, top) контентной зоны."""
    oy = TITLE_H
    # тень
    rr((ox + 4, oy + 6, ox + PW + 4, oy + PH + 6), 30, fill=(205, 211, 222))
    rr((ox, oy, ox + PW, oy + PH), 30, fill=BACKGROUND, outline=(44, 47, 51), width=3)
    return ox, oy


def appbar(ox, oy, title, sub=None, search=False, back=False):
    h = 96 if search else (70 if sub else 56)
    d.rounded_rectangle((ox + 3, oy + 3, ox + PW - 3, oy + 30 + h), radius=27,
                        fill=PRIMARY)
    d.rectangle((ox + 3, oy + 22, ox + PW - 3, oy + 30 + h), fill=PRIMARY)
    text((ox + 18, oy + 8), "9:41", font(11), (220, 228, 242))
    text((ox + PW - 18, oy + 8), "100% ▰▰▰", font(11), (220, 228, 242), anchor="ra")
    ty = oy + 30
    pre = "‹  " if back else ""
    text((ox + 18, ty), pre + title, font(18, True), ON_PRIMARY)
    if sub:
        text((ox + 18, ty + 26), sub, font(11), (211, 222, 245))
    if search:
        rr((ox + 14, ty + 44, ox + PW - 14, ty + 76), 16, fill=(63, 88, 134))
        text((ox + 28, ty + 52), "Поиск товаров…", font(13), (210, 218, 235))
    return oy + 30 + h + 10  # y контента


def bottomnav(ox, oy, active):
    by = oy + PH - 58
    d.rounded_rectangle((ox + 3, by, ox + PW - 3, oy + PH - 3), radius=27, fill=SURFACE)
    d.rectangle((ox + 3, by, ox + PW - 3, by + 20), fill=SURFACE)
    d.line((ox + 3, by, ox + PW - 3, by), fill=(227, 230, 236), width=1)
    items = ["Главная", "Каталог", "Корзина", "Профиль"]
    icons = ["⌂", "▤", "▣", "○"]
    cw = (PW - 6) / 4
    for i, (lab, ic) in enumerate(zip(items, icons)):
        cx = ox + 3 + cw * i + cw / 2
        col = PRIMARY if i == active else SECONDARY
        if i == active:
            rr((cx - 22, by + 8, cx + 22, by + 28), 12, fill=PRI_CONTAINER)
        d.text((cx, by + 10), ic, font=font(16, i == active), fill=col, anchor="ma")
        d.text((cx, by + 32), lab, font=font(10, i == active), fill=col, anchor="ma")


def product_thumb(box, label, accent):
    rr(box, 12, fill=SURFACE_VAR)
    d.text(((box[0] + box[2]) / 2, (box[1] + box[3]) / 2 - 6), label,
           font=font(13, True), fill=accent, anchor="mm")


# ===================== ЭКРАН 1: КАТАЛОГ =====================
ox, oy = phone_base(MARGIN)
cy = appbar(ox, oy, "Asat Clothes", "● Открыт · Доставка от 0 ₽", search=True)
# чипы
chips = [("Все", True), ("Куртки", False), ("Футболки", False), ("Обувь", False)]
cx = ox + 14
for lab, act in chips:
    w = font(12).getlength(lab) + 22
    rr((cx, cy, cx + w, cy + 26), 8,
       fill=PRI_CONTAINER if act else SURFACE, outline=OUTLINE if not act else None, width=1)
    text((cx + 11, cy + 6), lab, font(12), PRIMARY if act else ON_BG)
    cx += w + 8
cy += 34
# фильтр-строка
fx = ox + 14
for lab in ["Цена ▾", "В наличии ✓", "Новые ▾"]:
    w = font(11).getlength(lab) + 16
    rr((fx, cy, fx + w, cy + 24), 8, outline=OUTLINE, width=1)
    text((fx + 8, cy + 6), lab, font(11), SECONDARY)
    fx += w + 8
cy += 36
# сетка 2x3
cards = [
    ("Куртка Aurora", "7 990 ₽", "9 990 ₽", "Куртка", "-20%"),
    ("Футболка Basic", "1 290 ₽", None, "Футболка", None),
    ("Кроссовки Trail X", "5 490 ₽", None, "Обувь", None),
    ("Сумка City", "4 200 ₽", None, "Сумка", None),
    ("Кепка классич.", "990 ₽", None, "Кепка", None),
    ("Шарф шерстяной", "1 690 ₽", "1 990 ₽", "Шарф", "-15%"),
]
gap = 12
cw = (PW - 28 - gap) / 2
ch = 150
for i, (name, price, old, lbl, badge) in enumerate(cards):
    col = i % 2
    row = i // 2
    x0 = ox + 14 + col * (cw + gap)
    y0 = cy + row * (ch + gap)
    rr((x0, y0, x0 + cw, y0 + ch), 14, fill=SURFACE)
    product_thumb((x0, y0, x0 + cw, y0 + 86), lbl, SECONDARY)
    if badge:
        rr((x0 + 6, y0 + 6, x0 + 6 + 40, y0 + 24), 6, fill=ERROR)
        text((x0 + 26, y0 + 9), badge, font(10, True), ON_PRIMARY, anchor="ma")
    d.text((x0 + cw - 10, y0 + 8), "♥" if i in (0, 3) else "♡", font=font(15),
           fill=ERROR, anchor="ra")
    text((x0 + 9, y0 + 94), name, font(12, True), ON_BG)
    text((x0 + 9, y0 + 118), price, font(13, True), PRIMARY)
    if old:
        px = x0 + 9 + font(13, True).getlength(price) + 8
        d.text((px, y0 + 119), old, font=font(11), fill=OUTLINE)
        d.line((px, y0 + 125, px + font(11).getlength(old), y0 + 125), fill=OUTLINE, width=1)
bottomnav(ox, oy, 0)
center(ox, ox + PW, oy + PH + 12, "Каталог (FR-B02)", font(13, True), SECONDARY)

# ===================== ЭКРАН 2: КАРТОЧКА ТОВАРА =====================
ox, oy = phone_base(MARGIN * 2 + PW)
cy = appbar(ox, oy, "Куртка Aurora    ♡   ↗", back=True)
rr((ox + 14, cy, ox + PW - 14, cy + 150), 14, fill=SURFACE_VAR)
d.text(((ox + 14 + ox + PW - 14) / 2, cy + 65), "ФОТО ТОВАРА", font=font(15, True),
       fill=SECONDARY, anchor="ma")
# точки
dcx = (ox + PW) / 2
for k in range(3):
    fill = PRIMARY if k == 0 else OUTLINE
    d.ellipse((dcx - 24 + k * 18, cy + 160, dcx - 24 + k * 18 + 7, cy + 167), fill=fill)
yy = cy + 180
text((ox + 16, yy), "Куртка утеплённая Aurora", font(18, True), ON_BG)
text((ox + 16, yy + 28), "★ 4.6 · 28 отзывов", font(12), SECONDARY)
text((ox + 16, yy + 50), "7 990 ₽", font(22, True), PRIMARY)
px = ox + 16 + font(22, True).getlength("7 990 ₽") + 10
d.text((px, yy + 58), "9 990 ₽", font=font(14), fill=OUTLINE)
d.line((px, yy + 67, px + font(14).getlength("9 990 ₽"), yy + 67), fill=OUTLINE, width=1)
text((ox + 16, yy + 90), "Размер / цвет", font(12), SECONDARY)
vx = ox + 16
for lab, sel in [("M · синий", True), ("L · синий", False), ("M · чёрный", False)]:
    w = font(13).getlength(lab) + 22
    rr((vx, yy + 110, vx + w, yy + 138), 8,
       fill=PRI_CONTAINER if sel else SURFACE, outline=PRIMARY if sel else OUTLINE, width=1)
    text((vx + 11, yy + 117), lab, font(13), PRIMARY if sel else ON_BG)
    vx += w + 8
text((ox + 16, yy + 150), "● В наличии — 7 шт.", font(13), GOOD)
rr((ox + 16, yy + 178, ox + PW - 16, yy + 218), 22, fill=PRIMARY)
center(ox + 16, ox + PW - 16, yy + 188, "В корзину", font(15, True), ON_PRIMARY)
text((ox + 16, yy + 234), "Отзывы", font(15, True), ON_BG)
rr((ox + 16, yy + 258, ox + PW - 16, yy + 312), 12, fill=SURFACE)
text((ox + 28, yy + 268), "★★★★★", font(12), STAR)
text((ox + 28, yy + 286), "Тёплая, по размеру. Доставка 2 дня.", font(11), (58, 61, 66))
bottomnav(ox, oy, 1)
center(ox, ox + PW, oy + PH + 12, "Карточка товара (FR-B03)", font(13, True), SECONDARY)

# ===================== ЭКРАН 3: КОРЗИНА =====================
ox, oy = phone_base(MARGIN * 3 + PW * 2)
cy = appbar(ox, oy, "Корзина", "Asat Clothes · 2 товара")
items = [("Куртка Aurora", "M · синий", "7 990 ₽", "1", "Куртка"),
         ("Кроссовки Trail X", "42 · чёрный", "5 490 ₽", "2", "Обувь")]
for name, var, price, qty, lbl in items:
    rr((ox + 14, cy, ox + PW - 14, cy + 84), 14, fill=SURFACE)
    product_thumb((ox + 24, cy + 10, ox + 84, cy + 70), lbl, SECONDARY)
    text((ox + 96, cy + 12), name, font(13, True), ON_BG)
    text((ox + 96, cy + 32), var, font(11), SECONDARY)
    text((ox + 96, cy + 50), price, font(13, True), PRIMARY)
    # qty
    qx = ox + PW - 110
    for j, sym in enumerate(["−", qty, "+"]):
        if sym in ("−", "+"):
            d.ellipse((qx, cy + 48, qx + 24, cy + 72), outline=OUTLINE, width=1)
            d.text((qx + 12, cy + 52), sym, font=font(15), fill=PRIMARY, anchor="ma")
        else:
            d.text((qx + 12, cy + 52), sym, font=font(13), fill=ON_BG, anchor="ma")
        qx += 34
    cy += 96
# промокод
rr((ox + 14, cy, ox + PW - 110, cy + 36), 10, outline=OUTLINE, width=1)
text((ox + 26, cy + 9), "SALE10", font(13), ON_BG)
rr((ox + PW - 100, cy, ox + PW - 14, cy + 36), 10, fill=SECONDARY)
center(ox + PW - 100, ox + PW - 14, cy + 9, "Применить", font(12), ON_PRIMARY)
cy += 50
# итог
rr((ox + 14, cy, ox + PW - 14, cy + 130), 14, fill=SURFACE)
rows = [("Товары", "18 970 ₽", ON_BG), ("Промокод SALE10", "−1 897 ₽", ERROR),
        ("Доставка", "0 ₽", ON_BG)]
ry = cy + 12
for lab, val, vc in rows:
    text((ox + 28, ry), lab, font(13), (58, 61, 66))
    text((ox + PW - 28, ry), val, font(13), vc, anchor="ra")
    ry += 24
d.line((ox + 28, ry + 2, ox + PW - 28, ry + 2), fill=(227, 230, 236), width=1)
text((ox + 28, ry + 10), "Итого", font(16, True), ON_BG)
text((ox + PW - 28, ry + 10), "17 073 ₽", font(16, True), ON_BG, anchor="ra")
cy += 146
rr((ox + 14, cy, ox + PW - 14, cy + 42), 22, fill=PRIMARY)
center(ox + 14, ox + PW - 14, cy + 11, "Оформить заказ", font(15, True), ON_PRIMARY)
bottomnav(ox, oy, 2)
center(ox, ox + PW, oy + PH + 12, "Корзина + чекаут (FR-B04/B05)", font(13, True), SECONDARY)

img.save("docs/prototype/storefront.png")
print("saved docs/prototype/storefront.png", img.size)
