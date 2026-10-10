# Documentación de ChessQuery

| Documento | Para qué |
|---|---|
| `arquitectura/arquitectura-v3.md` (+ PDF) | **Empezar acá:** para las partes interesadas (problema, valor, guion de la demo), arquitectura en AWS, jugar y organizar, eventos, Terraform y ETL (`make arquitectura-docs`) |
| `adr/0001-arquitectura-v3.md` · `adr/0002-despliegue-aws-bajo-costo.md` | Las decisiones y su porqué, con las enmiendas del Learner Lab (entrada, tiempo real, ETL, login con Cognito) |
| `events.md` | Catálogo de eventos entre servicios: **fuente de verdad**, se edita antes de codificar |
| `auth/cognito-google.md` | Login con Google: cliente OAuth, secreto en el llavero y prueba local con `make dev-idp` |
| `etl/onboarding.md` · `etl/federacion.md` · `etl/nueva-fuente.md` · `etl/glosario.md` | El ETL: por dónde empezar, la Federación, cómo sumar una fuente y términos (PDF con `make etl-docs`) |
| `verificacion/plan-de-pruebas.md` | Qué se prueba (caja blanca, caja negra, seguridad, integración, tiempos) y cómo |
| `verificacion/flujos-e2e.md` | Los recorridos verificados en el navegador, paso a paso, y su último resultado |
| `verificacion/auditoria-ux.md` | Auditoría de experiencia vista por vista (galería en 375, 768 y 1280 px) |
| `verificacion/nube-aceptacion.md` | Checklist de aceptación en la nube, rol por rol |
| `verificacion/benchmark-v2-vs-v3.md` (+ `.json`) | Rendimiento del servicio de jugadores, v2 contra v3 (28-09-2026) |
