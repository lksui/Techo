"""
生成 Techo 的主屏图标。

用代码画而不是手工做图，是为了以后想调配色 / 换图形时能一条命令重生成。

用法（在项目根目录）：
    python tools/make_icons.py

输出到 src/main/resources/static/：
    icon-192.png            圆角方块 + 勾，Chrome / Android 的普通图标
    icon-512.png            同上，大尺寸
    icon-maskable-512.png   满幅背景 + 缩小的勾，供 Android 自适应图标裁切
    apple-touch-icon.png    满幅背景，iOS 会自己加圆角
    favicon.png             浏览器标签页
"""

import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("需要 Pillow：pip install Pillow")

# 相对于本脚本定位输出目录，换机器也不用改
OUT_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "static"

# 和 app.css 里的 --accent 保持一致，改配色时两处一起改
ACCENT = (74, 111, 165, 255)      # #4a6fa5
WHITE = (255, 255, 255, 255)

# 先在 4 倍尺寸上画再缩小，得到平滑边缘
SUPERSAMPLE = 4

# 勾的折线：相对整个画布的坐标（0~1）
CHECK_POINTS = [(0.28, 0.53), (0.44, 0.69), (0.73, 0.33)]
CHECK_WIDTH_RATIO = 0.095          # 线宽相对画布


def scaled_points(scale: float):
    """把勾按画布中心缩放。maskable 图标需要把内容收进安全区。"""
    return [
        (0.5 + (x - 0.5) * scale, 0.5 + (y - 0.5) * scale)
        for x, y in CHECK_POINTS
    ]


def draw_icon(size: int, *, rounded: bool, full_bleed: bool, check_scale: float) -> Image.Image:
    s = size * SUPERSAMPLE
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if full_bleed:
        # 满幅铺色：iOS 和 Android 的自适应图标都会自己裁形状
        d.rectangle([0, 0, s, s], fill=ACCENT)
    elif rounded:
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=ACCENT)
    else:
        d.rectangle([0, 0, s, s], fill=ACCENT)

    pts = [(x * s, y * s) for x, y in scaled_points(check_scale)]
    width = max(1, int(s * CHECK_WIDTH_RATIO * check_scale))

    # joint="curve" 让折角圆滑，两端再补圆头
    d.line(pts, fill=WHITE, width=width, joint="curve")
    r = width / 2
    for x, y in (pts[0], pts[-1]):
        d.ellipse([x - r, y - r, x + r, y + r], fill=WHITE)

    return img.resize((size, size), Image.Resampling.LANCZOS)


def save(img: Image.Image, name: str):
    path = OUT_DIR / name
    img.save(path, "PNG", optimize=True)
    print(f"  {name:<26} {img.size[0]}x{img.size[1]}   {path.stat().st_size:,} 字节")


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"输出到 {OUT_DIR}")
    print("生成图标：")

    # 普通图标：圆角 + 正常大小的勾
    save(draw_icon(192, rounded=True, full_bleed=False, check_scale=1.0), "icon-192.png")
    save(draw_icon(512, rounded=True, full_bleed=False, check_scale=1.0), "icon-512.png")

    # maskable：满幅背景，勾收进安全区。
    # 安全区是以中心为圆心、半径 40% 的圆。check_scale=0.75 时勾的最远点
    # 距中心约 26%，留有余量但又不会小到看不清。
    save(draw_icon(512, rounded=False, full_bleed=True, check_scale=0.75),
         "icon-maskable-512.png")

    # iOS：满幅，系统自己加圆角，可以比 maskable 更满
    save(draw_icon(180, rounded=False, full_bleed=True, check_scale=0.85),
         "apple-touch-icon.png")

    # 浏览器标签页
    save(draw_icon(64, rounded=True, full_bleed=False, check_scale=1.0), "favicon.png")


if __name__ == "__main__":
    main()
