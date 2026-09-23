#!/usr/bin/env python3
"""Rebuild the bundled Material Symbols Rounded icon font.

Google publishes Material Symbols on npm (`material-symbols`) as a variable
WOFF2. Android cannot load WOFF2, and the full 6.6k-glyph font is 15 MB, so this
script

  1. downloads the npm tarball,
  2. decompresses WOFF2 -> TTF (fontTools + brotli),
  3. pins the variable axes to FILL=0 GRAD=0 opsz=24 wght=400,
  4. keeps only the glyphs the app actually uses, and
  5. prints the Kotlin codepoint table.

Result: app/src/main/assets/fonts/MaterialSymbolsRounded.ttf (~16 kB).
Requires: pip install fonttools brotli
"""
import io
import json
import os
import tarfile
import urllib.request

from fontTools import subset as ftsubset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/assets/fonts/MaterialSymbolsRounded.ttf")
VERSION = os.environ.get("MS_VERSION", "0.47.5")

ICONS = [
    "history", "history_2", "straighten", "calculate", "settings", "info", "backspace",
    "currency_exchange", "weight", "storage", "device_thermostat", "deployed_code",
    "arrow_back", "more_vert", "favorite", "share", "equal", "close", "delete", "search",
    "content_copy", "check", "swap_vert", "refresh", "download", "palette", "dark_mode",
    "light_mode", "brightness_auto", "code", "open_in_new", "add", "remove", "functions",
    "percent", "numbers", "update", "check_circle", "error", "download_done", "downloading",
    "cloud_off", "link", "tune", "grid_view", "chevron_right", "cancel", "schedule", "star",
    "handshake", "volunteer_activism", "android", "description", "groups", "sort", "list",
    "memory", "thermostat", "square_foot", "monitor_weight", "keyboard_arrow_down",
    "keyboard_arrow_up", "unfold_more", "drag_handle", "bolt", "science", "pin", "wifi_off",
    "folder_open", "translate", "language",
]


def main() -> None:
    url = f"https://registry.npmjs.org/material-symbols/-/material-symbols-{VERSION}.tgz"
    print(f"fetching {url}")
    blob = urllib.request.urlopen(url).read()
    woff = None
    with tarfile.open(fileobj=io.BytesIO(blob)) as tf:
        for m in tf.getmembers():
            if m.name.endswith("material-symbols-rounded.woff2"):
                woff = tf.extractfile(m).read()
    assert woff, "rounded woff2 not found in package"

    font = TTFont(io.BytesIO(woff))
    cmap = font.getBestCmap()
    inv = {}
    for cp, name in cmap.items():
        inv.setdefault(name, cp)

    missing = [n for n in ICONS if n not in inv]
    if missing:
        raise SystemExit(f"icons not present in font {VERSION}: {missing}")

    font = instancer.instantiateVariableFont(
        font, {"FILL": 0, "GRAD": 0, "opsz": 24, "wght": 400}, inplace=False
    )
    opts = ftsubset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = [1, 2, 3, 4, 6]
    opts.notdef_outline = True
    sub = ftsubset.Subsetter(options=opts)
    sub.populate(unicodes=[inv[n] for n in ICONS])
    sub.subset(font)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    font.save(OUT)
    print(f"wrote {OUT} ({os.path.getsize(OUT)} bytes, {font['maxp'].numGlyphs} glyphs)")
    print("\nKotlin codepoints:")
    for n in sorted(ICONS):
        print(f'    val {n.upper()} = "\\u{inv[n]:04x}"')
    json.dump({n: hex(inv[n]) for n in ICONS}, open("/tmp/m3e-icons.json", "w"), indent=1)


if __name__ == "__main__":
    main()
