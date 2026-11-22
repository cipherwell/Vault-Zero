#!/usr/bin/env python3
"""Render the bilingual VaultZero guides to PDF (A4) via Chromium."""
import subprocess
from pathlib import Path

import markdown

HERE = Path(__file__).resolve().parent
ASSETS = HERE / "assets"

CSS = """
@page { size: A4; margin: 18mm 16mm 16mm 16mm; }
* { box-sizing: border-box; }
body {
  font-family: "Inter", "Noto Sans", "DejaVu Sans", sans-serif;
  font-size: 10.6pt; line-height: 1.52; color: #1b2430; margin: 0;
}
.cover {
  height: 250mm; display: flex; flex-direction: column; justify-content: center;
  align-items: center; text-align: center; color: #fff; border-radius: 10mm;
  background: linear-gradient(145deg, #2F6BFF 0%, #1B46C8 55%, #0A2366 100%);
  padding: 26mm 18mm; page-break-after: always;
}
.cover img { width: 46mm; border-radius: 12mm; box-shadow: 0 10mm 24mm rgba(0,0,0,.35); }
.cover h1 { font-size: 32pt; margin: 12mm 0 2mm; letter-spacing: -0.5pt; color: #ffffff; }
.cover .sub { font-size: 13pt; opacity: .92; max-width: 130mm; }
.cover .meta { margin-top: 16mm; font-size: 10pt; opacity: .85; }
h1, h2, h3 { color: #12306e; line-height: 1.2; page-break-after: avoid; }
h1 { font-size: 21pt; margin: 0 0 4mm; border-bottom: 2.5pt solid #2F6BFF; padding-bottom: 3mm; }
h2 { font-size: 14.5pt; margin: 8mm 0 3mm; }
h3 { font-size: 12pt; margin: 6mm 0 2mm; color: #1b4aa8; }
p { margin: 0 0 3.2mm; orphans: 2; widows: 2; }
ul, ol { margin: 0 0 3.5mm 5mm; padding-left: 4mm; }
li { margin-bottom: 1.4mm; orphans: 2; widows: 2; }
blockquote {
  margin: 4mm 0; padding: 3.5mm 5mm; border-left: 3.5pt solid #2F6BFF;
  background: #eef4ff; border-radius: 0 3mm 3mm 0; color: #16325f;
}
table { width: 100%; border-collapse: collapse; margin: 3mm 0 5mm; font-size: 9.4pt; page-break-inside: avoid; }
th { background: #e8f0ff; color: #12306e; text-align: left; padding: 2.2mm 2.6mm; border: .3pt solid #c9d9f7; }
td { padding: 2mm 2.6mm; border: .3pt solid #d8e2f3; vertical-align: top; }
tr:nth-child(even) td { background: #f7faff; }
img { display: block; max-width: 72%; max-height: 148mm; margin: 4mm auto 2mm; border-radius: 4mm; box-shadow: 0 2mm 6mm rgba(15,35,80,.18); page-break-inside: avoid; }
code { font-family: "JetBrains Mono", "DejaVu Sans Mono", monospace; background: #eef2f8; padding: .4mm 1.2mm; border-radius: 1.2mm; font-size: 9.2pt; }
pre { background: #0f1a2e; color: #dce8ff; padding: 3.5mm 4mm; border-radius: 3mm; font-size: 9pt; overflow: hidden; }
pre code { background: none; color: inherit; padding: 0; }
hr { border: none; border-top: .4pt solid #dbe4f2; margin: 6mm 0; }
.small { font-size: 8.6pt; color: #5b6b82; }
"""

def render(md_path: Path, out_pdf: Path, lang: str):
    text = md_path.read_text(encoding="utf-8")
    html_body = markdown.markdown(
        text,
        extensions=["tables", "fenced_code", "sane_lists", "nl2br"],
    )
    cover = f"""
    <section class="cover">
      <img src="assets/icon.png" alt="VaultZero icon">
      <h1>VaultZero</h1>
      <div class="sub">{'Your private vault — beta user guide' if lang == 'en' else 'Tu bóveda privada — guía del usuario beta'}</div>
      <div class="meta">v1.7.17 · November 2026 · Android 7.0+<br>
      {'No account · No cloud · No internet permission' if lang == 'en' else 'Sin cuenta · Sin nube · Sin permiso de internet'}</div>
    </section>
    """
    doc = f"""<!doctype html>
<html lang="{lang}"><head><meta charset="utf-8">
<title>VaultZero — {'User Guide' if lang == 'en' else 'Guía del usuario'}</title>
<style>{CSS}</style></head>
<body>{cover}{html_body}</body></html>"""
    html_path = out_pdf.with_suffix(".html")
    html_path.write_text(doc, encoding="utf-8")
    subprocess.run([
        "chromium", "--headless", "--disable-gpu", "--no-sandbox",
        "--no-pdf-header-footer", f"--print-to-pdf={out_pdf}",
        f"file://{html_path}",
    ], check=True, capture_output=True)
    print(f"wrote {out_pdf.name} ({out_pdf.stat().st_size} bytes)")

if __name__ == "__main__":
    render(HERE / "VaultZero-Guide-EN.md", HERE / "VaultZero-Guide-EN.pdf", "en")
    render(HERE / "VaultZero-Guia-ES.md", HERE / "VaultZero-Guia-ES.pdf", "es")
