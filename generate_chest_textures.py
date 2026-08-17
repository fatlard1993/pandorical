#!/usr/bin/env python3
"""Generate the suite's chest textures: vanilla's chest with a recoloured clasp.

The clasp is already the right shape for it. It is a small nub protruding from
the middle of the chest front, which is exactly what a villager's nose is, so
nothing needs remodelling: recolouring the metal to skin is the whole trick.
Vanilla's shading is preserved by mapping each grey it uses onto a skin tone of
matching lightness, which keeps the nub reading as a lit 3D shape rather than a
flat patch of colour.

Reads the three vanilla chest textures out of the Minecraft jar and writes the
recoloured copies. Pure stdlib (zlib + struct), no Pillow, deterministic:
re-running produces identical bytes. Same house pattern as the suite's other
icon generators.

Usage: python3 generate_village_chest.py
"""

import os
import struct
import sys
import zlib
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
JAR = os.path.expanduser(
    "~/.gradle/caches/fabric-loom/26.3-snapshot-8/minecraft-client-only.jar")
OUT_DIR = os.path.join(HERE, "src/client/resources/assets/pandorical/textures/entity/chest")

# normal.png is the single chest; the other two are the halves of a double chest.
SOURCES = ["normal.png", "normal_left.png", "normal_right.png"]

# The clasp occupies a 6x5 corner at the texture origin: a 2x4x1 box unwrapped at
# UV (0,0), of which x=1..2, y=1..4 is the face you actually see.
CLASP = (0, 0, 6, 5)

# Every grey vanilla uses in the clasp, mapped per variant to a tone of matching
# lightness. Preserving the lightness order is what keeps the nub reading as a
# lit 3D shape instead of a flat patch of colour.
#
#   village: the villager's own skin, sampled from their face, so the nose
#            belongs to the person the player has been trading with.
#   looted:  the same clasp with the light gone out of it. Darker and browner
#            than vanilla at every step, so a chest you have emptied reads as
#            spent from across a room without looking like a different block.
VARIANTS = {
    "village": {
        (118, 118, 118): (107, 78, 53),
        (134, 134, 134): (125, 92, 64),
        (145, 145, 145): (138, 102, 71),
        (165, 165, 165): (160, 122, 85),
        (194, 194, 194): (188, 144, 104),
        (205, 205, 205): (198, 154, 112),
    },
    "looted": {
        (118, 118, 118): (38, 34, 30),
        (134, 134, 134): (46, 41, 36),
        (145, 145, 145): (54, 48, 42),
        (165, 165, 165): (66, 59, 51),
        (194, 194, 194): (84, 75, 65),
        (205, 205, 205): (92, 82, 71),
    },
}


def read_png(data):
    """Decode a PNG into (width, height, rows of RGBA tuples)."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")

    idat, palette, trns, width, height, depth, colour = b"", None, None, 0, 0, 0, 0
    pos = 8
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", body[:10])
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break
        pos += 12 + length

    if depth != 8:
        raise ValueError("only 8-bit PNGs are handled; got %d" % depth)

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    stride = width * channels
    raw = zlib.decompress(idat)

    # Undo the per-scanline filters. This is the whole of the PNG spec that
    # matters here, and it is short enough to not be worth a dependency.
    out, prev = [], bytearray(stride)
    pos = 0
    for _ in range(height):
        filt = raw[pos]
        line = bytearray(raw[pos + 1:pos + 1 + stride])
        pos += 1 + stride
        for i in range(stride):
            a = line[i - channels] if i >= channels else 0
            b = prev[i]
            c = prev[i - channels] if i >= channels else 0
            if filt == 1:
                line[i] = (line[i] + a) & 0xFF
            elif filt == 2:
                line[i] = (line[i] + b) & 0xFF
            elif filt == 3:
                line[i] = (line[i] + (a + b) // 2) & 0xFF
            elif filt == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pred) & 0xFF
        out.append(line)
        prev = line

    rows = []
    for line in out:
        row = []
        for x in range(width):
            if colour == 3:
                idx = line[x]
                r, g, b = palette[idx * 3:idx * 3 + 3]
                alpha = trns[idx] if trns and idx < len(trns) else 255
                row.append((r, g, b, alpha))
            elif colour == 6:
                row.append(tuple(line[x * 4:x * 4 + 4]))
            elif colour == 2:
                row.append(tuple(line[x * 3:x * 3 + 3]) + (255,))
            else:
                raise ValueError("unhandled colour type %d" % colour)
        rows.append(row)
    return width, height, rows


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in rows)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))


def recolour(rows, palette):
    x0, y0, x1, y1 = CLASP
    missed = set()
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b, a = rows[y][x]
            if a == 0:
                continue
            if (r, g, b) in palette:
                rows[y][x] = palette[(r, g, b)] + (a,)
            else:
                missed.add((r, g, b))
    if missed:
        # A grey nobody mapped means the vanilla clasp was retextured upstream and
        # this would silently ship a half-painted nose.
        raise SystemExit("unmapped clasp colours, update VARIANTS: %s" % sorted(missed))
    return rows


def main():
    if not os.path.exists(JAR):
        raise SystemExit("client jar not found: %s" % JAR)

    with zipfile.ZipFile(JAR) as jar:
        for source in SOURCES:
            data = jar.read("assets/minecraft/textures/entity/chest/" + source)
            for variant, palette in VARIANTS.items():
                _w, _h, rows = read_png(data)
                target = source.replace("normal", variant)
                write_png(os.path.join(OUT_DIR, target), recolour(rows, palette))


if __name__ == "__main__":
    sys.exit(main())
