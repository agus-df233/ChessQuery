"""Convierte `terraform output -json` del Learner Lab en las variables VITE_OIDC_* de la web (export para `eval`).

Solo con el login de Cognito: con Entra no imprime nada y la web usa apps/web/.env. Los valores no son secretos
(issuer, client id público y dominio del login); se citan para el shell.
"""
from __future__ import annotations

import json
import shlex
import sys


def exports(outputs: dict) -> list[str]:
    value = {k: v.get("value", "") for k, v in outputs.items()}
    # Tras un apply parcial (make academy-auth) puede faltar `auth_provider`: basta con que exista el cliente de Cognito
    if value.get("auth_provider", "cognito") != "cognito" or not value.get("oidc_client_id"):
        return []
    env = {
        "VITE_OIDC_PROVIDER": "cognito",
        "VITE_OIDC_AUTHORITY": value["oidc_issuer"],
        "VITE_OIDC_CLIENT_ID": value["oidc_client_id"],
        "VITE_OIDC_SCOPE": "openid email profile",
        "VITE_OIDC_LOGOUT_URL": f"{value['auth_domain_url']}/logout",
    }
    return [f"export {k}={shlex.quote(v)}" for k, v in env.items()]


if __name__ == "__main__":
    print("\n".join(exports(json.load(sys.stdin))))
