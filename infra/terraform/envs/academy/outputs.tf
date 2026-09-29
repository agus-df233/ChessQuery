output "app_url" {
  description = "URL pública HTTPS (API Gateway). Agregar <app_url>/app como redirect URI de la SPA en Entra"
  value       = module.edge.app_url
}

output "web_bucket" { value = module.edge.web_bucket }
output "ecr_repositories" { value = { for k, r in aws_ecr_repository.svc : k => r.repository_url } }
output "ecs_cluster" { value = aws_ecs_cluster.this.name }
output "db_identifier" { value = module.data.db_identifier }
output "files_bucket" { value = module.data.files_bucket }
output "etl_bucket" { value = module.etl.bucket }
output "etl_functions" { value = module.etl.function_names }
