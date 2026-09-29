# Atajos del día a día. Todo corre en local; nada de esto toca una cuenta cloud.
#   make local-up         infra local (Postgres, LocalStack SNS/SQS/S3, Mailpit)
#   make users            servicio users contra la infra local (requiere OIDC_ISSUER_URI/OIDC_AUDIENCE)
#   make web              web en http://localhost:5173 (proxy /api → users)
#   make etl-fide-local   importa la lista FIDE real (CHI) y la publica en LocalStack
#   make federation-contract / federation-tournaments-local   Federación: esquema y torneos en vivo
#   make etl-docs         regenera el PDF de la guía del ETL desde docs/etl/*.md
#   make test             Java + ETL + web
#   make image            imagen OCI de users en el Docker local (Jib, arm64)
#   make tf-check         terraform fmt + validate de todos los entornos
#   make complexity       complejidad ciclomática ≤ 10 por función (lizard, Java + Python + TypeScript)

SHELL := /bin/bash
TF ?= terraform
COMPOSE := docker compose -f infra/docker-compose.yml
# Mismas variables que infra/.env.example: el SDK de AWS apunta a LocalStack sin cambios de código.
LOCAL_AWS := AWS_ENDPOINT_URL=http://localhost:4566 AWS_REGION=us-east-1 AWS_DEFAULT_REGION=us-east-1 \
             AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test

.PHONY: local-up local-down users web etl-setup etl-fide-local federation-contract federation-tournaments-local etl-docs test test-java test-etl test-web image tf-check complexity

local-up:
	$(COMPOSE) up -d --wait
	@echo "Postgres :5432 · LocalStack :4566 · Mailpit http://localhost:8025"

local-down:
	$(COMPOSE) down

users:
	mvn -B -ntp -q -DskipTests install
	cd services/users && $(LOCAL_AWS) mvn -B -ntp spring-boot:run

web:
	npm run dev -w web

etl/.venv:
	python3 -m venv etl/.venv && etl/.venv/bin/pip install -q -e 'etl[dev]'

etl-setup: etl/.venv

etl-fide-local: etl/.venv
	$(LOCAL_AWS) etl/.venv/bin/python -m chessquery_etl.handler $(if $(FILE),--file $(FILE),)

# Federación: el contrato solo lee el esquema; los torneos se publican en LocalStack (make local-up antes).
federation-contract: etl/.venv
	etl/.venv/bin/python -m chessquery_etl.federation.cli contract

federation-tournaments-local: etl/.venv
	$(LOCAL_AWS) PRIVACY_PEPPER=dev-only-pepper-no-usar-en-cloud etl/.venv/bin/python -m chessquery_etl.federation.cli tournaments

# PDF de la guía del ETL generado desde los Markdown (fuente única, también la leen los agentes).
etl-docs:
	uvx --with markdown python docs/etl/build-pdf.py

test: test-java test-etl test-web

test-java:
	mvn -B -ntp clean verify

test-etl: etl/.venv
	cd etl && .venv/bin/pytest -q

test-web:
	npm run test -w web

image:
	mvn -B -ntp -q -pl services/users -am -DskipTests package jib:dockerBuild -Djib.from.platforms=linux/arm64

# Umbral del equipo: CCN ≤ 10 por función; 100 líneas como techo (en JSX el largo es markup, no lógica).
# Player.java se excluye: lizard confunde sus anotaciones JPA con una función (falso positivo).
complexity:
	uvx lizard libs/*/src/main services/*/src/main etl/chessquery_etl apps/web/src packages/ui-lib/src \
	  -x "*.spec.*" -x "*/player/Player.java" -C 10 -L 100 -w

tf-check:
	$(TF) fmt -check -recursive infra/terraform
	@for d in infra/terraform/bootstrap infra/terraform/envs/*/; do \
	  echo "== $$d"; $(TF) -chdir=$$d init -backend=false -input=false >/dev/null && $(TF) -chdir=$$d validate || exit 1; \
	done

# ── Despliegue al Learner Lab (lo ejecuta una persona; el agente solo prepara y hace plan) ──────────────
# Credenciales del lab en AWS_PROFILE (por defecto `default`). Orden: academy-bootstrap (una vez) →
# academy-apply con IMAGE_TAG → academy-image → academy-web. Ver infra/terraform/README.md.
ACADEMY_PROFILE ?= default
ACADEMY_DIR := infra/terraform/envs/academy
ACADEMY_TF := AWS_PROFILE=$(ACADEMY_PROFILE) terraform -chdir=$(ACADEMY_DIR)
IMAGE_TAG ?= $(shell git rev-parse --short HEAD)

.PHONY: academy-bootstrap academy-init academy-plan academy-apply academy-image academy-web academy-down academy-destroy

academy-bootstrap:
	AWS_PROFILE=$(ACADEMY_PROFILE) terraform -chdir=infra/terraform/bootstrap init -input=false
	AWS_PROFILE=$(ACADEMY_PROFILE) terraform -chdir=infra/terraform/bootstrap apply

academy-init:
	$(ACADEMY_TF) init -input=false -reconfigure \
	  -backend-config="bucket=chessquery-tfstate-$$(AWS_PROFILE=$(ACADEMY_PROFILE) aws sts get-caller-identity --query Account --output text)"

academy-plan: academy-init
	$(ACADEMY_TF) plan -var-file=academy.tfvars -var 'image_tags={users="$(IMAGE_TAG)"}'

academy-apply: academy-init
	$(ACADEMY_TF) apply -var-file=academy.tfvars -var 'image_tags={users="$(IMAGE_TAG)"}'

academy-image:
	AWS_PROFILE=$(ACADEMY_PROFILE) aws ecr get-login-password | docker login --username AWS --password-stdin \
	  "$$($(ACADEMY_TF) output -json ecr_repositories | python3 -c 'import sys,json; print(json.load(sys.stdin)["users"].split("/")[0])')"
	mvn -B -ntp -q -pl services/users -am -DskipTests package jib:build -Djib.from.platforms=linux/amd64 \
	  -Dimage="$$($(ACADEMY_TF) output -json ecr_repositories | python3 -c 'import sys,json; print(json.load(sys.stdin)["users"])'):$(IMAGE_TAG)"

academy-web:
	npm run build -w web
	AWS_PROFILE=$(ACADEMY_PROFILE) aws s3 sync apps/web/dist "s3://$$($(ACADEMY_TF) output -raw web_bucket)" --delete
	@echo "Web publicada en $$($(ACADEMY_TF) output -raw app_url)"

academy-down:
	AWS_PROFILE=$(ACADEMY_PROFILE) aws ecs update-service --cluster chessquery-academy --service users --desired-count 0 >/dev/null
	AWS_PROFILE=$(ACADEMY_PROFILE) aws rds stop-db-instance --db-instance-identifier chessquery-academy >/dev/null
	@echo "Servicio en 0 y RDS detenida (sin borrar nada)."

academy-destroy:
	$(ACADEMY_TF) destroy -var-file=academy.tfvars -var 'image_tags={users="$(IMAGE_TAG)"}'
