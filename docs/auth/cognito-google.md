# Login con Google por Amazon Cognito (Learner Lab)

Por qué Cognito y no Entra: ADR-0002, enmienda del 09-10-2026. Resumen del flujo:

- la web va a Google por el dominio de Cognito;
- Cognito devuelve el **ID token** (con `aud`, `email` y nombre);
- la web lo envía como `Bearer` a los servicios, que validan el issuer del user pool y `aud` = client id.

Terraform crea el user pool, el dominio, Google como proveedor y el cliente de la web (módulo `auth-cognito`). Solo
dos cosas se hacen a mano: el cliente OAuth en Google Cloud y guardar su secreto en el llavero.

## 1. Cliente OAuth en Google Cloud

El dominio de login de Cognito es `chessquery-<id de la cuenta AWS>`. Hoy es
`https://chessquery-176441580669.auth.us-east-1.amazoncognito.com`. Se conoce antes de crearlo, así que este paso va
primero.

1. https://console.cloud.google.com → **New Project** → `ChessQuery`.
2. **APIs & Services → OAuth consent screen** (o *Google Auth Platform*):
   - tipo **External**;
   - nombre de la app `ChessQuery`, correo de soporte y contacto del desarrollador;
   - en **Authorized domains**: `amazoncognito.com`;
   - antes de la demo: **Publish app** («In production»). En «Testing» solo entran los *test users* agregados.
3. **Credentials → Create credentials → OAuth client ID → Web application** (`cognito-chessquery`):
   - **Authorized JavaScript origins:** `https://chessquery-176441580669.auth.us-east-1.amazoncognito.com`
   - **Authorized redirect URIs:** `https://chessquery-176441580669.auth.us-east-1.amazoncognito.com/oauth2/idpresponse`
4. Copiar el **Client ID** (no es secreto) a `infra/terraform/envs/academy/academy.tfvars`:
   `google_client_id = "<…>.apps.googleusercontent.com"`.
5. Guardar el **Client secret** solo en el llavero de macOS. El comando lo pide sin mostrarlo:
   ```bash
   security add-generic-password -a chessquery -s chessquery-google-oauth -w
   ```
   El Makefile lo pasa a Terraform como `TF_VAR_google_client_secret`. Nunca va al repo ni a un archivo.

## 2. Crear el login y probarlo en local

```bash
make academy-bootstrap   # una vez por cuenta: bucket del estado (lo ejecuta una persona)
make academy-auth        # solo el login (Cognito + la URL de la app para el callback)
make dev-idp             # app local completa contra ese login → http://localhost:5173 → «Continuar con Google»
```

Debe terminar en «Hola, <nombre>» con el correo de la cuenta Google. Si algo responde 401, pegar el ID token en
https://jwt.ms y revisar:

- `iss`: `https://cognito-idp.us-east-1.amazonaws.com/<pool>`;
- `aud`: el client id de la web (`terraform output oidc_client_id`);
- `email`, `given_name` y `family_name` presentes.

## 3. En la nube

`make academy-apply` y luego `make academy-web`: la web toma el login de las salidas de Terraform (`scripts/oidc_env.py`).
No hace falta tocar Google ni Cognito, porque `<app_url>/app` ya está registrada como callback del cliente.
