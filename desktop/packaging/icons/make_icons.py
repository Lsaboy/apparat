#!/usr/bin/env python3
"""Builds the desktop icons from the Android launcher vectors (app/src/main/res/drawable).

Output (next to this script): apparat.svg / apparat-charcoal.svg, PNGs at common sizes,
apparat.ico (Windows) and apparat.png (Linux, 512 px). Needs Node with Playwright (to rasterise
the SVG in Chromium) and Pillow.
Run: python3 desktop/packaging/icons/make_icons.py
"""
import os
import subprocess
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "..", "app", "src", "main", "res", "drawable")
A = "{http://schemas.android.com/apk/res/android}"


def color(c):
    """#AARRGGBB / #RRGGBB → (svg colour, opacity)."""
    c = c.strip()
    if len(c) == 9:
        return "#" + c[3:], int(c[1:3], 16) / 255
    return c, 1.0


def paths(xml_file):
    root = ET.parse(xml_file).getroot()
    out = []
    for p in root.iter("path"):
        attrs = {"d": p.get(A + "pathData")}
        fill = p.get(A + "fillColor")
        if fill:
            col, op = color(fill)
            attrs["fill"] = col
            if op < 1:
                attrs["fill-opacity"] = f"{op:.3f}"
        else:
            attrs["fill"] = "none"
        stroke = p.get(A + "strokeColor")
        if stroke:
            col, op = color(stroke)
            attrs["stroke"] = col
            attrs["stroke-width"] = p.get(A + "strokeWidth", "1")
            cap = p.get(A + "strokeLineCap")
            if cap:
                attrs["stroke-linecap"] = cap
        out.append("<path " + " ".join(f'{k}="{v}"' for k, v in attrs.items()) + "/>")
    return out


def svg(bg_file, fg_file):
    bg = paths(os.path.join(RES, bg_file))
    fg = paths(os.path.join(RES, fg_file))
    # Adaptive icons show the centre 72 of 108 units; use a slightly wider crop with a rounded tile.
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="14 14 80 80" width="512" height="512">'
        '<defs><clipPath id="r"><rect x="14" y="14" width="80" height="80" rx="18" ry="18"/></clipPath></defs>'
        '<g clip-path="url(#r)">' + "".join(bg) + "".join(fg) + "</g></svg>"
    )


RENDER_JS = """
const { chromium } = require('playwright');
(async () => {
  const [svgPath, pngPath] = process.argv.slice(2);
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1024, height: 1024 } });
  const svg = require('fs').readFileSync(svgPath, 'utf8').replace('width="512" height="512"', 'width="1024" height="1024"');
  await page.setContent('<html><body style="margin:0;background:transparent">' + svg + '</body></html>');
  await page.locator('svg').screenshot({ path: pngPath, omitBackground: true });
  await browser.close();
})();
"""


def render_master(svg_path, png_path):
    js = os.path.join(HERE, ".render.js")
    with open(js, "w") as f:
        f.write(RENDER_JS)
    try:
        subprocess.run(["node", js, svg_path, png_path], check=True)
    finally:
        os.remove(js)


from PIL import Image

for name, bg, fg in [
    ("apparat", "ic_launcher_background.xml", "ic_launcher_foreground.xml"),
    ("apparat-charcoal", "ic_launcher_charcoal_background.xml", "ic_launcher_charcoal_foreground.xml"),
]:
    svg_path = os.path.join(HERE, f"{name}.svg")
    with open(svg_path, "w") as f:
        f.write(svg(bg, fg))
    master = os.path.join(HERE, f".{name}-master.png")
    render_master(svg_path, master)
    img = Image.open(master).convert("RGBA")
    img.resize((512, 512), Image.LANCZOS).save(os.path.join(HERE, f"{name}.png"))
    for s in (32, 64, 128, 256):
        img.resize((s, s), Image.LANCZOS).save(os.path.join(HERE, f"{name}-{s}.png"))
    img.resize((256, 256), Image.LANCZOS).save(
        os.path.join(HERE, f"{name}.ico"), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    )
    os.remove(master)
print("icons written to", HERE)
