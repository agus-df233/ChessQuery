# Glosario del ETL

| Término | Significado en ChessQuery |
|---|---|
| **Fuente** | Origen externo de datos: FIDE, la Federación, Lichess, Chess.com. |
| **Federado / no federado** | Jugador inscrito en la Federación (tiene id federativo y ELO nacional) o no. Los no federados son la mayoría y no aparecen en ninguna lista externa. |
| **Período** | Mes al que corresponde una lista de ratings, en formato `AAAA-MM`. |
| **Lista blanca** | Campos que sí pedimos a una fuente. Lo que no está en la lista no se descarga. |
| **Minimización** | Guardar y publicar solo lo necesario: año en vez de fecha de nacimiento, hash en vez de RUT. |
| **Hash con pepper (`rutHash`)** | HMAC-SHA256 del RUT normalizado con un secreto (pepper) que vive fuera de la base. Permite comparar RUT sin guardarlos en claro. |
| **Supresión** | Derecho de una persona a que borremos sus datos. Sus identificadores quedan en `data_suppression` para que ninguna fuente los vuelva a importar. |
| **Consulta puntual (consentida)** | Traer la ficha de una sola persona porque ella misma lo pidió (al vincular su id federativo). |
| **Flag de masivo** | `FEDERATION_BULK_PLAYERS_ENABLED`: habilita la descarga de todas las personas. Apagado hasta el convenio. |
| **Contrato** | Forma acordada de un evento (en `docs/events.md`) o del esquema de la fuente (en `contract.py`). Romperlo rompe a otros. |
| **Staged** | Copia validada y minimizada de una corrida, guardada en S3 para compararla con la próxima. |
| **Diff** | Comparación contra la corrida anterior: solo se publica lo nuevo o modificado. |
| **Lote** | Grupo de hasta 200 registros por mensaje SNS (el límite de un mensaje es 256 KB). |
| **Manifest** | Resumen de una corrida: leídos, aceptados, rechazados, cambiados, lotes. |
| **Rechazo** | Registro que no pasó una regla de validación. Se guarda solo con id y motivo. |
| **Umbral de rechazos** | Proporción máxima de rechazos (5 %) antes de detener la corrida sin publicar. |
| **Idempotencia** | Procesar dos veces el mismo evento no cambia el resultado (`processed_event` en `users`). |
| **DLQ** | Cola de mensajes que fallaron 5 veces; si recibe algo, suena una alarma. |
| **Circuit breaker** | Corte automático tras varios fallos seguidos para no insistir contra un sitio caído. |
| **Envelope** | Sobre común de todo evento: `eventId`, `eventType`, `timestamp`, `payload`. |
