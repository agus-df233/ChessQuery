# Íconos de los diagramas

Los usa `docs/arquitectura/diagramas.py`, que los incrusta en cada SVG (base64): los diagramas quedan autocontenidos
y se ven igual en el navegador, en el Markdown y en el PDF.

**Origen:** se copiaron una sola vez del paquete [`diagrams`](https://github.com/mingrammer/diagrams) 0.25.1 (licencia
MIT) y se redujeron a 96 px con `sips -Z 96`. No se usa Graphviz ni ninguna dependencia al generar los diagramas.

| Archivos | Qué son | Condiciones de uso |
|---|---|---|
| `aws-*.png` | AWS Architecture Icons (oficiales) | AWS permite usarlos para dibujar diagramas de arquitectura |
| `azure-*.png` | Íconos oficiales de Microsoft Azure (Entra External ID) | Microsoft permite usarlos en diagramas y documentación de arquitectura |
| `github-actions.png`, `terraform.png`, `react.png`, `spring.png`, `python.png` | Logos de herramientas | Marcas de sus dueños; solo identifican la herramienta |

**Para agregar uno:** copiarlo desde `resources/` del paquete `diagrams`
(`uvx --from diagrams python -c "import diagrams, os; print(os.path.dirname(diagrams.__file__))"`), reducirlo con
`sips -Z 96` y nombrarlo con el prefijo del proveedor.
