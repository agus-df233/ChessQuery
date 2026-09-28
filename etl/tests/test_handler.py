import io
import json
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from chessquery_etl import handler

SAMPLE = Path(__file__).parent / "fixtures" / "players_list_sample.txt"


class NoSuchKey(Exception):
    pass


class FakeS3:
    class exceptions:  # noqa: N801 (imita boto3: client.exceptions.NoSuchKey)
        NoSuchKey = NoSuchKey

    def __init__(self):
        self.objects: dict[str, bytes] = {}

    def get_object(self, Bucket, Key):  # noqa: N803
        if Key not in self.objects:
            raise NoSuchKey(Key)
        return {"Body": io.BytesIO(self.objects[Key])}

    def put_object(self, Bucket, Key, Body):  # noqa: N803
        self.objects[Key] = Body


class FakeSns:
    def __init__(self):
        self.messages = []

    def publish(self, TopicArn, Message, MessageAttributes):  # noqa: N803
        self.messages.append((TopicArn, json.loads(Message), MessageAttributes))


def zipped(text: str) -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("players_list_foa.txt", text.encode("latin-1"))
    return buf.getvalue()


def test_import_lines_publishes_with_event_type_attribute():
    s3, sns = FakeS3(), FakeSns()
    lines = SAMPLE.read_text(encoding="latin-1").splitlines()

    result = handler.import_lines(lines, "2026-10", "bucket", "arn:topic", (s3, sns))

    assert result == {"period": "2026-10", "read": 5, "rated": 4, "changed": 4, "batches": 1}
    topic, body, attrs = sns.messages[0]
    assert topic == "arn:topic" and attrs["eventType"]["StringValue"] == "rating.updated"
    assert {p["fideId"] for p in body["payload"]["players"]} == {"9000001", "9000002", "9000003", "9000005"}
    assert "staged/source=fide/period=2026-10/players.jsonl" in s3.objects


def test_lambda_handler_downloads_stores_raw_and_runs(monkeypatch):
    s3, sns = FakeS3(), FakeSns()
    monkeypatch.setattr(handler, "download", lambda url: zipped(SAMPLE.read_text(encoding="latin-1")))
    monkeypatch.setattr(handler, "_clients", lambda: (s3, sns))
    monkeypatch.setenv("ETL_BUCKET", "bucket")
    monkeypatch.setenv("CHESS_EVENTS_TOPIC_ARN", "arn:topic")

    result = handler.lambda_handler({"period": "2026-11"}, None)

    assert result["changed"] == 4
    assert "raw/source=fide/period=2026-11/players_list.zip" in s3.objects


def test_cli_reads_txt_and_zip(monkeypatch, tmp_path, capsys):
    s3, sns = FakeS3(), FakeSns()
    monkeypatch.setattr(handler, "_clients", lambda: (s3, sns))
    handler.main(["--file", str(SAMPLE), "--period", "2026-10"])
    assert json.loads(capsys.readouterr().out)["rated"] == 4

    z = tmp_path / "list.zip"
    z.write_bytes(zipped(SAMPLE.read_text(encoding="latin-1")))
    handler.main(["--file", str(z), "--period", "2026-11"])
    assert json.loads(capsys.readouterr().out)["changed"] == 0  # mismo contenido que octubre

    monkeypatch.setattr(handler, "download", lambda url: z.read_bytes())
    handler.main(["--period", "2026-12"])
    assert json.loads(capsys.readouterr().out)["read"] == 5


def test_current_period_and_download(monkeypatch):
    assert handler.current_period(datetime(2026, 10, 2, tzinfo=timezone.utc)) == "2026-10"

    class Resp(io.BytesIO):
        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

    seen = {}

    def fake_urlopen(req, timeout):
        seen["ua"] = req.get_header("User-agent")
        return Resp(b"zip")

    monkeypatch.setattr(handler.urllib.request, "urlopen", fake_urlopen)
    assert handler.download("https://example.invalid/list.zip") == b"zip"
    assert seen["ua"].startswith("ChessQuery-ETL")
