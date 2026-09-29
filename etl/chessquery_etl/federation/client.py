"""Cliente GraphQL de la Federación con buenos modales: ritmo máximo, reintentos y circuit breaker.

Por qué tanto cuidado: es el sitio de la Federación, no una API pensada para integraciones. Si responde
mal varias veces seguidas, el circuito se abre y la corrida se corta (mejor fallar y avisar que insistir).
El transporte y el reloj se inyectan para poder probar todo sin red (tests/test_federation_client.py).
"""
from __future__ import annotations

import json
import random
import time
import urllib.error
import urllib.request
from typing import Callable

from .config import FederationConfig

RETRYABLE_STATUS = {429, 500, 502, 503, 504}

# (url, body_bytes, headers, timeout) -> (status, body_text)
Transport = Callable[[str, bytes, dict, float], tuple[int, str]]


class FederationError(Exception):
    """Error de la Federación que no se arregla reintentando (consulta inválida, respuesta con `errors`)."""


class CircuitOpenError(FederationError):
    """Demasiados fallos seguidos: se corta la corrida para no castigar al sitio."""


def urllib_transport(url: str, body: bytes, headers: dict, timeout: float) -> tuple[int, str]:
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:  # noqa: S310 (URL de configuración)
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")


class FederationClient:
    def __init__(self, config: FederationConfig, transport: Transport = urllib_transport,
                 clock: Callable[[], float] = time.monotonic, sleep: Callable[[float], None] = time.sleep):
        self.config = config
        self._transport, self._clock, self._sleep = transport, clock, sleep
        self._last_request_at: float | None = None
        self._consecutive_failures = 0

    def query(self, query: str, variables: dict | None = None) -> dict:
        """Ejecuta una consulta y devuelve ``data``. Lanza FederationError si la respuesta trae ``errors``."""
        body = json.dumps({"query": query, "variables": variables or {}}).encode("utf-8")
        status, text = self._send_with_retries(body)
        payload = json.loads(text)
        if payload.get("errors"):
            raise FederationError(f"La Federación respondió con errores: {payload['errors'][:2]}")
        return payload.get("data") or {}

    def _send_with_retries(self, body: bytes) -> tuple[int, str]:
        for attempt in range(self.config.max_retries + 1):
            self._check_breaker()
            self._respect_rate_limit()
            status, text = self._send_once(body)
            if status == 200:
                self._consecutive_failures = 0
                return status, text
            self._consecutive_failures += 1
            if status not in RETRYABLE_STATUS or attempt == self.config.max_retries:
                raise FederationError(f"HTTP {status} de la Federación tras {attempt + 1} intento(s)")
            self._sleep(self._backoff(attempt))
        raise FederationError("sin respuesta")  # pragma: no cover (el bucle siempre retorna o lanza)

    def _send_once(self, body: bytes) -> tuple[int, str]:
        headers = {"Content-Type": "application/json", "User-Agent": self.config.user_agent}
        try:
            return self._transport(self.config.graphql_url, body, headers, self.config.timeout_s)
        except OSError:  # red caída, timeout, DNS: se trata como un 503 reintentable
            return 503, ""

    def _check_breaker(self) -> None:
        if self._consecutive_failures >= self.config.breaker_threshold:
            raise CircuitOpenError(f"{self._consecutive_failures} fallos seguidos: circuito abierto")

    def _respect_rate_limit(self) -> None:
        min_interval = 1.0 / self.config.requests_per_second
        if self._last_request_at is not None:
            wait = min_interval - (self._clock() - self._last_request_at)
            if wait > 0:
                self._sleep(wait)
        self._last_request_at = self._clock()

    @staticmethod
    def _backoff(attempt: int) -> float:
        """1 s, 2 s, 4 s… más un jitter de hasta 0,5 s para no reintentar todos a la vez."""
        return 2 ** attempt + random.uniform(0, 0.5)  # noqa: S311 (jitter, no criptografía)
