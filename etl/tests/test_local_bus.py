import json
import threading
import urllib.request

from chessquery_etl import local_bus


class FakeSns:
    def __init__(self):
        self.confirmed, self.subscribed = [], []

    def confirm_subscription(self, TopicArn, Token):
        self.confirmed.append((TopicArn, Token))

    def subscribe(self, **kw):
        self.subscribed.append(kw)
        return {"SubscriptionArn": f"arn:sub:{len(self.subscribed)}"}


def test_la_topologia_declara_la_lambda_de_la_ficha_federativa():
    lambdas = local_bus.subscriptions()
    assert lambdas["federation-lookup"] == ["federation.lookup.requested"]
    assert set(lambdas) <= set(local_bus.HANDLERS), "toda Lambda suscrita necesita su handler local"
    # La cola SQS del ETL ya no existe: SNS entrega directo a la Lambda
    assert "etl-federation-lookup" not in json.loads(local_bus.DEFAULT_TOPOLOGY.read_text())["consumers"]


def test_resuelve_el_mismo_handler_que_la_nube():
    from chessquery_etl.federation import cli
    assert local_bus.resolve(local_bus.HANDLERS["federation-lookup"]) is cli.lambda_handler


def test_suscribe_cada_lambda_con_su_filtro():
    sns = FakeSns()
    arns = local_bus.subscribe_all(sns, "arn:topic", "http://host:8098", {"federation-lookup": ["a.b", "c.d"]})
    assert arns == ["arn:sub:1"]
    sub = sns.subscribed[0]
    assert sub["Protocol"] == "http" and sub["Endpoint"] == "http://host:8098/federation-lookup"
    assert json.loads(sub["Attributes"]["FilterPolicy"]) == {"eventType": ["a.b", "c.d"]}


def test_despacha_confirmaciones_notificaciones_y_errores():
    sns, eventos = FakeSns(), []

    def handler(event, _ctx):
        if event["Records"][0]["Sns"]["Message"] == "explota":
            raise RuntimeError("falla")
        eventos.append(event)

    bus = local_bus.Bus(sns, {"federation-lookup": handler})
    assert bus.dispatch("/otra", "Notification", {}) == 404
    assert bus.dispatch("/federation-lookup", "SubscriptionConfirmation", {"TopicArn": "t", "Token": "k"}) == 200
    assert sns.confirmed == [("t", "k")]
    assert bus.dispatch("/federation-lookup", "UnsubscribeConfirmation", {}) == 200
    assert bus.dispatch("/federation-lookup", "Notification", {"MessageId": "m", "Message": "hola"}) == 200
    assert eventos[0]["Records"][0]["Sns"] == {"MessageId": "m", "TopicArn": None, "Message": "hola",
                                               "MessageAttributes": {}}
    assert bus.dispatch("/federation-lookup", "Notification", {"Message": "explota"}) == 500


def test_servidor_http_entrega_la_notificacion_al_handler():
    recibidos = []
    bus = local_bus.Bus(FakeSns(), {"federation-lookup": lambda event, _ctx: recibidos.append(event)})
    server = local_bus.make_server(bus, 0)  # puerto libre
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        req = urllib.request.Request(f"http://127.0.0.1:{server.server_port}/federation-lookup",
                                     data=json.dumps({"Message": "m"}).encode(), method="POST",
                                     headers={"x-amz-sns-message-type": "Notification"})
        with urllib.request.urlopen(req) as resp:
            assert resp.status == 200
    finally:
        server.shutdown()
    assert recibidos[0]["Records"][0]["Sns"]["Message"] == "m"
