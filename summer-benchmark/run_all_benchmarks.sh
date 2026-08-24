#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "==> Building Summer benchmark targets..."
mvn clean package -DskipTests -f pom.xml

for profile in spring-boot summer summer-jsonb gin fastify; do
  echo "==> Running benchmark for $profile..."
  docker compose --profile "$profile" up --build --abort-on-container-exit
  docker compose --profile "$profile" down
  echo "==> Finished $profile."
  sleep 5
done

python3 compare-benchmarks.py
