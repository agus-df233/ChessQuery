# Bucket del estado remoto de Terraform. Se aplica una sola vez por cuenta, con estado local:
#   terraform -chdir=infra/terraform/bootstrap init && terraform -chdir=infra/terraform/bootstrap apply
# El locking usa `use_lockfile` (S3 nativo, Terraform >= 1.10): no hace falta DynamoDB.

terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.0" }
  }
}

variable "region" {
  type    = string
  default = "us-east-1"
}

provider "aws" {
  region = var.region
}

data "aws_caller_identity" "current" {}

variable "bucket_via_cli" {
  description = "true en el Learner Lab: el lab niega leer object lock y el bucket se crea con la AWS CLI"
  type        = bool
  default     = false
}

# El estado nunca se borra con el bucket: force_destroy = false.
module "state_bucket" {
  source        = "../modules/s3-bucket"
  name          = "chessquery-tfstate-${data.aws_caller_identity.current.account_id}"
  use_cli       = var.bucket_via_cli
  force_destroy = false
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = module.state_bucket.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = module.state_bucket.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = module.state_bucket.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

output "state_bucket" {
  value = module.state_bucket.id
}
