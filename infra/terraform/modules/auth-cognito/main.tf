# Login de ChessQuery con Google a través de Amazon Cognito (ADR-0002, enmienda 2026-10-09: sin tenant de Entra
# disponible, el IdP del Learner Lab es un user pool de Cognito con Google federado).
#
#   web ──Authorization Code + PKCE (identity_provider=Google)──► dominio de Cognito ──► Google ──► Cognito ──► web
#   web ──Authorization: Bearer <ID token>──► servicios (validan iss del pool y aud = client id de la web)
#
# Se envía el ID token y no el access token porque solo el ID token de Cognito trae `aud`, `email` y el nombre en el
# plan Lite (agregarlos al access token exige un plan pagado). Es el mismo token que acepta el autorizador de Cognito
# de API Gateway. No hay contraseñas: el cliente solo admite Google y el autorregistro con contraseña está apagado
# (los usuarios federados igual se crean en su primer ingreso).

variable "name" { type = string }

variable "domain_prefix" {
  description = "Prefijo del dominio de login: <prefijo>.auth.<región>.amazoncognito.com (único en la región)"
  type        = string
}

variable "callback_urls" {
  description = "Dónde vuelve la web tras el login (exactas, con esquema y ruta)"
  type        = list(string)
}

variable "logout_urls" { type = list(string) }

variable "google_client_id" {
  description = "Client ID del cliente OAuth de Google (no es secreto)"
  type        = string
}

variable "google_client_secret" {
  description = "Secreto del cliente OAuth de Google: llega por TF_VAR_google_client_secret, nunca desde el repo"
  type        = string
  sensitive   = true
}

data "aws_region" "current" {}

resource "aws_cognito_user_pool" "this" {
  name           = var.name
  user_pool_tier = "LITE"

  # Sin registro con contraseña: solo entran usuarios federados (Google)
  admin_create_user_config {
    allow_admin_create_user_only = true
  }
  username_configuration {
    case_sensitive = false
  }
  deletion_protection = "INACTIVE" # el lab se recrea con apply/destroy
}

resource "aws_cognito_user_pool_domain" "this" {
  domain       = var.domain_prefix
  user_pool_id = aws_cognito_user_pool.this.id
}

resource "aws_cognito_identity_provider" "google" {
  user_pool_id  = aws_cognito_user_pool.this.id
  provider_name = "Google"
  provider_type = "Google"

  provider_details = {
    client_id        = var.google_client_id
    client_secret    = var.google_client_secret
    authorize_scopes = "openid email profile"
  }

  # Cognito completa solo estos datos de Google al crear el proveedor; sin esto, cada plan querría borrarlos
  lifecycle {
    ignore_changes = [
      provider_details["attributes_url"], provider_details["attributes_url_add_attributes"],
      provider_details["authorize_url"], provider_details["oidc_issuer"],
      provider_details["token_request_method"], provider_details["token_url"],
    ]
  }

  # Lo que Google entrega pasa al ID token que leen los servicios (correo verificado y nombre)
  attribute_mapping = {
    email          = "email"
    email_verified = "email_verified"
    given_name     = "given_name"
    family_name    = "family_name"
    username       = "sub"
  }
}

resource "aws_cognito_user_pool_client" "web" {
  name         = "${var.name}-web"
  user_pool_id = aws_cognito_user_pool.this.id

  # Cliente público (SPA): sin secreto, Authorization Code + PKCE
  generate_secret                      = false
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = [aws_cognito_identity_provider.google.provider_name]
  callback_urls                        = var.callback_urls
  logout_urls                          = var.logout_urls
  explicit_auth_flows                  = ["ALLOW_REFRESH_TOKEN_AUTH"]
  read_attributes                      = ["email", "email_verified", "given_name", "family_name"]

  prevent_user_existence_errors = "ENABLED"
  enable_token_revocation       = true
  id_token_validity             = 60
  access_token_validity         = 60
  refresh_token_validity        = 30
  token_validity_units {
    id_token      = "minutes"
    access_token  = "minutes"
    refresh_token = "days"
  }
}

output "issuer" {
  description = "OIDC_ISSUER_URI de los servicios y authority de la web"
  value       = "https://cognito-idp.${data.aws_region.current.region}.amazonaws.com/${aws_cognito_user_pool.this.id}"
}

output "client_id" {
  description = "Client id de la web: es también la audiencia (aud del ID token) que validan los servicios"
  value       = aws_cognito_user_pool_client.web.id
}

output "domain_url" {
  description = "Dominio del login de Cognito (Google redirige a <domain_url>/oauth2/idpresponse)"
  value       = "https://${aws_cognito_user_pool_domain.this.domain}.auth.${data.aws_region.current.region}.amazoncognito.com"
}
