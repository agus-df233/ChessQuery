"""Genera docs/etl/ChessQuery-ETL-guia-equipo.pdf a partir de los Markdown de docs/etl/ y etl/README.md.

Los Markdown son la fuente única (los leen personas y agentes); el PDF es solo una vista para imprimir o
compartir. Uso: ``make etl-docs`` (necesita Google Chrome instalado para imprimir a PDF).
"""
from __future__ import annotations

import datetime as dt
import html
import pathlib
import subprocess
import sys

import markdown

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT_HTML = ROOT / "docs/etl/.guia-equipo.html"
OUT_PDF = ROOT / "docs/etl/ChessQuery-ETL-guia-equipo.pdf"
SOURCES = ["docs/etl/onboarding.md", "docs/etl/federacion.md", "docs/etl/nueva-fuente.md",
           "docs/etl/glosario.md", "etl/AGENTS.md", "etl/README.md"]
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

CSS = """
@page { size: A4; margin: 16mm 15mm 18mm; }
html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body { font: 10pt/1.5 -apple-system, "Helvetica Neue", Arial, sans-serif; color: #1d1f1a; }
h1 { font-size: 18pt; border-bottom: 2px solid #6abf74; padding-bottom: 6px; margin-top: 0; }
h2 { font-size: 13pt; margin: 18px 0 6px; } h3 { font-size: 11pt; }
code { font: 8.6pt "SF Mono", Menlo, monospace; background: #f3f4ee; padding: 1px 4px; border-radius: 3px; }
pre { background: #f3f4ee; border: 1px solid #d9dbd0; border-radius: 6px; padding: 8px 10px; white-space: pre-wrap; }
pre code { background: none; padding: 0; font-size: 8pt; }
table { width: 100%; border-collapse: collapse; margin: 6px 0 12px; font-size: 8.8pt; }
th { text-align: left; background: #f3f4ee; } th, td { border-bottom: 1px solid #d9dbd0; padding: 4px 6px; vertical-align: top; }
tr { break-inside: avoid; }
.doc { break-before: page; } .src { color: #6b6e63; font-size: 8.5pt; margin: -4px 0 10px; }
.cover { height: 250mm; display: flex; flex-direction: column; justify-content: center; }
.cover h1 { font-size: 30pt; border: 0; } .cover p { font-size: 12pt; color: #4a4d44; max-width: 150mm; }
"""


def render() -> str:
    parts = [f"""<section class="cover"><p style="color:#3d8a4a;font-weight:700">♔ ChessQuery</p>
<h1>Guía del ETL para el equipo</h1>
<p>Onboarding, ingesta de la Federación, receta para nuevas fuentes, glosario y reglas para agentes.</p>
<p class="src">Generado el {dt.date.today():%d-%m-%Y} desde los Markdown del repositorio (fuente única):
{', '.join(SOURCES)}. Si cambias una regla, edita el Markdown y corre <code>make etl-docs</code>.</p></section>"""]
    for rel in SOURCES:
        body = markdown.markdown((ROOT / rel).read_text(encoding="utf-8"), extensions=["tables", "fenced_code"])
        parts.append(f'<section class="doc"><p class="src">Fuente: <code>{html.escape(rel)}</code></p>{body}</section>')
    return f"<!doctype html><html lang='es'><head><meta charset='utf-8'><title>Guía del ETL</title><style>{CSS}</style></head><body>{''.join(parts)}</body></html>"


def main() -> None:
    OUT_HTML.write_text(render(), encoding="utf-8")
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    f"--print-to-pdf={OUT_PDF}", OUT_HTML.as_uri()], check=True, capture_output=True)
    OUT_HTML.unlink()
    print(f"PDF generado: {OUT_PDF.relative_to(ROOT)}")


if __name__ == "__main__":
    sys.exit(main())
