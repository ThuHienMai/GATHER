# Gather

Gather is a Telegram Mini App for planning meetups with a group. Members can create events, RSVP, join a waitlist, suggest times, and discuss plans in one place.

Built with Next.js, TypeScript, Spring Boot, Java 21, and PostgreSQL.

## Use Gather

Open [@gather_minerva_bot](https://t.me/gather_minerva_bot) in Telegram.

To set up a group, add the bot as an administrator and send `/setup@gather_minerva_bot`. Each member can then send `/join@gather_minerva_bot` in the group and open the app from the bot's private chat.

Plans support fixed times or a shared availability window. Timezone options include San Francisco, Tokyo, Buenos Aires, and Berlin. Members can comment, receive Telegram notifications, and export scheduled events to their calendars.

## Local development

Install Node.js 24, pnpm 10.17.1, Java 21, and Docker with Compose.

```sh
cp .env.example .env
```

Set `POSTGRES_PASSWORD` and `JWT_SIGNING_SECRET` in `.env`. The signing secret must be at least 32 bytes. Add the Telegram variables when connecting a test bot.

```sh
docker compose up --build
```

In another terminal:

```sh
pnpm install --frozen-lockfile
pnpm dev
```

The frontend runs at http://localhost:3000 and the API at http://localhost:8080. Database readiness is available at `/actuator/health/readiness`.

Next.js reads `apps/web/.env.local`, not the root `.env`. Set `NEXT_PUBLIC_API_URL` there to use a different backend. Authentication requires opening the app through Telegram; browser tests supply a local test session.

To run the backend without Docker, start PostgreSQL, export the database and authentication variables from `.env.example`, then run `./mvnw spring-boot:run` in `apps/api`. On macOS with Homebrew and Colima, `bash scripts/check-api.sh spring-boot:run` sets the Java path and Docker socket.

## Tests

```sh
pnpm lint
pnpm typecheck
pnpm test
pnpm build
bash scripts/check-api.sh spotless:check verify
pnpm --dir apps/web exec playwright install chromium
bash scripts/e2e-stack.sh test
```

Backend tests use PostgreSQL Testcontainers. Browser tests start their own database on port 5433 and API on port 8081, then stop both when finished. GitHub Actions runs the checks on pushes and pull requests.

See [load tests](load-tests/README.md) for k6 commands.

## Project layout

- `apps/web` — Next.js frontend
- `apps/api` — Spring Boot API and Telegram bot
- `infra` — local PostgreSQL and API containers
- `scripts` — development and test utilities

[API reference](docs/api.md) · [Architecture](docs/architecture.md) · [Deployment](docs/deployment.md)
