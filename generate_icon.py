#!/usr/bin/env python3
"""Generate Pandorical's mod menu icon: a Minecraft screen, which is what
the library exists to let a server draw on your client.

Pure stdlib PNG reader and writer (zlib + struct) so it runs without Pillow,
the same script generated art approach as the rest of the suite. Deterministic:
re-running produces identical bytes. Source pixels are read straight out of the
vanilla Minecraft jar and scaled nearest neighbour, never smoothed.

Usage: python3 generate_icon.py [path/to/minecraft.jar]
"""

import glob
import os
import struct
import sys
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/main/resources/assets/pandorical/icon.png")

CLEAR = (0, 0, 0, 0)
_JAR = None


def minecraft_version():
    """The version this mod targets, so the icon is cut from the same jar the
    mod is built against rather than whatever happens to be cached."""
    path = os.path.join(HERE, "gradle.properties")
    if not os.path.exists(path):
        return None
    for line in open(path):
        key, sep, value = line.partition("=")
        if sep and key.strip() == "minecraft_version":
            return value.strip()
    return None


def find_jar():
    """Loom caches the remapped Minecraft jars after a build; that is where the
    vanilla art comes from. Override with an argument or $MINECRAFT_JAR."""
    global _JAR
    if _JAR:
        return _JAR
    if len(sys.argv) > 1:
        _JAR = sys.argv[1]
        return _JAR
    if os.environ.get("MINECRAFT_JAR"):
        _JAR = os.environ["MINECRAFT_JAR"]
        return _JAR
    cache = os.path.expanduser("~/.gradle/caches/fabric-loom")
    names = ("minecraft-merged.jar", "minecraft-client.jar")
    found = []
    version = minecraft_version()
    if version:
        for name in names:
            found += glob.glob(os.path.join(cache, version, name))
    if not found:
        for name in names:
            found += glob.glob(os.path.join(cache, "*", name))
    if not found:
        sys.exit("no cached Minecraft jar found: build the mod once, "
                 "or pass a jar path as the first argument")
    _JAR = max(found, key=os.path.getmtime)
    return _JAR


def vanilla(name):
    """Read assets/minecraft/textures/<name> out of the vanilla jar."""
    with zipfile.ZipFile(find_jar()) as jar:
        return decode_png(jar.read("assets/minecraft/textures/" + name))


def decode_png(data):
    """Minimal PNG reader: no interlacing, every colour type and bit depth
    vanilla actually ships. Returns rows of RGBA tuples."""
    pos = 8
    idat = b""
    width = height = depth = ctype = None
    palette = trns = None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert interlace == 0, "interlaced PNG not supported"
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride = (width * channels * depth + 7) // 8
    step = max(1, (channels * depth) // 8)
    raw = zlib.decompress(idat)
    out = bytearray(stride * height)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        filt = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if filt == 1:
            for i in range(step, stride):
                line[i] = (line[i] + line[i - step]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                b = prev[i]
                c = prev[i - step] if i >= step else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line

    pixels = []
    if depth < 8:
        per = 8 // depth
        mask = (1 << depth) - 1
        for y in range(height):
            base = y * stride
            row = []
            for x in range(width):
                i = x * channels
                value = (out[base + i // per] >> (8 - depth * (i % per + 1))) & mask
                if ctype == 3:
                    r, g, b = palette[value * 3:value * 3 + 3]
                    a = trns[value] if trns and value < len(trns) else 255
                    row.append((r, g, b, a))
                else:
                    v = value * 255 // mask
                    row.append((v, v, v, 255))
            pixels.append(row)
        return pixels

    for y in range(height):
        base = y * stride
        row = []
        for x in range(width):
            i = base + x * channels
            if ctype == 6:
                row.append(tuple(out[i:i + 4]))
            elif ctype == 2:
                row.append((out[i], out[i + 1], out[i + 2], 255))
            elif ctype == 4:
                row.append((out[i], out[i], out[i], out[i + 1]))
            elif ctype == 0:
                row.append((out[i], out[i], out[i], 255))
            else:
                r, g, b = palette[out[i] * 3:out[i] * 3 + 3]
                a = trns[out[i]] if trns and out[i] < len(trns) else 255
                row.append((r, g, b, a))
        pixels.append(row)
    return pixels


def write_png(path, pixels):
    """pixels: rows of RGBA tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))


def scale(pixels, n):
    """Nearest neighbour only: these are pixel textures, never smooth them."""
    return [[px for px in row for _ in range(n)] for row in pixels for _ in range(n)]

def blank(width, height):
    return [[CLEAR] * width for _ in range(height)]

def rect(pixels, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            pixels[y][x] = colour


# Vanilla widget palette, so the icon reads as a Minecraft screen rather than
# as generic UI chrome.
BLACK = (0x00, 0x00, 0x00, 0xFF)
PANEL = (0xC6, 0xC6, 0xC6, 0xFF)
LIGHT = (0xFF, 0xFF, 0xFF, 0xFF)
SHADE = (0x55, 0x55, 0x55, 0xFF)
SLOT = (0x8B, 0x8B, 0x8B, 0xFF)
SLOT_DARK = (0x37, 0x37, 0x37, 0xFF)
BUTTON = (0x6A, 0x6A, 0x6A, 0xFF)
BUTTON_LIGHT = (0xA8, 0xA8, 0xA8, 0xFF)
BUTTON_DARK = (0x2E, 0x2E, 0x2E, 0xFF)
ACCENT = (0x8C, 0x5A, 0xC6, 0xFF)
ACCENT_DARK = (0x5E, 0x3B, 0x86, 0xFF)
CONTENT = (0x17, 0xC8, 0x56, 0xFF)
CONTENT_LIGHT = (0x6B, 0xEE, 0x9A, 0xFF)

SLOT_COLUMNS = (4, 13, 22)
SLOT_ROW = 14


def build_icon():
    """Pandorical ships no content textures of its own: it draws other mods'
    UI on the client. So the icon is a screen. Title bar, a row of slots with
    one server pushed item sitting in the middle one, and a button. Composed
    at 32x32 and scaled by four, which keeps every feature at least two pixels
    wide once the mod menu draws it small."""
    canvas = blank(32, 32)
    x0, y0, x1, y1 = 1, 2, 30, 29

    rect(canvas, x0, y0, x1, y1, BLACK)
    rect(canvas, x0 + 1, y0 + 1, x1 - 1, y1 - 1, PANEL)
    for x in range(x0 + 1, x1):
        canvas[y0 + 1][x] = LIGHT
        canvas[y1 - 1][x] = SHADE
    for y in range(y0 + 1, y1):
        canvas[y][x0 + 1] = LIGHT
        canvas[y][x1 - 1] = SHADE

    rect(canvas, x0 + 2, y0 + 2, x1 - 2, y0 + 7, ACCENT)
    rect(canvas, x0 + 2, y0 + 7, x1 - 2, y0 + 7, ACCENT_DARK)
    rect(canvas, x0 + 4, y0 + 4, x0 + 13, y0 + 5, LIGHT)   # title line

    for index, sx in enumerate(SLOT_COLUMNS):
        sy = SLOT_ROW
        rect(canvas, sx, sy, sx + 6, sy + 6, SLOT)
        for k in range(7):
            canvas[sy][sx + k] = SLOT_DARK
            canvas[sy + k][sx] = SLOT_DARK
            canvas[sy + 6][sx + k] = LIGHT
            canvas[sy + k][sx + 6] = LIGHT
        canvas[sy + 6][sx] = SLOT_DARK
        if index == 1:
            rect(canvas, sx + 1, sy + 1, sx + 5, sy + 5, CONTENT)
            rect(canvas, sx + 2, sy + 2, sx + 3, sy + 3, CONTENT_LIGHT)

    bx0, by0, bx1, by1 = 8, 23, 23, 26
    rect(canvas, bx0, by0, bx1, by1, BUTTON)
    for x in range(bx0, bx1 + 1):
        canvas[by0][x] = BUTTON_LIGHT
        canvas[by1][x] = BUTTON_DARK
    for y in range(by0, by1 + 1):
        canvas[y][bx0] = BUTTON_LIGHT
        canvas[y][bx1] = BUTTON_DARK
    rect(canvas, bx0 + 4, by0 + 2, bx1 - 4, by0 + 2, LIGHT)   # button label
    return scale(canvas, 4)


if __name__ == "__main__":
    icon = build_icon()
    assert len(icon) == 128 and len(icon[0]) == 128, "mod menu icons are 128x128"
    write_png(OUT, icon)
