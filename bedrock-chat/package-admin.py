"""Embed the compiled, prebound UltimateGUI without committing binary artifacts."""
from pathlib import Path
import hashlib
import sys
import zipfile

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
