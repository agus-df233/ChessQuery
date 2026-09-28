# Atajos del día a día. Todo corre en local; nada de esto toca una cuenta cloud.
#   make local-up         infra local (Postgres, LocalStack SNS/SQS/S3, Mailpit)
#   make users            servicio users contra la infra local (requiere OIDC_ISSUER_URI/OIDC_AUDIENCE)
#   make web              web en http://localhost:5173 (proxy /api → users)
#   make etl-fide-local   importa la lista FIDE real (CHI) y la publica en LocalStack
#   make test             Java + ETL + web
#   make image            imagen OCI de users en el Docker local (Jib, arm64)
#   make tf-check         terraform fmt + validate de todos los entornos

SHELL := /bin/bash
TF ?= terraform
COMPOSE := docker compose -f infra/docker-compose.yml
# Mismas variables que infra/.env.example: el SDK de AWS apunta a LocalStack sin cambios de código.
LOCAL_AWS := AWS_ENDPOINT_URL=http://localhost:4566 AWS_REGION=us-east-1 AWS_DEFAULT_REGION=us-east-1 \
             AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test

.PHONY: local-up local-down users web etl-setup etl-fide-local test test-java test-etl test-web image tf-check

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

test: test-java test-etl test-web

test-java:
	mvn -B -ntp clean verify

test-etl: etl/.venv
	cd etl && .venv/bin/pytest -q

test-web:
	npm run test -w web

image:
	mvn -B -ntp -q -pl services/users -am -DskipTests package jib:dockerBuild -Djib.from.platforms=linux/arm64

tf-check:
	$(TF) fmt -check -recursive infra/terraform
	@for d in infra/terraform/bootstrap infra/terraform/envs/*/; do \
	  echo "== $$d"; $(TF) -chdir=$$d init -backend=false -input=false >/dev/null && $(TF) -chdir=$$d validate || exit 1; \
	done
