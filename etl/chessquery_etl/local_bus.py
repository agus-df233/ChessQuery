"""Receptor local de SNS: hace en desarrollo lo que en la nube hace la suscripción SNS → Lambda.

En la nube, las Lambdas que reaccionan a eventos (sección ``lambdas`` de ``infra/events/topology.json``) están
suscritas directo al tópico ``chess-events``, sin cola SQS. LocalStack no ejecuta Lambdas sin levantar más
contenedores, así que en local este proceso:

1. levanta un servidor HTTP chico;
2. suscribe una ruta por Lambda al tópico de LocalStack, con el mismo filtro por ``eventType`` que usa Terraform;
3. confirma la suscripción y, por cada notificación, arma el evento de Lambda (``Records[].Sns``) y llama al
   **mismo handler** que corre en la nube.

Uso: ``python -m chessquery_etl.local_bus`` (``make etl-bus-local``; ``make dev`` y ``make e2e`` lo levantan solos).
"""
from __future__ import annotations

import importlib
import json
import logging
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Callable

log = logging.getLogger(__name__)

# Lambda (nombre en topology.json) → handler. Debe coincidir con modules/etl-jobs de Terraform.
HANDLERS = {
    "federation-lookup": "chessquery_etl.federation.cli:lambda_handler",
}

DEFAULT_TOPIC = "arn:aws:sns:us-east-1:000000000000:chess-events"
DEFAULT_TOPOLOGY = Path(__file__).resolve().parents[2] / "infra" / "events" / "topology.json"

Handler = Callable[[dict, object], object]


def subscriptions(topology_path: Path = DEFAULT_TOPOLOGY) -> dict[str, list[str]]:
    """Lambdas suscritas al bus y sus eventTypes, según la topología compartida con Terraform."""
    return json.loads(Path(topology_path).read_text(encoding="utf-8")).get("lambdas", {})


def resolve(spec: str) -> Handler:
    module, func = spec.split(":")
    return getattr(importlib.import_module(module), func)


def lambda_event(notification: dict) -> dict:
    """Notificación HTTP de SNS → evento de Lambda con un registro SNS (mismo formato que en la nube)."""
    return {"Records": [{
        "EventSource": "aws:sns",
        "Sns": {
            "MessageId": notification.get("MessageId"),
            "TopicArn": notification.get("TopicArn"),
            "Message": notification.get("Message", ""),
            "MessageAttributes": notification.get("MessageAttributes", {}),
        },
    }]}


class Bus:
    """Despacha lo que llega por HTTP: confirma suscripciones e invoca el handler de cada ruta."""

    def __init__(self, sns, handlers: dict[str, Handler]):
        self.sns = sns
        self.handlers = handlers

    def dispatch(self, path: str, message_type: str, body: dict) -> int:
        name = path.strip("/")
        if name not in self.handlers:
            return 404
        if message_type == "SubscriptionConfirmation":
            self.sns.confirm_subscription(TopicArn=body["TopicArn"], Token=body["Token"])
            log.info("Suscripción confirmada: %s", name)
            return 200
        if message_type != "Notification":
            return 200
        try:
            self.handlers[name](lambda_event(body), None)
        except Exception:  # noqa: BLE001 — en la nube Lambda reintenta; acá se registra y SNS reintenta la entrega
            log.exception("Falló %s con el mensaje %s", name, body.get("MessageId"))
            return 500
        return 200


def subscribe_all(sns, topic_arn: str, base_url: str, lambdas: dict[str, list[str]]) -> list[str]:
    """Suscribe una ruta por Lambda con su filtro. Devuelve los ARN para desuscribir al salir."""
    arns = []
    for name, event_types in lambdas.items():
        resp = sns.subscribe(TopicArn=topic_arn, Protocol="http", Endpoint=f"{base_url}/{name}",
                             Attributes={"FilterPolicy": json.dumps({"eventType": event_types})},
                             ReturnSubscriptionArn=True)
        arns.append(resp["SubscriptionArn"])
        log.info("chess-events → %s (%s)", name, ", ".join(event_types))
    return arns


def make_server(bus: Bus, port: int) -> ThreadingHTTPServer:
    class Request(BaseHTTPRequestHandler):
        def do_POST(self):  # noqa: N802 — nombre que exige http.server
            length = int(self.headers.get("Content-Length") or 0)
            body = json.loads(self.rfile.read(length) or b"{}")
            status = bus.dispatch(self.path, self.headers.get("x-amz-sns-message-type", ""), body)
            self.send_response(status)
            self.end_headers()

        def log_message(self, *_):  # el log útil lo hace Bus
            pass

    return ThreadingHTTPServer(("0.0.0.0", port), Request)


def main() -> None:  # pragma: no cover — se prueba de punta a punta con make e2e
    import boto3  # import tardío, igual que en handler.py
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    port = int(os.environ.get("LOCAL_BUS_PORT", "8098"))
    host = os.environ.get("LOCAL_BUS_HOST", "host.docker.internal")  # cómo ve LocalStack (Docker) a este proceso
    topic = os.environ.get("CHESS_EVENTS_TOPIC_ARN", DEFAULT_TOPIC)
    lambdas = {k: v for k, v in subscriptions().items() if k in HANDLERS}
    sns = boto3.client("sns")
    server = make_server(Bus(sns, {k: resolve(HANDLERS[k]) for k in lambdas}), port)
    arns = subscribe_all(sns, topic, f"http://{host}:{port}", lambdas)
    print(f"Receptor SNS local en :{port} (Ctrl+C para salir)", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        for arn in arns:
            sns.unsubscribe(SubscriptionArn=arn)


if __name__ == "__main__":  # pragma: no cover
    main()
