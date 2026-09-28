output "app_url" {
  description = "URL pública (agregar <app_url>/app como redirect URI de la SPA en Entra)"
  value       = "https://${module.edge.cloudfront_domain}"
}

output "web_bucket" { value = module.edge.web_bucket }
output "cloudfront_distribution_id" { value = module.edge.cloudfront_distribution_id }
output "ecr_repositories" { value = { for k, r in aws_ecr_repository.svc : k => r.repository_url } }
output "ecs_cluster" { value = aws_ecs_cluster.this.name }
output "db_identifier" { value = module.data.db_identifier }
output "files_bucket" { value = module.data.files_bucket }
