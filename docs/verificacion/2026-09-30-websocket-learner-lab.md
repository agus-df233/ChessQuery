# ¿Funciona API Gateway WebSocket API en el Learner Lab? — Sí (30-09-2026)

Prueba desechable para decidir si el tablero 1 vs 1 puede usar WebSocket en el Learner Lab (hoy usa long polling).
Los otros caminos estaban descartados: WebSocket directo al ALB exige `wss://` y el ALB no tiene certificado (sin
dominio propio), y AppSync está bloqueado en el lab.

## Qué se probó y resultado

| Paso | Resultado |
|---|---|
| Crear una WebSocket API (rutas `$connect` y `$default`, stage con auto-deploy) | ✅ permitido |
| Conectarse desde fuera de AWS por `wss://…execute-api.us-east-1.amazonaws.com/<stage>` | ✅ conexión aceptada |
| `$connect` y `$default` atendidos por una Lambda (integración `AWS_PROXY`, método POST) | ✅ la ruta `$default` devolvió el `connectionId` |
| **Empujar un mensaje desde el `LabRole`** (`apigatewaymanagementapi.post_to_connection`) | ✅ el cliente recibió `{"jugada":"e2e4","desde":"LabRole"}` |
| Limpieza (API, 2 Lambdas y sus logs) | ✅ nada quedó; la API de la app no se tocó |

**Conclusión:** el `LabRole` (el rol de las tasks de ECS) tiene el permiso `execute-api:ManageConnections`, así que el
servicio `game` puede avisar cada jugada por WebSocket a través de API Gateway en el lab.

## Aprendizajes para la implementación

- **Integraciones simuladas (MOCK) en `$connect`:** respondieron 500 sin detalle; con una Lambda (o una integración
  HTTP) funciona. Sin logs de ejecución de API Gateway (piden un rol de CloudWatch a nivel de cuenta, y la cuenta del lab
  es compartida) los errores solo dicen "Internal server error".
- **Esperar la propagación** tras cambiar rutas o integraciones: la primera conexión, 5 s después del cambio, falló.
- La `template-selection-expression` se escribe literalmente `\$default` (la API rechaza `$default`).
- El tiempo medido (3,7 s) incluye invocar una Lambda en frío desde un computador; no es la latencia real de una jugada.

## Siguiente paso

Implementar el WebSocket en el lab según el plan aprobado aparte: API WebSocket en Terraform, conexiones por partida en
el schema de `game`, envío desde `GameNotifier` y `useLiveGame` con WebSocket y vuelta a long polling si falla. En la
cuenta propia se puede usar lo mismo o AppSync Events.
