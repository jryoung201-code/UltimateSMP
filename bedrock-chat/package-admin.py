"""Embed the compiled, prebound UltimateGUI without committing binary artifacts."""
from pathlib import Path
import hashlib
import sys
import zipfile
import urllib.request

source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "ultimate-gui/target/UltimateGUI-1.0.1.jar"
payload = source.read_bytes()
with zipfile.ZipFile(source) as jar:
    assert "version: 1.0.1" in jar.read("plugin.yml").decode()
    assert b"bd33980d-5b8d-4f1c-becc-0520f92c4a0d" in jar.read("shop/blinksmp/gui/GuiRules.class")
    assert jar.testzip() is None
target = Path(__file__).resolve().parent / "build/generated-resources/bundled"
target.mkdir(parents=True, exist_ok=True)
(target / "UltimateGUI.jar").write_bytes(payload)
(target / "admin.properties").write_text("version=1.0.1\nsha256=" + hashlib.sha256(payload).hexdigest() + "\n")
print("Embedded prebound UltimateGUI 1.0.1 with SHA-256 manifest")

# Build-time download only. The installed plugin copies these verified bytes offline.
packets = Path(__file__).resolve().parent / "build-deps/packetevents-spigot-2.14.0.jar"
expected = "060087c58ec268eae7dd0eb1f17ac3bea1eaadda1693414141ff64e506091bd7"
if not packets.exists():
    packets.parent.mkdir(parents=True, exist_ok=True)
    url = "https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar"
    with urllib.request.urlopen(url, timeout=90) as response:
        packets.write_bytes(response.read())
packet_payload = packets.read_bytes()
assert hashlib.sha256(packet_payload).hexdigest() == expected, "Official PacketEvents checksum mismatch"
with zipfile.ZipFile(packets) as jar:
    assert "version: 2.14.0" in jar.read("plugin.yml").decode()
    assert jar.testzip() is None
(target / "PacketEvents.jar").write_bytes(packet_payload)
(target / "packetevents.properties").write_text("version=2.14.0\nsha256=" + expected + "\n")
(target / "THIRD-PARTY.txt").write_text(
    "PacketEvents 2.14.0, retrooper and contributors. Official binary, unmodified.\n"
    "Source and licensing: https://github.com/retrooper/packetevents/tree/v2.14.0\n"
    "Release: https://github.com/retrooper/packetevents/releases/tag/v2.14.0\n"
)
print("Embedded official PacketEvents 2.14.0 with verified release checksum")
