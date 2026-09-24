# Gather

A Telegram Mini App for making plans with your community: fixed events, fair waitlists, private availability, collaborative scheduling, and reliable event updates.

Gather uses **Next.js 16.3.6**, **Spring Boot 3.5.16 / Java 21**, and **PostgreSQL 17**. The web app opens inside Telegram; ordinary browser visitors see an “Open in Telegram” entry screen. Production authentication has no test bypass.

## Features

- Telegram launch validation, memory-only 60-minute sessions, group enrollment, administrator synchronization, and membership revocation.
- Community feeds, My Events and Past views; create/edit/cancel/lock plans with stale-edit protection.
- Going/Maybe, capacity enforcement, FIFO waitlists and automatic promotion.
- Authenticated realtime updates, reconnect/refetch, and one comment feed per event, with replies.
- City timezone dropdowns for San Francisco, Tokyo, Buenos Aires, and Berlin.
- Private availability with keyboard and drag selection, daylight-saving validation, ranked scheduling and finalize-and-lock.
- Transactional outbox, leased notification delivery, preferences, DM opt-in and bounded retries.
- Authorized Telegram sharing, Google Calendar links and authenticated `.ics` export.

[Acceptance evidence](docs/implementation-status.md) distinguishes passing local checks from pending release work. [Measured performance](docs/benchmarks.md) includes workload details and limitations.

## Run locally

Prerequisites: Node.js 24, pnpm 10.17.1, Java 21, Docker and Compose. On macOS, Colima can provide Docker.

```sh
cp .env.example .env
# Set POSTGRES_PASSWORD and JWT_SIGNING_SECRET to your own random values.
# JWT_SIGNING_SECRET must contain at least 32 bytes.
# Add Telegram secrets only when connecting a real test bot.
docker compose up --build
```

In another terminal:

```sh
pnpm install --frozen-lockfile
pnpm dev
```

Frontend: http://localhost:3000. API readiness: http://localhost:8080/actuator/health/readiness. Next.js uses its default localhost API in development. For a different API or a visible Telegram entry link, configure `NEXT_PUBLIC_API_URL` and `NEXT_PUBLIC_TELEGRAM_BOT_USERNAME` (and the Mini App short name if changed) in `apps/web/.env.local`; root `.env` is used by Compose, not automatically loaded by Next.js.

To run Java outside Docker, start PostgreSQL only, export backend environment variables, and run `./mvnw spring-boot:run` from `apps/api`. Maven does not load the root `.env` automatically. On Homebrew/Colima systems, `bash scripts/check-api.sh spring-boot:run` configures Java and the Docker socket.

The browser entry screen is expected without a signed Telegram launch. To exercise complete workflows locally, use the isolated E2E setup below. Its synthetic signing keys exist only in test scripts and are never a production authentication path.

## Verify

```sh
pnpm lint
pnpm typecheck
pnpm test
pnpm build
bash scripts/check-api.sh spotless:check verify
pnpm --dir apps/web exec playwright install chromium
bash scripts/e2e-stack.sh test
```

Backend integration tests use real PostgreSQL Testcontainers. The E2E command starts an isolated database on port 5433 and API on port 8081, runs Chromium workflows, and stops its services. Tests cover the last-seat race, waitlist fairness, authorization, stale edits, outbox rollback, lease recovery, and 4,000 seeded scheduling comparisons. Browser workflows include two-client convergence and conflict recovery.

Regenerate API types with the [contract instructions](docs/api.md). CI regenerates both artifacts and rejects drift. GitHub Actions runs these checks on pushes and pull requests.

Run load tests separately from browser tests and other builds:

```sh
bash scripts/e2e-stack.sh start
docker compose -p gather-e2e -f infra/e2e-compose.yml exec -T postgres psql -U gather_test -d gather_test < scripts/load-seed.sql
docker compose -p gather-e2e -f infra/e2e-compose.yml exec -T postgres psql -U gather_test -d gather_test < scripts/load-reset.sql
cd load-tests/k6
k6 run -e CASE=feed scenarios.js
k6 run -e CASE=rsvp scenarios.js
k6 run -e CASE=ws scenarios.js
k6 run -e CASE=schedule scenarios.js
k6 run -e CASE=webhook scenarios.js
cd ../..
bash scripts/e2e-stack.sh stop
```

Reset synthetic attendance before repeating the sequence; otherwise repeated Going requests become no-ops and distort measurements. Scenarios deliberately refuse nonlocal targets. On machines with the standalone Compose binary, substitute `docker-compose` for `docker compose`.

## Screenshots

[Mobile home](docs/screenshots/gather-home-mobile.png) · [Finalized event](docs/screenshots/gather-event-mobile.png). Captured from the local browser workflow with synthetic test users.

## Architecture and operations

`apps/web` contains the Telegram UI; `apps/api` contains the modular monolith; `infra` and `scripts` provide local operations; `load-tests` holds reproducible traffic; `docs` contains the contract, seven ADRs, release evidence and original specification.

PostgreSQL row locks serialize capacity changes. Event versions apply to organizer edits; incidental RSVP/comment activity does not invalidate a draft. Mutations and immutable outbox records commit together. Notifications use fenced leases and five total attempts; an ambiguous external Telegram response can still cause duplicate delivery. Range-add scheduling runs in O(A + S) after interval normalization and counts each eligible attendee once per candidate.

See [architecture](docs/architecture.md), [API and WebSocket protocol](docs/api.md), [deployment and operations](docs/deployment.md), and [original specification](docs/product-specification.md).

V1 intentionally excludes native apps, PWA installation, media storage, calendar synchronization/OAuth, payments, social feeds, recommendation AI, Redis and Kafka. Deployment targets one backend instance. The frontend is deployed on Vercel and the API on Railway. See the deployment checklist for remaining mobile, calendar import, backup, and cohort checks.
