variable "image_tags" {
  description = "Tag de imagen en ECR por servicio (p. ej. sha corto del commit)"
  type        = map(string)
}

variable "use_spot" {
  description = "true = FARGATE_SPOT (barato); false el día de una demo (sin interrupciones)"
  type        = bool
  default     = true
}

variable "oidc_issuer_uri" {
  description = "Issuer de Entra External ID: https://<tenant>.ciamlogin.com/<tenant-id>/v2.0"
  type        = string
}

variable "oidc_audience" {
  description = "Client id de la app registration de la API"
  type        = string
}

variable "alert_email" {
  description = "Destino de las alarmas de CloudWatch (hay que confirmar la suscripción por email)"
  type        = string
}
