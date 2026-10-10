output "app_url" {
  description = "URL pública HTTPS (API Gateway). <app_url>/app ya es callback del cliente de Cognito"
  value       = module.edge.app_url
}

output "web_bucket" { value = module.edge.web_bucket }

output "ws_url" {
  description = "WebSocket de las partidas en vivo: make academy-web lo inyecta como VITE_WS_URL"
  value       = module.realtime.ws_url
}
output "alb_dns_name" {
  description = "ALB (sin TLS): solo lo usa make cloud-smoke para verificar que sin la cabecera de origen responde 404"
  value       = module.alb.dns_name
}
output "ecr_repositories" { value = { for k, r in aws_ecr_repository.svc : k => r.repository_url } }
output "ecs_cluster" { value = aws_ecs_cluster.this.name }
output "db_identifier" { value = module.data.db_identifier }
output "files_bucket" { value = module.data.files_bucket }
output "etl_bucket" { value = module.etl.bucket }
output "etl_functions" { value = module.etl.function_names }

output "auth_provider" { value = var.auth_provider }

output "oidc_issuer" {
  description = "Issuer que validan los servicios (y authority de la web)"
  value       = local.oidc_issuer
}

output "oidc_client_id" {
  description = "Cognito: client id de la web (también la audiencia del ID token)"
  value       = local.cognito ? module.auth[0].client_id : ""
}

output "auth_domain_url" {
  description = "Cognito: dominio del login; en Google va <auth_domain_url>/oauth2/idpresponse como redirect URI"
  value       = local.cognito ? module.auth[0].domain_url : ""
}
