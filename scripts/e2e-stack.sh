#!/usr/bin/env bash
set -euo pipefail
export DEBUG=false
cd "$(dirname "$0")/.."
mkdir -p .tools
if command -v docker-compose >/dev/null; then compose=(docker-compose); else compose=(docker compose); fi
if [ "${1:-start}" = stop ]; then
  if [ -f .tools/e2e-api.pid ]; then kill "$(cat .tools/e2e-api.pid)" 2>/dev/null || true; fi
  "${compose[@]}" -p gather-e2e -f infra/e2e-compose.yml down
  exit
fi
"${compose[@]}" -p gather-e2e -f infra/e2e-compose.yml up -d --wait
bash scripts/check-api.sh package -DskipTests > .tools/e2e-build.log 2>&1
java_command=java
if [ -x /opt/homebrew/opt/openjdk@21/bin/java ]; then java_command=/opt/homebrew/opt/openjdk@21/bin/java; fi
DATABASE_URL=jdbc:postgresql://localhost:5433/gather_test DATABASE_USERNAME=gather_test DATABASE_PASSWORD=gather_test JWT_SIGNING_SECRET=e2e-only-signing-secret-never-use-in-production TELEGRAM_BOT_TOKEN=123:e2e-only TELEGRAM_BOT_USERNAME=gather_test_bot TELEGRAM_WEBHOOK_SECRET=e2e-only-webhook-secret FRONTEND_ORIGIN=http://127.0.0.1:3000 PORT=8081 nohup "$java_command" -jar apps/api/target/gather-api-0.1.0-SNAPSHOT.jar > .tools/e2e-api.log 2>&1 &
echo "$!" > .tools/e2e-api.pid
ready=false
for attempt in {1..60}; do
  if curl -fsS http://localhost:8081/actuator/health/readiness >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [ "$ready" != true ]; then tail -40 .tools/e2e-api.log; exit 1; fi
"${compose[@]}" -p gather-e2e -f infra/e2e-compose.yml exec -T postgres psql -U gather_test -d gather_test < scripts/e2e-seed.sql
printf 'Test API ready at http://localhost:8081. Run GATHER_E2E_INTEGRATION=1 pnpm e2e.\n'

if [ "${1:-start}" = test ]; then
  trap 'kill "$(cat .tools/e2e-api.pid)" 2>/dev/null || true; "${compose[@]}" -p gather-e2e -f infra/e2e-compose.yml down' EXIT
  GATHER_E2E_INTEGRATION=1 pnpm --dir apps/web exec playwright test
fi
