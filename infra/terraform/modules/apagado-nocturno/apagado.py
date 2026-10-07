"""Apagado nocturno del entorno Academy: deja los servicios de ECS en 0 tareas y detiene la base RDS.

Hace lo mismo que `make academy-down`, pero solo (regla de EventBridge), para que un olvido no se coma el crédito
del Learner Lab. No borra nada: `make academy-up` lo vuelve a encender.
"""
import os

import boto3


def lambda_handler(_event, _context):
    cluster, db = os.environ["CLUSTER"], os.environ["DB_IDENTIFIER"]
    ecs, rds = boto3.client("ecs"), boto3.client("rds")
    services = ecs.list_services(cluster=cluster).get("serviceArns", [])
    for arn in services:
        ecs.update_service(cluster=cluster, service=arn, desiredCount=0)
    status = rds.describe_db_instances(DBInstanceIdentifier=db)["DBInstances"][0]["DBInstanceStatus"]
    if status == "available":  # detenida, deteniéndose o iniciando: no se toca
        rds.stop_db_instance(DBInstanceIdentifier=db)
    return {"servicios_en_cero": len(services), "rds": status}
