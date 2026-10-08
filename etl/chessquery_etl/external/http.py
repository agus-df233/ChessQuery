"""Cliente HTTP con buenos modales para APIs públicas de terceros: ritmo máximo, reintentos y circuit breaker.

Lichess y Chess.com son gratuitas y piden explícitamente no saturarlas (Chess.com responde 429 si se le pide en
paralelo). Igual que con la Federación: si fallan varias veces seguidas, se corta la corrida en vez de insistir.
El transporte, el reloj y la espera se inyectan para probar todo sin red.
"""
from __future__ import annotations

import time
import urllib.error
import urllib.request
from typing import Callable

RETRYABLE_STATUS = {429, 500, 502, 503, 504}
USER_AGENT = "ChessQuery/3 (contacto@chessquery.cl)"

# (method, url, body, headers, timeout) -> (status, text)
Transport = Callable[[str, str, bytes | None, dict, float], tuple[int, str]]


class CircuitOpenError(Exception):
    """Demasiados fallos seguidos: se corta la corrida para no castigar al sitio."""


def urllib_transport(method: str, url: str, body: bytes | None, headers: dict, timeout: float) -> tuple[int, str]:
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:  # noqa: S310 (URL de configuración)
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")


class PoliteHttp:
    def __init__(self, min_interval: float = 1.0, max_retries: int = 3, breaker_threshold: int = 5,
                 transport: Transport = urllib_transport, clock: Callable[[], float] = time.monotonic,
                 sleep: Callable[[float], None] = time.sleep, timeout: float = 15.0):
        self.min_interval, self.max_retries, self.breaker_threshold = min_interval, max_retries, breaker_threshold
        self._transport, self._clock, self._sleep, self._timeout = transport, clock, sleep, timeout
        self._last: float | None = None
        self._failures = 0

    def request(self, method: str, url: str, body: bytes | None = None, content_type: str | None = None) -> tuple[int, str]:
        """Devuelve (status, texto). Reintenta 429/5xx y errores de red con espera creciente; 4xx no se reintenta."""
        headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
        if content_type:
            headers["Content-Type"] = content_type
        for attempt in range(self.max_retries + 1):
            if self._failures >= self.breaker_threshold:
                raise CircuitOpenError(f"{self._failures} fallos seguidos: se corta la corrida")
            self._pace()
            status, text = self._send(method, url, body, headers)
            if status not in RETRYABLE_STATUS:
                self._failures = 0
                return status, text
            self._failures += 1
            if attempt < self.max_retries:
                self._sleep(2 ** attempt)
        return status, text

    def _send(self, method, url, body, headers) -> tuple[int, str]:
        try:
            return self._transport(method, url, body, headers, self._timeout)
        except OSError:  # red caída, DNS, timeout: se trata como 503 (reintentable)
            return 503, ""

    def _pace(self) -> None:
        now = self._clock()
        if self._last is not None and now - self._last < self.min_interval:
            self._sleep(self.min_interval - (now - self._last))
        self._last = self._clock()
