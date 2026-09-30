# Bucket S3 con nombre fijo. Dos formas de crearlo:
#   - use_cli = false (cuenta propia): el recurso aws_s3_bucket de siempre.
#   - use_cli = true (Learner Lab): el lab niega con una política de la organización leer la configuración de
#     "object lock" (s3:GetBucketObjectLockConfiguration), y el recurso aws_s3_bucket la lee siempre al refrescar,
#     así que falla aunque el bucket se cree. Con use_cli el bucket se crea y se borra con la AWS CLI (que no hace
#     esa lectura); cifrado, acceso público, sitio web, políticas y ciclo de vida siguen siendo recursos de
#     Terraform normales en quien use este módulo (esos no leen object lock).
# La CLI usa las mismas credenciales que Terraform (AWS_PROFILE que exporta el Makefile).

variable "name" {
  description = "Nombre exacto del bucket (global en S3: incluir el id de la cuenta)"
  type        = string
}

variable "use_cli" {
  description = "true en el Learner Lab (ver arriba)"
  type        = bool
  default     = false
}

variable "force_destroy" {
  description = "Borrar el contenido al destruir el bucket"
  type        = bool
  default     = true
}

data "aws_region" "current" {}

resource "aws_s3_bucket" "this" {
  count         = var.use_cli ? 0 : 1
  bucket        = var.name
  force_destroy = var.force_destroy
}

resource "terraform_data" "cli" {
  count = var.use_cli ? 1 : 0
  input = { name = var.name, region = data.aws_region.current.region, force = var.force_destroy }

  # Idempotente: si el bucket ya es nuestro, no hace nada; si el nombre es de otra cuenta, head-bucket da 403 y
  # create-bucket falla con un error claro.
  provisioner "local-exec" {
    command = <<-EOT
      aws s3api head-bucket --bucket ${self.input.name} 2>/dev/null || \
      if [ "${self.input.region}" = "us-east-1" ]; then
        aws s3api create-bucket --bucket ${self.input.name}
      else
        aws s3api create-bucket --bucket ${self.input.name} --create-bucket-configuration LocationConstraint=${self.input.region}
      fi
    EOT
  }

  provisioner "local-exec" {
    when    = destroy
    command = self.input.force ? "aws s3 rb s3://${self.input.name} --force" : "aws s3 rb s3://${self.input.name}"
  }
}

output "id" {
  description = "Nombre del bucket (se conoce en el plan)"
  value       = var.use_cli ? terraform_data.cli[0].output.name : aws_s3_bucket.this[0].id
}

output "arn" { value = "arn:aws:s3:::${var.name}" }

output "regional_domain_name" { value = "${var.name}.s3.${data.aws_region.current.region}.amazonaws.com" }
