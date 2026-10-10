"""Genera los diagramas SVG de docs/arquitectura/ (los usa build-pdf.py y el Markdown).

Los diagramas están definidos en código a propósito: se editan como cualquier archivo del repo, se revisan en un
PR y no dependen de herramientas externas (ni Graphviz ni un editor de diagramas). Uso: ``make arquitectura-docs``.
"""
from __future__ import annotations

import base64
import html
import pathlib

OUT = pathlib.Path(__file__).resolve().parent / "diagramas"
ICONS = pathlib.Path(__file__).resolve().parent / "iconos"
ICON = 44  # lado de un ícono en el diagrama (px)

# Colores de borde de los grupos, como en los diagramas oficiales de AWS (nube, VPC, cómputo, integración, datos).
AWS = {"nube": "#232F3E", "vpc": "#7AA116", "computo": "#ED7100", "integracion": "#E7157B", "datos": "#C925D1",
       "red": "#8C4FFF", "seguridad": "#DD344C", "azure": "#0078D4", "externo": "#7D8998"}

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
        self.icons: set[str] = set()

    def icon(self, cx, y, name, title, lines=(), color="#232F3E", size=ICON):
        """Ícono oficial centrado en ``cx`` con su nombre (negrita) y hasta tres líneas de detalle debajo."""
        self.icons.add(name)
        self.parts.append(f'<use href="#ic-{name}" xlink:href="#ic-{name}" '
                          f'transform="translate({cx - size / 2} {y}) scale({size / 96})"/>')
        self.text(cx, y + size + 14, title, size=11.5, bold=True, color=color)
        for i, line in enumerate(lines):
            self.text(cx, y + size + 28 + i * 13, line, size=10, color="#3c4043")

    def frame(self, x, y, w, h, label, color, icon=None, fill="none", dashed=False):
        """Grupo al estilo AWS: borde del color de la categoría, ícono chico arriba a la izquierda y su nombre."""
        dash = ' stroke-dasharray="5 4"' if dashed else ""
        self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}" stroke="{color}" '
                          f'stroke-width="1.6"{dash}/>')
        tx = x + 8
        if icon:
            self.icons.add(icon)
            self.parts.append(f'<use href="#ic-{icon}" xlink:href="#ic-{icon}" transform="translate({x} {y}) scale({24 / 96})"/>')
            tx = x + 30
        self.text(tx, y + 16, label, size=11, bold=True, color=color, anchor="start")

    def path(self, points, label="", color="#455a64", dashed=False, lx=None, ly=None):
        """Conector en codo (lista de puntos) con flecha al final y etiqueta opcional en ``(lx, ly)``."""
        dash = ' stroke-dasharray="5 4"' if dashed else ""
        pts = " ".join(f"{x},{y}" for x, y in points)
        self.parts.append(f'<polyline points="{pts}" fill="none" stroke="{color}" stroke-width="1.5"{dash} '
                          f'marker-end="url(#flecha)"/>')
        if label:
            self.label(lx, ly, label, color)

    def label(self, cx, cy, text, color="#455a64"):
        wlab = len(text) * 5.9 + 8
        self.parts.append(f'<rect x="{cx - wlab / 2}" y="{cy - 10}" width="{wlab}" height="15" rx="3" '
                          f'fill="white" fill-opacity="0.95"/>')
        self.text(cx, cy + 1.5, text, size=10.5, color=color)

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
        # Cada ícono se incrusta una sola vez (defs) y se reutiliza con <use>: el SVG queda autocontenido y liviano.
        images = "".join(
            f'<image id="ic-{n}" width="96" height="96" href="data:image/png;base64,{_b64(n)}"/>'
            for n in sorted(self.icons))
        defs = ('<defs><marker id="flecha" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
                f'orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#455a64"/></marker>{images}</defs>')
        body = "\n".join(self.parts)
        OUT.mkdir(exist_ok=True)
        (OUT / f"{name}.svg").write_text(
            f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
            f'viewBox="0 0 {self.w} {self.h}" width="{self.w}" height="{self.h}">'
            f'<rect width="100%" height="100%" fill="white"/>{defs}\n{body}\n</svg>\n', encoding="utf-8")


def _b64(name: str) -> str:
    return base64.b64encode((ICONS / f"{name}.png").read_bytes()).decode()


def arquitectura() -> None:
    """Arquitectura desplegada en AWS (Learner Lab) con los íconos oficiales: entrada, servicios, datos, eventos y ETL."""
    s = Svg(1720, 1000)
    s.text(20, 30, "ChessQuery v3 · arquitectura desplegada en AWS (Learner Lab, us-east-1)", size=17, bold=True,
           color=AWS["nube"], anchor="start")
    s.text(20, 50, "Línea continua: pedido · punteada: respuesta o validación. Todo se crea con Terraform (infra/terraform).",
           size=11, color="#5f6368", anchor="start")
    _usuarios(s)
    _identidad(s)
    s.frame(250, 135, 1230, 850, "AWS Cloud · us-east-1 · Learner Lab (todo corre con el rol LabRole)", AWS["nube"])
    _entrada(s)
    _vpc(s)
    _eventos_y_etl(s)
    _operacion(s)
    s.save("01-arquitectura")


def _usuarios(s: Svg) -> None:
    s.frame(20, 150, 190, 560, "Personas", AWS["externo"], dashed=True)
    s.icon(115, 180, "aws-users", "Jugador", ["navegador o celular"])
    s.icon(115, 300, "aws-client", "Organizador", ["club, torneos y salas"])
    s.icon(115, 420, "aws-client", "Monitor de la sala", ["pantalla en vivo, sin login"])
    s.icon(115, 540, "aws-mobile", "Apoderado", ["sigue a un jugador", "desde el celular"])
    s.path([(210, 207), (316, 207)], "2 · HTTPS", lx=236, ly=207)
    s.path([(210, 547), (316, 547)], "wss://", lx=236, ly=547)


def _identidad(s: Svg) -> None:
    s.icon(640, 40, "aws-cognito", "Amazon Cognito + Google", ["user pool del lab · login solo con Google",
                                                                 "OIDC + PKCE · emite el ID token (JWT)"], color=AWS["seguridad"])
    s.path([(115, 150), (115, 75), (616, 75)], "1 · login", lx=330, ly=75)
    s.path([(905, 190), (905, 62), (664, 62)], "los servicios validan el JWT (JWKS)", dashed=True, lx=800, ly=62)


def _entrada(s: Svg) -> None:
    s.icon(340, 185, "aws-api-gateway", "API Gateway · HTTP API", ["HTTPS (execute-api)", "/api/* → ALB · resto → S3"],
           color=AWS["red"])
    s.icon(340, 355, "aws-s3", "S3 · sitio web", ["la SPA React (build de Vite)"], color=AWS["vpc"])
    s.path([(340, 272), (340, 353)], "el resto: la web", lx=340, ly=315)
    s.icon(340, 525, "aws-api-gateway", "API Gateway · WebSocket", ["partidas y salas en vivo", "(respaldo: long polling)"],
           color=AWS["red"])
    s.path([(362, 200), (575, 200), (575, 248)], "/api/* + cabecera de origen", lx=468, ly=200)
    s.path([(362, 540), (485, 540), (485, 272), (551, 272)], "/internal/ws/*", lx=425, ly=540)
    s.path([(680, 560), (364, 560)], "respuestas de game: Management API", dashed=True, lx=585, ly=560)


def _vpc(s: Svg) -> None:
    s.frame(470, 165, 580, 640, "VPC · 2 zonas · subnets públicas, sin NAT", AWS["vpc"], icon="aws-vpc")
    s.icon(575, 250, "aws-alb", "Application Load Balancer", ["enruta por prefijo", "exige X-Origin-Verify"],
           color=AWS["red"])
    s.frame(680, 190, 350, 455, "ECS Fargate · 3 servicios Java 21", AWS["computo"], icon="aws-fargate")
    services = [("users :8081", ["identidad, perfil, club, roster,", "amigos, ranking y ratings"]),
                ("tournament :8082", ["inscripción, acreditación QR,", "suizo / round robin, sala en vivo"]),
                ("game :8083", ["1 vs 1 con reloj, salas de clase,", "desafío abierto, ELO por ritmo"])]
    for i, (title, lines) in enumerate(services):
        y = 225 + i * 130
        s.icon(855, y, "spring", title, lines, color=AWS["computo"])
        s.path([(700, 272), (700, y + 22), (831, y + 22)] if i else [(597, 272), (700, 272), (700, y + 22), (831, y + 22)])
    s.label(650, 272, "por prefijo")
    s.text(855, 632, "users y tournament en Fargate Spot · game: 1 tarea fija", size=9.5, color="#5f6368")
    s.icon(855, 700, "aws-rds-postgresql", "RDS PostgreSQL 16", ["un schema por servicio (Flyway):",
                                                                "users · tournament · game"], color=AWS["datos"])
    s.path([(855, 645), (855, 698)], "JDBC", lx=885, ly=672)


def _eventos_y_etl(s: Svg) -> None:
    s.icon(1150, 200, "aws-sns", "SNS · chess-events", ["sobre ChessEvent", "filtro por eventType"], color=AWS["integracion"])
    s.icon(1360, 200, "aws-sqs", "SQS · 4 colas con DLQ", ["users-elo · users-rating", "tournament-federation",
                                                           "tournament-players"], color=AWS["integracion"])
    s.path([(1030, 300), (1060, 300), (1060, 215), (1126, 215)], "publican", lx=1060, ly=262)
    s.path([(1174, 222), (1336, 222)], "filtro", lx=1255, ly=222)
    s.path([(1360, 312), (1360, 335), (1032, 335)], "consumen (idempotentes)", lx=1310, ly=335)
    s.frame(1080, 380, 380, 270, "AWS Lambda · ETL en Python", AWS["computo"], icon="aws-lambda")
    lambdas = [(1175, 415, "federation-lookup", ["ficha que el jugador vinculó"]),
               (1365, 415, "external-ratings", ["ELO de Lichess y Chess.com"]),
               (1175, 535, "fide-import", ["lista FIDE (CHI), mensual"]),
               (1365, 535, "federation-tournaments", ["calendario federado, diario"])]
    for cx, y, title, lines in lambdas:
        s.icon(cx, y, "aws-lambda", title, lines, color=AWS["computo"], size=38)
    s.path([(1172, 236), (1230, 236), (1230, 412)], "pedidos: SNS directo, sin cola", lx=1230, ly=362)
    s.text(1270, 640, "responden con rating.updated en chess-events", size=10, color="#5f6368")
    s.icon(1175, 700, "aws-eventbridge", "EventBridge", ["fide-import: día 2 · torneos: diario", "apagado nocturno: 23:00"],
           color=AWS["integracion"])
    s.path([(1175, 698), (1175, 652)])
    s.icon(1365, 700, "aws-s3", "S3 · bucket del ETL", ["raw (30 días) · staged", "rejected · manifests"], color=AWS["vpc"])
    s.path([(1365, 652), (1365, 698)], "guarda", lx=1395, ly=675)
    s.frame(1500, 380, 200, 270, "Fuentes externas", AWS["externo"], icon="aws-internet", dashed=True)
    for i, line in enumerate(["FIDE (lista mensual)", "Federación (GraphQL)", "Lichess (API pública)", "Chess.com (API pública)"]):
        s.text(1600, 440 + i * 30, line, size=11.5, bold=True, color="#3c4043")
    s.text(1600, 580, "ritmo máximo, reintentos", size=10, color="#5f6368")
    s.text(1600, 594, "y circuit breaker", size=10, color="#5f6368")
    s.path([(1460, 515), (1498, 515)], "HTTPS", lx=1480, ly=535)


def _operacion(s: Svg) -> None:
    s.frame(270, 830, 1190, 140, "Operación y seguridad", AWS["seguridad"])
    items = [("aws-ecr", "ECR", ["imágenes de los 3 servicios", "Jib · escaneo al subir"]),
             ("aws-parameter-store", "SSM Parameter Store", ["token interno, pepper,", "cabecera de origen"]),
             ("aws-secrets-manager", "Secrets Manager", ["contraseña maestra de RDS", "(la maneja RDS)"]),
             ("aws-cloudwatch", "CloudWatch", ["logs de servicios y Lambdas", "alarmas: 5xx, RDS, DLQ"]),
             ("aws-sns-email", "SNS · alertas", ["alarmas → correo", "del equipo"]),
             ("aws-iam-role", "IAM · LabRole", ["rol único del lab", "(no se crean roles)"]),
             ("aws-lambda", "apagado nocturno", ["ECS a 0 · RDS detenida", "(ahorra saldo)"])]
    for i, (icon, title, lines) in enumerate(items):
        s.icon(360 + i * 170, 855, icon, title, lines, color=AWS["nube"], size=36)


def despliegue() -> None:
    """Cómo llega el código a la nube: CI en GitHub Actions y despliegue en el Learner Lab con make + Terraform."""
    s = Svg(1500, 640)
    s.text(20, 30, "ChessQuery v3 · integración continua y despliegue", size=17, bold=True, color=AWS["nube"], anchor="start")
    s.frame(20, 55, 1460, 200, "Desarrollo e integración continua (GitHub)", AWS["externo"])
    s.icon(120, 95, "aws-client", "Equipo", ["make dev · make test · make e2e", "(stack local: Postgres, LocalStack,",
                                            "IdP y fuentes simuladas)"])
    s.icon(470, 95, "github-actions", "GitHub Actions · CI", ["en push y PR a main y develop:", "Java · ETL · web · Terraform ·",
                                                               "complejidad · Trivy"])
    s.icon(820, 95, "github-actions", "Dependabot", ["1 PR semanal agrupado", "contra develop, sin mayores"])
    s.box(1010, 90, 440, 110, "nota", "Reglas del repositorio", ["main protegida: todo por PR con CI verde",
                                                                "acciones de terceros fijadas a un commit",
                                                                "cobertura ≥ 90 % por módulo · axe AA"], title_size=12)
    s.path([(220, 117), (446, 117)], "push / PR", lx=335, ly=117)
    s.path([(796, 117), (494, 117)], "abre PRs (pasan por el CI)", dashed=True, lx=645, ly=117)

    s.frame(20, 280, 1460, 340, "Despliegue en el Learner Lab: lo ejecuta una persona (perfil chessquery-academy); "
                                "el agente solo propone el plan", AWS["nube"])
    steps = [("terraform", "academy-bootstrap", ["bucket S3 del estado", "(con lockfile)"]),
             ("terraform", "academy-plan", ["se revisa qué se crea", "o cambia"]),
             ("aws-ecr", "academy-ecr", ["repositorios de", "imágenes"]),
             ("spring", "academy-image", ["Jib construye users,", "tournament y game"]),
             ("terraform", "academy-apply", ["red, ALB, API Gateway,", "ECS, RDS, SNS/SQS, Lambdas"]),
             ("react", "academy-web", ["build de Vite →", "S3 (sitio web)"]),
             ("aws-lambda", "datos iniciales", ["invocar fide-import y", "federation-tournaments"]),
             ("aws-eventbridge", "apagado nocturno", ["23:00 (Chile): ECS a 0", "y RDS detenida · academy-up"])]
    for i, (icon, title, lines) in enumerate(steps):
        cx = 110 + i * 180
        s.icon(cx, 330, icon, f"{i} · {title}", lines, color=AWS["nube"])
        if i < len(steps) - 1:
            s.path([(cx + 28, 352), (cx + 152, 352)])
    s.box(40, 470, 680, 120, "nota", "Por qué así", ["Learner Lab: sin CloudFront, AppSync ni Cloud Map, y sin crear roles IAM",
                                                     "(ADR-0002): entrada por API Gateway y todo con el rol LabRole.",
                                                     "Las credenciales rotan cada ~4 h: todo se recrea con terraform apply.",
                                                     "Configuración en SSM; los secretos nunca van en el código."], title_size=12)
    s.box(760, 470, 700, 120, "externo", "Cuenta propia (envs/aws) · pendiente",
          ["mismo código Terraform con CloudFront delante de S3 y del API,",
           "despliegue desde el CI con OIDC (sin llaves de larga duración)",
           "y WebSocket sin depender del long polling de respaldo"], title_size=12)
    s.save("06-despliegue")


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
    s = Svg(1150, 640)
    s.group(20, 20, 560, 600, "borde", "Código: infra/terraform (un solo código, dos entornos)")
    mods = [("network", "VPC 2 zonas, subnets, grupos de seguridad"), ("data", "RDS, bucket de archivos, SSM"),
            ("s3-bucket", "buckets (en el lab, con la AWS CLI)"), ("alb", "reglas por prefijo + cabecera de origen"),
            ("edge-apigw", "API Gateway HTTP + web en S3"), ("realtime-ws", "API Gateway WebSocket → ALB → game"),
            ("ecs-service", "un servicio Fargate (se usa 3 veces)"), ("messaging", "SNS + colas SQS desde infra/events"),
            ("etl-jobs", "bucket del ETL + 4 Lambdas (SNS directo)"), ("apagado-nocturno", "EventBridge + Lambda: ECS y RDS"),
            ("observability", "alarmas → correo")]
    for i, (m, d) in enumerate(mods):
        s.box(40, 50 + i * 50, 300, 40, "borde", f"modules/{m}", [d], title_size=11.5)
    s.box(380, 240, 180, 120, "servicio", "envs/academy", ["arma los módulos para", "el Learner Lab", "(LabRole, x86, API GW)"])
    s.box(380, 400, 180, 80, "externo", "envs/aws", ["cuenta propia", "(CloudFront) · pendiente"])
    for i in range(len(mods)):
        s.arrow(340, 70 + i * 50, 380, 300, color="#90a4ae")
    s.box(380, 60, 180, 90, "datos", "bootstrap", ["bucket del estado", "(una vez por cuenta)"])

    s.group(620, 20, 510, 600, "servicio", "Orden de despliegue (make …)")
    steps = [("0 · academy-bootstrap", "crea el bucket donde Terraform guarda su estado"),
             ("1 · academy-plan", "simulacro: se revisa qué se crea o cambia"),
             ("2 · academy-ecr", "solo los repositorios de imágenes"),
             ("3 · academy-image", "Jib construye y sube users, tournament, game"),
             ("4 · academy-apply", "todo lo demás (~15 min por la base de datos)"),
             ("5 · academy-web", "build de la web y publicación en S3"),
             ("6 · invocar las Lambdas", "cargar FIDE y el calendario de la Federación"),
             ("7 · academy-down / apagado 23:00", "ECS a 0 y RDS detenida, sin borrar (ahorra saldo)")]
    for i, (t, d) in enumerate(steps):
        y = 50 + i * 70
        s.box(640, y, 470, 50, "servicio", t, [d], title_size=12)
        if i < len(steps) - 1:
            s.arrow(875, y + 50, 875, y + 70)
    s.save("04-terraform")


def etl_lambda() -> None:
    """Pipeline del ETL dentro de una Lambda, paso a paso."""
    s = Svg(1150, 470)
    s.group(20, 20, 250, 430, "etl", "Disparadores")
    s.box(35, 55, 220, 70, "etl", "EventBridge · día 2, 09:00 UTC", ["→ fide-import"], title_size=11.5)
    s.box(35, 145, 220, 70, "etl", "EventBridge · diario, 10:00 UTC", ["→ federation-tournaments"], title_size=11.5)
    s.box(35, 235, 220, 95, "eventos", "SNS chess-events (directo)", ["federation.lookup.requested",
          "external.ratings.sync.requested", "2 reintentos · alarma de errores"], title_size=11.5)
    s.box(35, 345, 220, 90, "nota", "Manual (prueba)", ["aws lambda invoke", "--invocation-type Event"], title_size=12)

    steps = [("1 · Obtener", "FIDE, Federación, Lichess o Chess.com", "(ritmo máximo, reintentos, corte)"),
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
    s.arrow(255, 283, 310, 80)
    s.arrow(255, 390, 310, 80)
    s.box(610, 355, 270, 80, "servicio", "SNS chess-events → SQS", ["users: ratings y fichas de jugadores", "tournament: calendario federativo"],
          title_size=12)
    s.arrow(745, 330, 745, 355)
    s.save("05-etl-lambda")


def main() -> None:
    for fn in (arquitectura, flujo_1v1, flujo_organizador, terraform, etl_lambda, despliegue):
        fn()
    print(f"Diagramas en {OUT.relative_to(OUT.parents[2])}: {', '.join(sorted(p.name for p in OUT.glob('*.svg')))}")


if __name__ == "__main__":
    main()
