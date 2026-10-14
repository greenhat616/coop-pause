"""Generates the Workshop preview and feature cards for Coop Pause.

Scenes are drawn at low resolution and upscaled with nearest-neighbour so they read as pixel art,
matching Necesse's look; text is drawn afterwards at full resolution so it stays crisp.
"""
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(sys.argv[1])
FONTS = Path("C:/Windows/Fonts")

SKY_TOP = (22, 34, 58)
SKY_BOTTOM = (52, 78, 104)
GRASS = (86, 140, 62)
GRASS_DARK = (62, 106, 46)
DIRT = (110, 78, 52)
DIRT_DARK = (84, 58, 40)
CREAM = (246, 232, 196)
OUTLINE = (34, 24, 20)
SHADOW = (14, 20, 32)
ACCENT = (255, 196, 84)
TEXT_DIM = (196, 210, 222)


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scene(w, h, seed=7, ground=0.22, moon=True):
    """Banded night sky, stars and a grass strip, at pixel-art resolution."""
    rng = random.Random(seed)
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    ground_y = int(h * (1 - ground))
    bands = 8
    for y in range(ground_y):
        t = min(bands - 1, int(y / ground_y * bands)) / (bands - 1)
        d.line([(0, y), (w, y)], fill=lerp(SKY_TOP, SKY_BOTTOM, t))
    for _ in range(w * h // 260):
        x, y = rng.randrange(w), rng.randrange(int(ground_y * 0.8))
        img.putpixel((x, y), lerp(SKY_BOTTOM, (255, 255, 240), rng.uniform(0.5, 1)))
    if moon:
        draw_moon(d, w, h)
    # Ground: grass lip with a jagged edge, then dirt with speckles
    d.rectangle([0, ground_y, w, h], fill=DIRT)
    for x in range(w):
        lip = ground_y + rng.choice([0, 0, 1])
        d.line([(x, ground_y - rng.choice([0, 0, 1])), (x, lip + 3)], fill=GRASS)
        d.point((x, lip + 4), fill=GRASS_DARK)
    for _ in range(w * (h - ground_y) // 18):
        d.point((rng.randrange(w), rng.randrange(ground_y + 6, h)), fill=DIRT_DARK)
    return img, ground_y


def draw_moon(d, w, h):
    mx, my, r = int(w * 0.82), int(h * 0.16), max(4, w // 22)
    d.ellipse([mx - r, my - r, mx + r, my + r], fill=(240, 236, 210))
    d.ellipse([mx - r + r // 2, my - r - 1, mx + r + r // 2, my + r - 1], fill=lerp(SKY_TOP, SKY_BOTTOM, 0.15))


def pause_icon(d, cx, cy, bar_w, bar_h, gap, o=2):
    """Two outlined bars with a drop shadow."""
    for dx in (-(gap // 2 + bar_w), gap // 2):
        x0, y0 = cx + dx, cy - bar_h // 2
        d.rectangle([x0 + o + 1, y0 + o + 1, x0 + bar_w + o + 1, y0 + bar_h + o + 1], fill=SHADOW)
        d.rectangle([x0 - o, y0 - o, x0 + bar_w + o - 1, y0 + bar_h + o - 1], fill=OUTLINE)
        d.rectangle([x0, y0, x0 + bar_w - 1, y0 + bar_h - 1], fill=CREAM)
        d.rectangle([x0, y0, x0 + 1, y0 + bar_h - 1], fill=(255, 248, 228))


def player(d, x, y, shirt, s=1):
    """A tiny Necesse-ish settler: head, shirt, legs. (x, y) is the feet."""
    def px(a, b, c, e, f):
        d.rectangle([x + a * s, y + b * s, x + c * s - 1, y + e * s - 1], fill=f)
    px(-3, -14, 3, -8, (232, 186, 150))   # head
    px(-3, -15, 3, -13, (92, 60, 40))     # hair
    px(-1, -11, 0, -10, OUTLINE)          # eye
    px(1, -11, 2, -10, OUTLINE)
    px(-4, -8, 4, -3, shirt)              # body
    px(-3, -3, -1, 0, (60, 60, 90))       # legs
    px(1, -3, 3, 0, (60, 60, 90))


def zzz(d, x, y, s=1):
    for i, size in enumerate((3, 4, 5)):
        ox, oy = x + i * 5 * s, y - i * 6 * s
        z = size * s
        d.line([(ox, oy), (ox + z, oy)], fill=CREAM, width=s)
        d.line([(ox + z, oy), (ox, oy + z)], fill=CREAM, width=s)
        d.line([(ox, oy + z), (ox + z, oy + z)], fill=CREAM, width=s)


def outlined_text(d, xy, text, f, fill, outline=OUTLINE, width=4, anchor="mm"):
    d.text(xy, text, font=f, fill=fill, anchor=anchor, stroke_width=width, stroke_fill=outline)


def preview(path):
    low, ground_y = scene(160, 160, seed=3, ground=0.2, moon=False)
    d = ImageDraw.Draw(low)
    pause_icon(d, 80, 82, 16, 50, 14)
    player(d, 38, ground_y + 2, (196, 72, 64))
    player(d, 122, ground_y + 2, (64, 120, 196))
    zzz(d, 128, ground_y - 22)
    img = low.resize((640, 640), Image.NEAREST)
    d = ImageDraw.Draw(img)
    outlined_text(d, (320, 92), "COOP PAUSE", font("ariblk.ttf", 66), CREAM, width=6)
    img.save(path, optimize=True)


def card(path, title_zh, title_en, lines, draw_art, seed, moon=True):
    w, h = 1280, 720
    low, _ = scene(w // 4, h // 4, seed=seed, ground=0.16, moon=moon)
    img = low.resize((w, h), Image.NEAREST)
    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.rounded_rectangle([60, 70, 760, 610], radius=18, fill=(10, 16, 28, 200), outline=(246, 232, 196, 90), width=2)
    img = Image.alpha_composite(img.convert("RGBA"), overlay)
    d = ImageDraw.Draw(img)
    d.text((100, 110), title_zh, font=font("msyhbd.ttc", 52), fill=CREAM)
    d.text((102, 182), title_en, font=font("segoeuib.ttf", 26), fill=ACCENT)
    y = 250
    for zh, en in lines:
        d.rectangle([102, y + 14, 114, y + 26], fill=ACCENT)
        d.text((134, y - 4), zh, font=font("msyh.ttc", 30), fill=CREAM)
        d.text((134, y + 38), en, font=font("segoeuib.ttf", 20), fill=TEXT_DIM)
        y += 86
    draw_art(img)
    d.text((w - 40, h - 30), "Coop Pause · Necesse", font=font("segoeuib.ttf", 20), fill=TEXT_DIM, anchor="rm")
    img.convert("RGB").save(path, optimize=True)


def art_pause(img):
    low = Image.new("RGBA", (100, 100), (0, 0, 0, 0))
    pause_icon(ImageDraw.Draw(low), 50, 50, 14, 44, 12)
    img.alpha_composite(low.resize((400, 400), Image.NEAREST), (820, 120))


def art_afk(img):
    low = Image.new("RGBA", (100, 100), (0, 0, 0, 0))
    d = ImageDraw.Draw(low)
    player(d, 30, 80, (196, 72, 64))
    player(d, 62, 80, (64, 120, 196))
    zzz(d, 38, 50)
    zzz(d, 70, 50)
    img.alpha_composite(low.resize((400, 400), Image.NEAREST), (820, 286))


def art_menu(img):
    """An abstract menu column, not a screenshot of the game's UI."""
    d = ImageDraw.Draw(img)
    x0, y0, bw, bh = 850, 150, 340, 54
    labels = [("为所有人暂停世界", ACCENT), ("Coop Pause 设置", CREAM)]
    for i in range(3):
        d.rounded_rectangle([x0, y0 + i * 66, x0 + bw, y0 + i * 66 + bh], radius=6, fill=(60, 70, 88), outline=(30, 34, 44), width=3)
    for i, (text, color) in enumerate(labels):
        y = y0 + (i + 3) * 66 + 20
        d.rounded_rectangle([x0, y, x0 + bw, y + bh], radius=6, fill=(84, 96, 64) if i == 0 else (60, 70, 88), outline=OUTLINE, width=3)
        d.text((x0 + bw / 2, y + bh / 2), text, font=font("msyhbd.ttc", 24), fill=color, anchor="mm")
    d.text((x0 + bw / 2, y0 + 6 * 66 - 24), "ADMIN", font=font("ariblk.ttf", 22), fill=ACCENT, anchor="mm")


def main():
    res = ROOT / "src/main/resources"
    out = ROOT / "docs/workshop/images"
    out.mkdir(parents=True, exist_ok=True)
    preview(res / "preview.png")
    card(out / "01-pause.png", "一键冻结整个世界", "Freeze the whole world, everyone stays connected", [
        ("/pause 暂停，/unpause 恢复", "Pause with /pause, resume with /unpause"),
        ("怪物、NPC、作物、时间全部停止", "Mobs, settlers, crops and time all stop"),
        ("暂停期间无法攻击、放置、开箱", "No attacking, building or looting while paused"),
        ("网络保持连接，不会掉线", "Connections and pings keep running"),
    ], art_pause, seed=11)
    card(out / "02-afk.png", "全员挂机自动暂停", "Auto-pause when everyone is AFK", [
        ("所有玩家闲置超过设定分钟数即暂停", "Pauses after all players idle for N minutes"),
        ("任意一人按键立即恢复", "Any player pressing a key resumes"),
        ("/afkpause [分钟] 实时调整，0 为关闭", "/afkpause [minutes] applies live; 0 = off"),
        ("房主挂机、朋友也挂机，世界不再白跑", "No more AFK raids, starving settlers or lost days"),
    ], art_afk, seed=23)
    card(out / "03-menu.png", "暂停菜单一键操作", "Controls right in the pause menu", [
        ("Esc 菜单里直接暂停 / 恢复世界", "Pause or resume from the Esc menu"),
        ("游戏内实时调整挂机分钟数", "Change the AFK threshold live in-game"),
        ("仅管理员可用，服务端二次校验", "Admin only, checked again by the server"),
        ("主菜单「Mod 设置」可改默认值", "Defaults in Settings > Mod settings"),
    ], art_menu, seed=31, moon=False)
    for p in [res / "preview.png", *sorted(out.glob("*.png"))]:
        print(p, Image.open(p).size, p.stat().st_size // 1024, "KB")


if __name__ == "__main__":
    main()
