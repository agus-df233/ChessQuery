"""Ingesta de la Federación Chilena de Ajedrez (jugadores y torneos).

Fuente: el endpoint GraphQL público del sitio (``/graphql``). No es una API oficial: por eso todo pasa por
un cliente con límites (``client.py``), un chequeo de contrato antes de bajar datos (``contract.py``) y
validación fila a fila (``validation.py``). La descarga masiva de personas está apagada por defecto hasta
contar con convenio (``FEDERATION_BULK_PLAYERS_ENABLED``). Guía completa: docs/etl/federacion.md.
"""
