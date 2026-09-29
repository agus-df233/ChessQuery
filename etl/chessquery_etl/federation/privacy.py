"""Minimización en origen: el RUT en claro nunca sale de este proceso.

Validamos el dígito verificador y lo convertimos en un HMAC-SHA256 con el mismo *pepper* que usa el servicio
``users`` (``IdentifierHasher.java``). Así ``users`` puede hacer match por RUT sin que el RUT viaje por SNS/SQS
ni quede en S3. Si este cálculo cambia, deja de coincidir con Java: hay un test que compara ambos
(tests/test_federation_privacy.py) usando el mismo vector de prueba que el lado Java.
"""
from __future__ import annotations

import hashlib
import hmac
import os
import re

MIN_PEPPER_LENGTH = 16


def normalize_rut(rut: str) -> str:
    """Sin puntos, espacios ni guion y con DV en mayúscula: "12.345.678-k" → "12345678K" (igual que Java)."""
    return re.sub(r"[.\s-]", "", rut or "").upper()


def rut_is_valid(rut: str) -> bool:
    """Dígito verificador módulo 11 (serie 2..7). Un RUT inválido no se hashea: la fila se rechaza."""
    n = normalize_rut(rut)
    if not re.fullmatch(r"\d{1,8}[\dK]", n):
        return False
    body, dv = n[:-1], n[-1]
    total = sum(int(d) * (2 + i % 6) for i, d in enumerate(reversed(body)))
    expected = {11: "0", 10: "K"}.get(11 - total % 11, str(11 - total % 11))
    return dv == expected


class Pepper:
    """Secreto del HMAC. Local: PRIVACY_PEPPER. Nube: PRIVACY_PEPPER_PARAM (nombre del parámetro SSM)."""

    def __init__(self, value: str):
        if not value or len(value) < MIN_PEPPER_LENGTH:
            raise ValueError(f"El pepper debe tener al menos {MIN_PEPPER_LENGTH} caracteres")
        self._key = value.encode("utf-8")

    @classmethod
    def from_env(cls, env=os.environ, ssm_client=None) -> "Pepper":
        if env.get("PRIVACY_PEPPER"):
            return cls(env["PRIVACY_PEPPER"])
        name = env.get("PRIVACY_PEPPER_PARAM")
        if not name:
            raise ValueError("Falta PRIVACY_PEPPER (local) o PRIVACY_PEPPER_PARAM (SSM en la nube)")
        ssm = ssm_client or _ssm()
        return cls(ssm.get_parameter(Name=name, WithDecryption=True)["Parameter"]["Value"])

    def rut_hash(self, rut: str) -> str:
        """Mismo cálculo que IdentifierHasher.rut() en Java: HMAC-SHA256("rut:" + RUT normalizado), en hex."""
        return hmac.new(self._key, ("rut:" + normalize_rut(rut)).encode("utf-8"), hashlib.sha256).hexdigest()


def _ssm():  # pragma: no cover (solo en la nube)
    import boto3
    return boto3.client("ssm")
