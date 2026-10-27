#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f .env ]; then set -a; source .env; set +a; fi
# Use only a dedicated *_test database: integration tests truncate its tables.
docker compose up -d --wait db
if ! docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '\''negocio_facil_test'\''"' | grep -q 1; then
 docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" negocio_facil_test'
fi
export TEST_DATABASE_URL="${TEST_DATABASE_URL:-jdbc:postgresql://localhost:${DATABASE_PORT:-5432}/negocio_facil_test}"
"${MAVEN_CMD:-mvn}" -B -f backend/pom.xml test
