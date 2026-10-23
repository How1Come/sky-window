import json
import struct
import zlib
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


size = 32
rows = bytearray()
for y in range(size):
    rows.append(0)
    for x in range(size):
        cross = 13 <= x <= 18 or 13 <= y <= 18
        rows.extend((0, 255, 255, 255) if cross else (255, 0, 255, 255))

png = b"\x89PNG\r\n\x1a\n"
png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(bytes(rows)))
png += chunk(b"IEND", b"")
root = Path(__file__).resolve().parent.parent
instance = root / "run" / "client-smoke"
packs = instance / "resourcepacks"
packs.mkdir(parents=True, exist_ok=True)
with ZipFile(packs / "sky-window-test.zip", "w", ZIP_DEFLATED) as archive:
    archive.writestr("pack.mcmeta", json.dumps({"pack": {"pack_format": 15, "description": "Sky Window resource integration test"}}))
    archive.writestr("assets/minecraft/textures/environment/sun.png", png)

options = instance / "options.txt"
lines = options.read_text().splitlines() if options.exists() else []
lines = [line for line in lines if not line.startswith("resourcePacks:")]
lines.append('resourcePacks:["vanilla","fabric","file/sky-window-test.zip"]')
options.write_text("\n".join(lines) + "\n")
print(packs / "sky-window-test.zip")
