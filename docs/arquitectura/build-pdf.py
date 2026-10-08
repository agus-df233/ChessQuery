"""Genera docs/arquitectura/ChessQuery-arquitectura-v3.pdf desde arquitectura-v3.md y los diagramas SVG.

El Markdown y ``diagramas.py`` son la fuente única; el PDF es solo una vista para compartir o imprimir.
Uso: ``make arquitectura-docs`` (necesita Google Chrome instalado para imprimir a PDF).
"""
from __future__ import annotations

import datetime as dt
import pathlib
import subprocess
import sys

import markdown

import diagramas

HERE = pathlib.Path(__file__).resolve().parent
SOURCE = HERE / "arquitectura-v3.md"
OUT_HTML = HERE / ".arquitectura.html"
OUT_PDF = HERE / "ChessQuery-arquitectura-v3.pdf"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

CSS = """
@page { size: A4 landscape; margin: 12mm 14mm 14mm; }
html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body { font: 10pt/1.5 -apple-system, "Helvetica Neue", Arial, sans-serif; color: #1d1f1a; }
h1 { font-size: 20pt; border-bottom: 2px solid #2e7d32; padding-bottom: 6px; margin-top: 0; }
h2 { font-size: 14pt; margin: 0 0 6px; color: #1b5e20; break-before: page; }
code { font: 8.6pt "SF Mono", Menlo, monospace; background: #f3f4ee; padding: 1px 4px; border-radius: 3px; }
pre { background: #f3f4ee; border: 1px solid #d9dbd0; border-radius: 6px; padding: 8px 10px; white-space: pre-wrap; }
pre code { background: none; padding: 0; font-size: 8pt; }
table { width: 100%; border-collapse: collapse; margin: 6px 0 12px; font-size: 8.8pt; }
th { text-align: left; background: #f3f4ee; } th, td { border-bottom: 1px solid #d9dbd0; padding: 4px 6px; vertical-align: top; }
tr { break-inside: avoid; }
img { display: block; max-width: 100%; max-height: 150mm; margin: 6px auto 10px; break-inside: avoid; }
.cover { height: 170mm; display: flex; flex-direction: column; justify-content: center; }
.cover h1 { font-size: 32pt; border: 0; } .cover p { font-size: 12pt; color: #4a4d44; max-width: 210mm; }
.src { color: #6b6e63; font-size: 8.5pt; }
"""


def render() -> str:
    body = markdown.markdown(SOURCE.read_text(encoding="utf-8"), extensions=["tables", "fenced_code"])
    cover = f"""<section class="cover"><p style="color:#2e7d32;font-weight:700">♔ ChessQuery</p>
<h1>Arquitectura v3</h1>
<p>Para las partes interesadas (problema, propuesta de valor y guion de la demo), arquitectura desplegada en AWS, jugar y organizar, eventos, Terraform y despliegue, y el ETL con AWS Lambda.</p>
<p class="src">Generado el {dt.date.today():%d-%m-%Y} desde <code>docs/arquitectura/arquitectura-v3.md</code> y
<code>diagramas.py</code> (fuente única). Si algo cambia, edita esos archivos y corre <code>make arquitectura-docs</code>.</p>
</section>"""
    return (f"<!doctype html><html lang='es'><head><meta charset='utf-8'><title>Arquitectura ChessQuery v3</title>"
            f"<style>{CSS}</style></head><body>{cover}{body}</body></html>")


def main() -> None:
    diagramas.main()
    OUT_HTML.write_text(render(), encoding="utf-8")
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    f"--print-to-pdf={OUT_PDF}", OUT_HTML.as_uri()], check=True, capture_output=True)
    OUT_HTML.unlink()
    print(f"PDF generado: {OUT_PDF.relative_to(HERE.parents[1])}")


if __name__ == "__main__":
    sys.exit(main())
