# RDS PostgreSQL (una instancia, un schema por servicio), bucket privado de archivos y parámetros
# SSM. La contraseña maestra la genera y rota RDS en Secrets Manager (`manage_master_user_password`):
# no aparece en el código, en el estado de Terraform ni en variables de entorno en texto plano.

variable "name" { type = string }
variable "subnet_ids" { type = list(string) }
variable "db_sg_id" { type = string }

variable "bucket_via_cli" {
  description = "true en el Learner Lab: crea el bucket con la AWS CLI (ver modules/s3-bucket)"
  type        = bool
  default     = false
}


variable "instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "deletion_protection" {
  type    = bool
  default = false
}

resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

resource "aws_db_instance" "this" {
  identifier                  = var.name
  engine                      = "postgres"
  engine_version              = "16"
  instance_class              = var.instance_class
  allocated_storage           = 20
  storage_type                = "gp3"
  storage_encrypted           = true
  db_name                     = "chessquery"
  username                    = "chessquery"
  manage_master_user_password = true
  db_subnet_group_name        = aws_db_subnet_group.this.name
  vpc_security_group_ids      = [var.db_sg_id]
  publicly_accessible         = false
  multi_az                    = false
  backup_retention_period     = 7
  deletion_protection         = var.deletion_protection
  skip_final_snapshot         = !var.deletion_protection
  final_snapshot_identifier   = var.deletion_protection ? "${var.name}-final" : null
  apply_immediately           = true
}

# ── Archivos (PGN, logos, comprobantes): privados, acceso por URL prefirmada ──
data "aws_caller_identity" "current" {}

module "files_bucket" {
  source        = "../s3-bucket"
  name          = "${var.name}-files-${data.aws_caller_identity.current.account_id}"
  use_cli       = var.bucket_via_cli
  force_destroy = !var.deletion_protection
}

resource "aws_s3_bucket_public_access_block" "files" {
  bucket                  = module.files_bucket.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "files" {
  bucket = module.files_bucket.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}

# ── Secreto compartido servicio→servicio (/internal/**) ───────────────────────
resource "random_password" "internal_token" {
  length  = 48
  special = false
}

resource "aws_ssm_parameter" "internal_token" {
  name  = "/${var.name}/internal-token"
  type  = "SecureString"
  value = random_password.internal_token.result
}

# Pepper del HMAC de identificadores personales (RUT, FIDE id, id federativo). Nunca se rota sin
# recalcular player.rut_hash y data_suppression: por eso ignore_changes sobre el valor.
resource "random_password" "privacy_pepper" {
  length  = 48
  special = false
}

resource "aws_ssm_parameter" "privacy_pepper" {
  name  = "/${var.name}/privacy-pepper"
  type  = "SecureString"
  value = random_password.privacy_pepper.result
  lifecycle {
    ignore_changes = [value]
  }
}

output "db_endpoint" { value = aws_db_instance.this.address }
output "db_name" { value = aws_db_instance.this.db_name }
output "db_username" { value = aws_db_instance.this.username }
output "db_identifier" { value = aws_db_instance.this.identifier }
output "db_master_secret_arn" { value = aws_db_instance.this.master_user_secret[0].secret_arn }
output "files_bucket" { value = module.files_bucket.id }
output "internal_token_param_arn" { value = aws_ssm_parameter.internal_token.arn }

# Lo necesita la integración de API Gateway WebSocket, que llama a /internal/ws/* de game
output "internal_token" {
  value     = random_password.internal_token.result
  sensitive = true
}
output "privacy_pepper_param_arn" { value = aws_ssm_parameter.privacy_pepper.arn }
output "privacy_pepper_param_name" { value = aws_ssm_parameter.privacy_pepper.name }
