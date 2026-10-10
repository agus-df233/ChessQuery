variable "image_tags" {
  description = "Tag de imagen en ECR por servicio (p. ej. sha corto del commit)"
  type        = map(string)
}

variable "use_spot" {
  description = "true = FARGATE_SPOT (barato); false el día de una demo (sin interrupciones)"
  type        = bool
  default     = true
}

variable "auth_provider" {
  description = "IdP del login: cognito (user pool con Google, por defecto en el lab) o entra (tenant de External ID)"
  type        = string
  default     = "cognito"
  validation {
    condition     = contains(["cognito", "entra"], var.auth_provider)
    error_message = "auth_provider debe ser cognito o entra."
  }
}

variable "google_client_id" {
  description = "Cognito: client ID del cliente OAuth de Google (no es secreto)"
  type        = string
  default     = ""
}

variable "google_client_secret" {
  description = "Cognito: secreto de Google. Lo pone el Makefile desde el llavero (TF_VAR_google_client_secret)"
  type        = string
  default     = ""
  sensitive   = true
}

variable "oidc_issuer_uri" {
  description = "Solo con auth_provider = entra: https://<tenant>.ciamlogin.com/<tenant-id>/v2.0"
  type        = string
  default     = ""
}

variable "oidc_audience" {
  description = "Solo con auth_provider = entra: client id de la app registration de la API"
  type        = string
  default     = ""
}

variable "alert_email" {
  description = "Destino de las alarmas de CloudWatch (hay que confirmar la suscripción por email)"
  type        = string
}
