# Atajos del día a día. Todo corre en local; nada de esto toca una cuenta cloud.
#   make dev              LA APP COMPLETA en http://localhost:5173 (infra + servicios + ETL + web); Ctrl+C apaga todo
#   make local-up         infra local (Postgres, LocalStack SNS/SQS/S3, Mailpit)
#   make users            servicio users contra la infra local (requiere OIDC_ISSUER_URI/OIDC_AUDIENCE)
#   make tournament       servicio tournament (8082); necesita users corriendo
#   make game             servicio game (8083): partidas en línea; necesita users corriendo
#   make web              web en http://localhost:5173 (proxy /api → users)
#   make etl-fide-local   importa la lista FIDE real (CHI) y la publica en LocalStack
#   make federation-contract / federation-tournaments-local   Federación: esquema y torneos en vivo
#   make etl-bus-local       receptor SNS local: los eventos del bus llegan a las Lambdas del ETL como en la nube
#   make etl-docs         regenera el PDF de la guía del ETL desde docs/etl/*.md
#   make arquitectura-docs  regenera diagramas y PDF de docs/arquitectura/ (arquitectura, Terraform, ETL con Lambda)
#   make test             Java + ETL + web
#   make e2e              recorridos del jugador y del organizador en Chromium contra el stack local completo
#   make image            imagen OCI de users en el Docker local (Jib, arm64)
#   make tf-check         terraform fmt + validate de todos los entornos
#   make complexity       complejidad ciclomática ≤ 10 por función (lizard, Java + Python + TypeScript)

SHELL := /bin/bash
TF ?= terraform
COMPOSE := docker compose -f infra/docker-compose.yml
# Mismas variables que infra/.env.example: el SDK de AWS apunta a LocalStack sin cambios de código.
LOCAL_AWS := AWS_ENDPOINT_URL=http://localhost:4566 AWS_REGION=us-east-1 AWS_DEFAULT_REGION=us-east-1 \
             AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test

.PHONY: dev local-up local-down users tournament game web etl-setup etl-fide-local federation-contract federation-tournaments-local etl-bus-local etl-docs arquitectura-docs test test-java test-etl test-web e2e image tf-check complexity

# Stack completo con IdP simulado y Federación falsa (no necesita tenant de Entra). Ver scripts/dev.sh.
dev:
	bash scripts/dev.sh

local-up:
	$(COMPOSE) up -d --wait
	@echo "Postgres :5432 · LocalStack :4566 · Mailpit http://localhost:8025"

local-down:
	$(COMPOSE) down

users:
	mvn -B -ntp -q -DskipTests install
	cd services/users && $(LOCAL_AWS) mvn -B -ntp spring-boot:run

tournament:
	mvn -B -ntp -q -DskipTests install
	cd services/tournament && $(LOCAL_AWS) mvn -B -ntp spring-boot:run

game:
	mvn -B -ntp -q -DskipTests install
	cd services/game && $(LOCAL_AWS) mvn -B -ntp spring-boot:run

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

# Cuando un jugador vincula su id federativo, users publica federation.lookup.requested; esto consulta su ficha.
etl-bus-local: etl/.venv
	$(LOCAL_AWS) PRIVACY_PEPPER=dev-only-pepper-no-usar-en-cloud etl/.venv/bin/python -m chessquery_etl.local_bus

# PDF de la guía del ETL generado desde los Markdown (fuente única, también la leen los agentes).
etl-docs:
	uvx --with markdown python docs/etl/build-pdf.py

# Arquitectura: diagramas (definidos en código) + PDF desde docs/arquitectura/arquitectura-v3.md.
arquitectura-docs:
	cd docs/arquitectura && uvx --with markdown python build-pdf.py

test: test-java test-etl test-web

test-java:
	mvn -B -ntp clean verify

test-etl: etl/.venv
	cd etl && .venv/bin/pytest -q

test-web:
	npm run test -w web

# Levanta todo lo necesario (infra, servicios, receptor SNS del ETL, Federación falsa, web), corre Playwright y apaga.
e2e:
	bash scripts/e2e.sh

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
# Credenciales del lab en AWS_PROFILE (por defecto `default`). Orden (ver infra/terraform/README.md):
# academy-bootstrap (una vez) → academy-plan → academy-ecr → academy-image → academy-apply → academy-web.
# Las imágenes van ANTES del apply completo: ECS tiene rollback automático y un primer despliegue sin imagen queda
# fallido. Todo con el mismo IMAGE_TAG (el commit actual).
ACADEMY_PROFILE ?= default
ACADEMY_DIR := infra/terraform/envs/academy
ACADEMY_TF := AWS_PROFILE=$(ACADEMY_PROFILE) terraform -chdir=$(ACADEMY_DIR)
IMAGE_TAG ?= $(shell git rev-parse --short HEAD)
SERVICES := users tournament game
# Mismo tag para los tres servicios (se construyen juntos desde el mismo commit).
TAGS_VAR := -var 'image_tags={users="$(IMAGE_TAG)",tournament="$(IMAGE_TAG)",game="$(IMAGE_TAG)"}'

.PHONY: academy-bootstrap academy-init academy-plan academy-ecr academy-apply academy-image academy-web academy-down academy-destroy

academy-bootstrap:
	AWS_PROFILE=$(ACADEMY_PROFILE) terraform -chdir=infra/terraform/bootstrap init -input=false
	AWS_PROFILE=$(ACADEMY_PROFILE) terraform -chdir=infra/terraform/bootstrap apply -var bucket_via_cli=true

academy-init:
	$(ACADEMY_TF) init -input=false -reconfigure \
	  -backend-config="bucket=chessquery-tfstate-$$(AWS_PROFILE=$(ACADEMY_PROFILE) aws sts get-caller-identity --query Account --output text)"

academy-plan: academy-init
	$(ACADEMY_TF) plan -var-file=academy.tfvars $(TAGS_VAR)

# Solo los repositorios de imágenes (ECR), para poder subir las imágenes antes de crear los servicios.
academy-ecr: academy-init
	$(ACADEMY_TF) apply -var-file=academy.tfvars $(TAGS_VAR) -target=aws_ecr_repository.svc -target=aws_ecr_lifecycle_policy.svc

academy-apply: academy-init
	$(ACADEMY_TF) apply -var-file=academy.tfvars $(TAGS_VAR)

academy-image:
	repos=$$($(ACADEMY_TF) output -json ecr_repositories); \
	registry=$$(echo "$$repos" | python3 -c 'import sys,json; print(json.load(sys.stdin)["users"].split("/")[0])'); \
	AWS_PROFILE=$(ACADEMY_PROFILE) aws ecr get-login-password | docker login --username AWS --password-stdin "$$registry"; \
	mvn -B -ntp -q -DskipTests install || exit 1; \
	for svc in $(SERVICES); do \
	  repo=$$(echo "$$repos" | python3 -c "import sys,json; print(json.load(sys.stdin)['$$svc'])"); \
	  echo "== $$svc -> $$repo:$(IMAGE_TAG)"; \
	  mvn -B -ntp -q -pl services/$$svc jib:build -Djib.from.platforms=linux/amd64 -Dimage="$$repo:$(IMAGE_TAG)" || exit 1; \
	done

academy-web:
	npm run build -w web
	AWS_PROFILE=$(ACADEMY_PROFILE) aws s3 sync apps/web/dist "s3://$$($(ACADEMY_TF) output -raw web_bucket)" --delete
	@echo "Web publicada en $$($(ACADEMY_TF) output -raw app_url)"

academy-down:
	for svc in $(SERVICES); do \
	  AWS_PROFILE=$(ACADEMY_PROFILE) aws ecs update-service --cluster chessquery-academy --service $$svc --desired-count 0 >/dev/null; \
	done
	AWS_PROFILE=$(ACADEMY_PROFILE) aws rds stop-db-instance --db-instance-identifier chessquery-academy >/dev/null
	@echo "Servicios en 0 y RDS detenida (sin borrar nada)."

academy-destroy:
	$(ACADEMY_TF) destroy -var-file=academy.tfvars $(TAGS_VAR)
