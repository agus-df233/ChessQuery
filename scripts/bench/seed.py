"""Siembra el MISMO dataset sintético en la v2 y la v3 para compararlas en igualdad de condiciones.

10.000 jugadores ficticios (semilla fija 42): nombres chilenos inventados, región entre las 16, año de
nacimiento 1950–2019 y ELO nacional 800–2600 (15 % sin ELO). Se inserta con COPY vía `docker exec psql`.
"""
from __future__ import annotations

import io
import random
import subprocess
import sys
from datetime import date, timedelta

N = 10_000
FIRST = ["Tomás", "Valentina", "Matías", "Catalina", "Benjamín", "Javiera", "Vicente", "Antonia", "Joaquín",
         "Isidora", "Martín", "Florencia", "Agustín", "Josefa", "Lucas", "Emilia", "Diego", "Fernanda"]
LAST = ["González", "Muñoz", "Rojas", "Díaz", "Pérez", "Soto", "Contreras", "Silva", "Martínez", "Sepúlveda",
        "Morales", "Rodríguez", "López", "Fuentes", "Hernández", "Torres", "Araya", "Flores", "Espinoza", "Valenzuela"]
REGIONS = ["Arica y Parinacota", "Tarapacá", "Antofagasta", "Atacama", "Coquimbo", "Valparaíso", "Metropolitana",
           "O'Higgins", "Maule", "Ñuble", "Biobío", "Araucanía", "Los Ríos", "Los Lagos", "Aysén", "Magallanes"]

TARGETS = {
    "v2": {"container": "chessquery-bench-v2-db-1", "user": "chessquery", "db": "user_db",
           "table": "player", "columns": "first_name,last_name,region,birth_date,elo_national"},
    "v3": {"container": "chessquery-postgres-1", "user": "chessquery", "db": "chessquery_bench",
           "table": "users.player", "columns": "first_name,last_name,region,birth_date,birth_year,elo_national"},
}


def rows(seed: int = 42):
    rnd = random.Random(seed)
    for _ in range(N):
        born = date(1950, 1, 1) + timedelta(days=rnd.randrange(0, 70 * 365))
        elo = None if rnd.random() < 0.15 else rnd.randint(800, 2600)
        yield (rnd.choice(FIRST), f"{rnd.choice(LAST)} {rnd.choice(LAST)}", rnd.choice(REGIONS), born, elo)


def copy_text(target: str) -> str:
    buf = io.StringIO()
    for first, last, region, born, elo in rows():
        values = [first, last, region, born.isoformat()]
        if target == "v3":
            values.append(str(born.year))
        values.append("\\N" if elo is None else str(elo))
        buf.write("\t".join(values) + "\n")
    return buf.getvalue()


def seed(target: str) -> None:
    t = TARGETS[target]
    sql = f"TRUNCATE {t['table']} RESTART IDENTITY CASCADE; COPY {t['table']} ({t['columns']}) FROM STDIN;"
    cmd = ["docker", "exec", "-i", t["container"], "psql", "-q", "-v", "ON_ERROR_STOP=1",
           "-U", t["user"], "-d", t["db"], "-c", sql]
    subprocess.run(cmd, input=copy_text(target).encode("utf-8"), check=True)
    subprocess.run(["docker", "exec", t["container"], "psql", "-q", "-U", t["user"], "-d", t["db"],
                    "-c", f"ANALYZE {t['table']};"], check=True)
    print(f"{target}: {N} jugadores sembrados en {t['db']}.{t['table']}")


if __name__ == "__main__":
    for name in sys.argv[1:] or ["v2", "v3"]:
        seed(name)
