# VPC de dos AZ con subnets públicas únicamente: sin NAT Gateway (US$33/mes). Las tasks reciben
# IP pública, pero sus security groups solo aceptan tráfico desde el ALB; RDS no es accesible
# públicamente y solo admite conexiones desde las tasks.

variable "name" { type = string }

variable "alb_ingress" {
  description = "cloudfront = solo desde CloudFront (cuenta propia); public = desde internet, protegido por la cabecera secreta del módulo alb (Academy, detrás de API Gateway)"
  type        = string
  default     = "cloudfront"
  validation {
    condition     = contains(["cloudfront", "public"], var.alb_ingress)
    error_message = "alb_ingress debe ser cloudfront o public."
  }
}

variable "cidr" {
  type    = string
  default = "10.40.0.0/16"
}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
}

resource "aws_vpc" "this" {
  cidr_block           = var.cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags                 = { Name = var.name }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = var.name }
}

resource "aws_subnet" "public" {
  count                   = length(local.azs)
  vpc_id                  = aws_vpc.this.id
  availability_zone       = local.azs[count.index]
  cidr_block              = cidrsubnet(var.cidr, 8, count.index)
  map_public_ip_on_launch = true
  tags                    = { Name = "${var.name}-public-${local.azs[count.index]}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }
  tags = { Name = "${var.name}-public" }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# ── Security groups ─────────────────────────────────────────────────────────
# Cuenta propia: el ALB solo acepta tráfico desde CloudFront (prefix list gestionada por AWS).
# Academy: API Gateway no tiene rangos fijos, así que el puerto 80 queda abierto y la protección es la
# cabecera X-Origin-Verify que exige el listener (módulo alb).
data "aws_ec2_managed_prefix_list" "cloudfront" {
  count = var.alb_ingress == "cloudfront" ? 1 : 0
  name  = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name        = "${var.name}-alb"
  description = "ALB: HTTP desde el borde (${var.alb_ingress})"
  vpc_id      = aws_vpc.this.id

  ingress {
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    prefix_list_ids = var.alb_ingress == "cloudfront" ? [data.aws_ec2_managed_prefix_list.cloudfront[0].id] : null
    cidr_blocks     = var.alb_ingress == "public" ? ["0.0.0.0/0"] : null
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "services" {
  name        = "${var.name}-services"
  description = "Tasks ECS: entrada desde el ALB y entre servicios"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "Desde el ALB"
    from_port       = 8080
    to_port         = 8099
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description = "Servicio a servicio (/internal/**)"
    from_port   = 8080
    to_port     = 8099
    protocol    = "tcp"
    self        = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "db" {
  name        = "${var.name}-db"
  description = "RDS: solo desde las tasks"
  vpc_id      = aws_vpc.this.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.services.id]
  }
}

output "vpc_id" { value = aws_vpc.this.id }
output "public_subnet_ids" { value = aws_subnet.public[*].id }
output "alb_sg_id" { value = aws_security_group.alb.id }
output "services_sg_id" { value = aws_security_group.services.id }
output "db_sg_id" { value = aws_security_group.db.id }
