"""Genera los diagramas SVG de docs/arquitectura/ (los usa build-pdf.py y el Markdown).

Los diagramas están definidos en código a propósito: se editan como cualquier archivo del repo, se revisan en un
PR y no dependen de herramientas externas (ni Graphviz ni un editor de diagramas). Uso: ``make arquitectura-docs``.
"""
from __future__ import annotations

import html
import pathlib

OUT = pathlib.Path(__file__).resolve().parent / "diagramas"

# Paleta (tema claro, pensado para imprimir). Cada familia de componentes tiene su color.
C = {
    "persona": ("#f5f5f5", "#424242"),
    "borde": ("#e3f2fd", "#1565c0"),
    "servicio": ("#e8f5e9", "#2e7d32"),
    "datos": ("#f3e5f5", "#6a1b9a"),
    "eventos": ("#fff3e0", "#e65100"),
    "etl": ("#e0f2f1", "#00695c"),
    "identidad": ("#fce4ec", "#ad1457"),
    "externo": ("#fafafa", "#757575"),
    "nota": ("#fffde7", "#9e9d24"),
}
FONT = "Helvetica, Arial, sans-serif"


class Svg:
    def __init__(self, w: int, h: int):
        self.w, self.h, self.parts = w, h, []

    def box(self, x, y, w, h, kind, title, lines=(), dashed=False, title_size=13):
        fill, stroke = C[kind]
        dash = ' stroke-dasharray="6 4"' if dashed else ""
        self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="{fill}" '
                          f'stroke="{stroke}" stroke-width="1.6"{dash}/>')
        ty = y + 20 if lines else y + h / 2 + 5
        self.text(x + w / 2, ty, title, size=title_size, bold=True, color=stroke)
        for i, line in enumerate(lines):
            self.text(x + w / 2, ty + 17 + i * 15, line, size=11, color="#333")

    def group(self, x, y, w, h, kind, label):
        _, stroke = C[kind]
        self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="none" '
                          f'stroke="{stroke}" stroke-width="1.2" stroke-dasharray="4 3"/>')
        self.text(x + 10, y + 16, label, size=11, bold=True, color=stroke, anchor="start")

    def text(self, x, y, s, size=12, bold=False, color="#222", anchor="middle"):
        weight = ' font-weight="700"' if bold else ""
        self.parts.append(f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}"{weight} '
                          f'fill="{color}" text-anchor="{anchor}">{html.escape(s)}</text>')

    def arrow(self, x1, y1, x2, y2, label="", color="#455a64", dashed=False, lx=None, ly=None, above=False):
        """Flecha con etiqueta opcional: sobre la línea (con fondo blanco) o, con ``above``, encima de ella."""
        dash = ' stroke-dasharray="5 4"' if dashed else ""
        self.parts.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="1.5"'
                          f'{dash} marker-end="url(#flecha)"/>')
        if label and above:
            self.text((x1 + x2) / 2, min(y1, y2) - 6, label, size=10.5, color=color)
        elif label:
            cx, cy = (lx if lx is not None else (x1 + x2) / 2), (ly if ly is not None else (y1 + y2) / 2)
            wlab = len(label) * 5.9 + 8
            self.parts.append(f'<rect x="{cx - wlab / 2}" y="{cy - 10}" width="{wlab}" height="15" rx="3" '
                              f'fill="white" fill-opacity="0.92"/>')
            self.text(cx, cy + 1.5, label, size=10.5, color=color)

    def save(self, name: str) -> None:
        defs = ('<defs><marker id="flecha" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
                'orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#455a64"/></marker></defs>')
        body = "\n".join(self.parts)
        OUT.mkdir(exist_ok=True)
        (OUT / f"{name}.svg").write_text(
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.w} {self.h}" width="{self.w}" '
            f'height="{self.h}"><rect width="100%" height="100%" fill="white"/>{defs}\n{body}\n</svg>\n',
            encoding="utf-8")


def arquitectura() -> None:
    """Vista general: entrada, servicios, datos, bus de eventos y ETL."""
    s = Svg(1200, 770)
    s.box(20, 140, 150, 50, "persona", "Jugador", ["navegador o celular"])
    s.box(20, 210, 150, 50, "persona", "Organizador", ["su club y torneos"])
    s.box(20, 280, 150, 50, "persona", "Sala / público", ["vistas sin login"])

    s.box(230, 25, 220, 75, "identidad", "Entra External ID", ["login con Google o código al correo", "entrega el token (OIDC + PKCE)"])
    s.box(230, 195, 220, 90, "borde", "API Gateway (HTTP API)", ["HTTPS · execute-api.amazonaws.com", "/api/* → ALB", "el resto → web (S3)"])
    s.box(230, 330, 220, 60, "borde", "S3 · sitio web", ["la SPA React (build de Vite)"])
    s.box(495, 195, 175, 90, "borde", "ALB", ["enruta por prefijo", "exige X-Origin-Verify", "(nadie se salta la entrada)"])

    s.group(715, 50, 245, 400, "servicio", "ECS Fargate · 1 tarea por servicio")
    s.box(730, 80, 215, 105, "servicio", "users :8081", ["identidad y perfil, club y roster,", "amigos, ranking, ratings e historial,", "ficha federativa, privacidad"])
    s.box(730, 200, 215, 105, "servicio", "tournament :8082", ["torneos del club, inscripción,", "pareo suizo y round robin,", "tabla, cierre con rating, TRF"])
    s.box(730, 320, 215, 115, "servicio", "game :8083", ["partidas 1 vs 1, desafíos,", "jugadas y reloj en el servidor,", "long polling, PGN, ELO"])

    s.box(1005, 60, 180, 70, "datos", "SSM Parameter Store", ["token interno, pepper,", "cabecera de origen"])
    s.box(1005, 200, 180, 95, "datos", "RDS PostgreSQL 16", ["un schema por servicio:", "users · tournament · game", "(Flyway es dueño)"])
    s.box(1005, 335, 180, 70, "datos", "CloudWatch", ["logs de cada servicio", "alarmas → tu correo"])

    s.group(20, 490, 430, 265, "etl", "ETL · AWS Lambda (Python, sin dependencias)")
    s.box(35, 520, 125, 55, "etl", "EventBridge", ["reglas programadas"])
    s.box(35, 590, 125, 75, "externo", "Fuentes", ["FIDE (lista mensual)", "Federación (GraphQL)"])
    s.box(180, 518, 255, 40, "etl", "fide-import", ["día 2 de cada mes"], title_size=12)
    s.box(180, 568, 255, 40, "etl", "federation-tournaments", ["todos los días"], title_size=12)
    s.box(180, 618, 255, 40, "etl", "federation-lookup", ["cuando un jugador vincula su ficha"], title_size=12)
    s.box(35, 685, 400, 55, "datos", "S3 · bucket del ETL", ["raw/ (se borra a los 30 días) · staged/ · rejected/ · manifests/"])

    s.box(495, 520, 175, 70, "eventos", "SNS chess-events", ["sobre ChessEvent", "+ atributo eventType"])
    s.group(715, 490, 470, 265, "eventos", "Consumidores: cola SQS con DLQ, o Lambda directa")
    queues = [("users-elo", "elo.updated → users"), ("users-rating", "rating.updated → users"),
              ("tournament-federation", "federation.tournament.published → tournament"),
              ("tournament-players", "player.merged → tournament"),
              ("Lambda federation-lookup (sin cola)", "federation.lookup.requested → SNS directo")]
    for i, (q, what) in enumerate(queues):
        y = 512 + i * 48
        s.box(730, y, 440, 42, "eventos", q, [what], title_size=11.5)

    for y in (165, 235, 305):
        s.arrow(170, y, 230, 240)
    s.arrow(170, 150, 230, 80, "login", color=C["identidad"][1], dashed=True, lx=190, ly=118)
    s.arrow(450, 62, 730, 95, "valida el token (JWKS)", color=C["identidad"][1], dashed=True)
    s.arrow(340, 285, 340, 330, "$default")
    s.arrow(450, 240, 495, 240, "/api/*")
    s.arrow(670, 225, 730, 135)
    s.arrow(670, 240, 730, 250)
    s.arrow(670, 255, 730, 375)
    s.arrow(730, 420, 630, 285, "/internal (token + origen)", dashed=True, lx=640, ly=360)
    s.arrow(960, 245, 1005, 245, "JDBC")
    s.arrow(1005, 95, 960, 95, "secretos")
    s.arrow(960, 370, 1005, 370, "logs")
    s.arrow(760, 450, 620, 520, "publica eventos", lx=700, ly=482)
    s.arrow(670, 555, 715, 555)
    s.arrow(900, 490, 900, 452, "consumen", lx=955, ly=472)
    s.arrow(160, 540, 180, 538)
    s.arrow(160, 555, 180, 588)
    s.arrow(160, 625, 180, 600)
    s.arrow(435, 540, 495, 548, "publica", lx=465, ly=530)
    s.arrow(730, 725, 435, 642, "dispara (lotes de 5)", dashed=True, lx=585, ly=690)
    s.arrow(305, 658, 305, 685)
    s.save("01-arquitectura")


def flujo_1v1() -> None:
    """Secuencia de una partida 1 vs 1: desafío, jugadas en vivo y actualización del rating."""
    s = Svg(1150, 700)
    cols = {"A": 90, "B": 270, "game": 520, "users": 790, "bus": 1030}
    names = {"A": "Jugador A", "B": "Jugador B", "game": "game", "users": "users", "bus": "SNS → SQS"}
    kinds = {"A": "persona", "B": "persona", "game": "servicio", "users": "servicio", "bus": "eventos"}
    for k, x in cols.items():
        s.box(x - 70, 15, 140, 38, kinds[k], names[k])
        s.parts.append(f'<line x1="{x}" y1="53" x2="{x}" y2="680" stroke="#b0bec5" stroke-width="1.2" stroke-dasharray="4 4"/>')

    def msg(y, a, b, label, dashed=False):
        s.arrow(cols[a], y, cols[b], y, label, dashed=dashed, above=True)

    msg(85, "A", "game", "POST /api/games {rival, ritmo, color} → PENDING")
    msg(120, "game", "users", "nombre público y rating (/internal)", dashed=True)
    msg(160, "B", "game", "Mis partidas: ve el desafío y lo acepta → ACTIVE")
    s.box(cols["game"] - 120, 180, 240, 34, "nota", "empieza a correr el reloj de blancas", title_size=11)
    msg(245, "A", "game", "GET /api/games/{id}?afterVersion=n  (espera ≤ 25 s)")
    msg(285, "B", "game", "GET …?afterVersion=n  (espera la jugada del rival)")
    msg(325, "A", "game", "POST /moves {uci}: valida la jugada, descuenta el reloj, suma incremento")
    s.arrow(cols["game"], 360, cols["B"], 360, "responde el long poll de B con la jugada (versión + 1)",
            color=C["servicio"][1], above=True)
    s.box(cols["A"] - 60, 385, cols["game"] - cols["A"] + 190, 34, "nota",
          "se repite jugada a jugada · tablas, abandono, y un barrido cada 1 s cierra por tiempo aunque nadie esté conectado",
          title_size=11)
    s.box(cols["game"] - 130, 440, 260, 50, "servicio", "fin de la partida", ["mate, tablas, abandono o tiempo → PGN + ELO"], title_size=12)
    msg(520, "game", "bus", "elo.updated × 2 (PLATFORM, source GAME) + game.finished")
    msg(560, "bus", "users", "cola users-elo (idempotente)")
    s.box(cols["users"] - 125, 580, 250, 42, "servicio", "actualiza rating de plataforma", ["e historial de ambos jugadores"], title_size=12)
    msg(650, "A", "users", "GET /api/users/me → Mi inicio muestra el nuevo rating y el gráfico")
    s.save("02-flujo-1v1")


def flujo_organizador() -> None:
    """Flujo del organizador: club, roster, torneo, rondas, cierre y efecto en las fichas de los jugadores."""
    s = Svg(1150, 470)
    top = [("1 · Crear el club", "users", ["crear mi club = ser organizador", "plan y límites (torneos activos)"]),
           ("2 · Roster", "users", ["alta individual o CSV con vista previa", "etiquetas, bajas; provisorios"]),
           ("3 · Crear el torneo", "tournament", ["suizo o todos contra todos", "valida el plan del club en users"]),
           ("4 · Inscribir", "tournament", ["todo el roster o uno a uno;", "el jugador también se inscribe solo"])]
    bottom = [("8 · Compartir", "tournament", ["vista pública /torneos/:id (QR)", "exportar TRF para homologar"]),
              ("7 · Cerrar el torneo", "tournament", ["tabla final + elo.updated", "por cada jugador"]),
              ("6 · Resultados por mesa", "tournament", ["1-0, ½-½, 0-1, no presentación;", "solo la ronda en curso"]),
              ("5 · Generar ronda", "tournament", ["pareo suizo sin revanchas,", "colores equilibrados y bye"])]
    for i, (t, svc, lines) in enumerate(top):
        x = 20 + i * 282
        s.box(x, 30, 250, 95, "servicio", t, lines + [f"servicio: {svc}"])
        if i < 3:
            s.arrow(x + 250, 78, x + 282, 78)
    for i, (t, svc, lines) in enumerate(bottom):
        x = 20 + i * 282
        s.box(x, 190, 250, 95, "servicio", t, lines + [f"servicio: {svc}"])
        if i < 3:
            s.arrow(x + 282, 238, x + 250, 238)
    s.arrow(20 + 3 * 282 + 125, 125, 20 + 3 * 282 + 125, 190)
    s.arrow(20 + 282 + 125, 285, 20 + 282 + 125, 345, "SNS → users-elo")
    s.box(20 + 282 - 20, 345, 290, 80, "servicio", "users actualiza cada ficha",
          ["rating de plataforma e historial", "(se ve en Jugadores y en Mi inicio)"])
    s.box(640, 345, 490, 80, "etl", "Calendario de la Federación (ETL)",
          ["la Lambda diaria publica federation.tournament.published;", "tournament lo muestra en /torneos junto a los del club"])
    s.box(20, 345, 240, 80, "nota", "Tabla con desempates",
          ["Buchholz, Buchholz corte 1,", "Sonneborn-Berger, victorias"], title_size=12)
    s.save("03-flujo-organizador")


def terraform() -> None:
    """Módulos de Terraform y orden de despliegue en el Learner Lab."""
    s = Svg(1150, 560)
    s.group(20, 20, 560, 520, "borde", "Código: infra/terraform (un solo código, dos entornos)")
    mods = [("network", "VPC 2 zonas, subnets, grupos de seguridad"), ("data", "RDS, bucket de archivos, SSM"),
            ("s3-bucket", "buckets (en el lab, con la AWS CLI)"), ("alb", "reglas por prefijo + cabecera de origen"),
            ("edge-apigw", "API Gateway + web en S3"), ("ecs-service", "un servicio Fargate (se usa 3 veces)"),
            ("messaging", "SNS + colas SQS desde infra/events"), ("etl-jobs", "bucket del ETL + 3 Lambdas"),
            ("observability", "alarmas → correo")]
    for i, (m, d) in enumerate(mods):
        s.box(40, 50 + i * 50, 300, 40, "borde", f"modules/{m}", [d], title_size=11.5)
    s.box(380, 200, 180, 120, "servicio", "envs/academy", ["arma los módulos para", "el Learner Lab", "(LabRole, x86, API GW)"])
    s.box(380, 360, 180, 80, "externo", "envs/aws", ["cuenta propia", "(CloudFront) · pendiente"])
    for i in range(len(mods)):
        s.arrow(340, 70 + i * 50, 380, 260, color="#90a4ae")
    s.box(380, 60, 180, 90, "datos", "bootstrap", ["bucket del estado", "(una vez por cuenta)"])

    s.group(620, 20, 510, 520, "servicio", "Orden de despliegue (make …)")
    steps = [("0 · academy-bootstrap", "crea el bucket donde Terraform guarda su estado"),
             ("1 · academy-plan", "simulacro: 107 recursos a crear, 0 a cambiar"),
             ("2 · academy-ecr", "solo los repositorios de imágenes"),
             ("3 · academy-image", "Jib construye y sube users, tournament, game"),
             ("4 · academy-apply", "todo lo demás (~15 min por la base de datos)"),
             ("5 · academy-web", "build de la web y publicación en S3"),
             ("6 · invocar las Lambdas", "cargar FIDE y el calendario de la Federación"),
             ("7 · academy-down", "apagar ECS y RDS sin borrar (ahorra saldo)")]
    for i, (t, d) in enumerate(steps):
        y = 50 + i * 60
        s.box(640, y, 470, 46, "servicio", t, [d], title_size=12)
        if i < len(steps) - 1:
            s.arrow(875, y + 46, 875, y + 60)
    s.save("04-terraform")


def etl_lambda() -> None:
    """Pipeline del ETL dentro de una Lambda, paso a paso."""
    s = Svg(1150, 470)
    s.group(20, 20, 250, 430, "etl", "Disparadores")
    s.box(35, 55, 220, 70, "etl", "EventBridge · día 2, 09:00 UTC", ["→ fide-import"], title_size=11.5)
    s.box(35, 145, 220, 70, "etl", "EventBridge · diario, 10:00 UTC", ["→ federation-tournaments"], title_size=11.5)
    s.box(35, 235, 220, 85, "eventos", "SNS chess-events (directo)", ["federation.lookup.requested", "2 reintentos · alarma de errores"], title_size=11.5)
    s.box(35, 340, 220, 95, "nota", "Manual (prueba)", ["aws lambda invoke", "--invocation-type Event"], title_size=12)

    steps = [("1 · Obtener", "descarga FIDE o consulta la Federación", "(ritmo máx. 5/s, reintentos, corte)"),
             ("2 · Contrato", "la Federación: si cambió el esquema,", "falla sin bajar datos"),
             ("3 · Validar", "reglas por fila; los rechazos", "se guardan solo con id y motivo"),
             ("4 · Minimizar", "RUT → rutHash (HMAC + pepper de SSM),", "fecha de nacimiento → solo el año"),
             ("5 · Guardar en S3", "raw/ (30 días) · staged/ ·", "rejected/ · manifests/"),
             ("6 · Diff", "solo lo nuevo o cambiado", "respecto de la corrida anterior"),
             ("7 · Publicar", "SNS en lotes de 200 (≈ 60 KB):", "rating.updated / torneos publicados")]
    for i, (t, a, b) in enumerate(steps):
        col, row = divmod(i, 4)
        x, y = 310 + col * 300, 40 + row * 105
        s.box(x, y, 270, 80, "etl", t, [a, b], title_size=12)
        if i < len(steps) - 1 and row < 3:
            s.arrow(x + 135, y + 80, x + 135, y + 105)
    s.arrow(580, 395, 610, 80, color="#90a4ae")
    s.arrow(255, 90, 310, 80)
    s.arrow(255, 180, 310, 80)
    s.arrow(255, 278, 310, 80)
    s.arrow(255, 387, 310, 80)
    s.box(610, 355, 270, 80, "servicio", "SNS chess-events → SQS", ["users: ratings y fichas de jugadores", "tournament: calendario federativo"],
          title_size=12)
    s.arrow(745, 330, 745, 355)
    s.save("05-etl-lambda")


def main() -> None:
    for fn in (arquitectura, flujo_1v1, flujo_organizador, terraform, etl_lambda):
        fn()
    print(f"Diagramas en {OUT.relative_to(OUT.parents[2])}: {', '.join(sorted(p.name for p in OUT.glob('*.svg')))}")


if __name__ == "__main__":
    main()
