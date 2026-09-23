# Deployment and live acceptance

Deployment preparation is now underway. The user has created `gather_minerva_bot`, provided the GATHER GitHub repository, and connected Railway and Vercel accounts to GitHub. Hosting projects, Mini App registration, webhook registration and live checks remain pending. See [current handoff](deployment-handoff.md).

## Telegram

1. Create a bot through BotFather. Store its token in the backend secret environment, never a browser variable.
2. Configure a named Mini App (`gather` is the default short name), using the production Vercel HTTPS URL. Enable inline mode and configure the bot menu button.
3. Add the bot as an administrator to a private test group. A group administrator runs `/setup`; students run `/join`. Start the bot privately to opt into DMs.
4. Register an HTTPS webhook at `/api/v1/telegram/webhook` with a random `secret_token`. Subscribe explicitly to `message`, `inline_query`, `chat_member`, and `my_chat_member` updates. Telegram does not include membership updates in every default subscription.
5. Verify signed launch, joining, administrator changes, leaving/removal, DM opt-in, blocked-bot handling, and user-initiated inline sharing with two real accounts. The sender and recipient both need community access to open an event.

Webhook setup is an operator action. Use Telegram's setWebhook API without placing bot tokens in logs, shell history, or checked-in scripts. The endpoint validates the secret header before JSON parsing. Group timezone currently defaults to Asia/Tokyo; configure the community's timezone in PostgreSQL for other deployments before onboarding users.

## Vercel

Use repository root with install `pnpm install --frozen-lockfile`, build `pnpm --dir apps/web build`, and Next.js project/root configuration targeting `apps/web`. Alternatively set Root Directory to `apps/web` and allow access to workspace files outside that directory. Set `NEXT_PUBLIC_API_URL` to the Railway HTTPS API and `NEXT_PUBLIC_TELEGRAM_BOT_USERNAME` to the bot username. Set `NEXT_PUBLIC_TELEGRAM_MINI_APP_SHORT_NAME` to the BotFather short name (default `gather`). Public variables are embedded at build time.

## Railway

Deploy exactly one backend instance using `apps/api/Dockerfile` with build context `apps/api`. Add managed PostgreSQL. Set:

- `DATABASE_URL`: JDBC format `jdbc:postgresql://host:port/database` (translate Railway's PostgreSQL URI; do not pass `postgresql://` directly).
- `DATABASE_USERNAME`, `DATABASE_PASSWORD` from the managed database.
- `JWT_SIGNING_SECRET`: cryptographically random, at least 32 bytes.
- `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_WEBHOOK_SECRET`, `TELEGRAM_MINI_APP_SHORT_NAME`.
- `MANAGEMENT_TOKEN`: separate random secret of at least 32 bytes for metrics access.
- `FRONTEND_ORIGIN`: exact Vercel origin, without trailing slash.

Railway supplies PORT. Readiness is `/actuator/health/readiness`; database unavailability must fail readiness. Flyway runs at startup and Hibernate validates the schema. Use expand/migrate/contract migrations. Never combine destructive column removal with the release that starts migrating its consumers.

## Operations

HTTP timings and status counts are recorded by Actuator. Gather exposes gauges for active WebSockets, pending outbox rows, and delivery states. Operational metrics require the X-Management-Token header matching MANAGEMENT_TOKEN (at least 32 random bytes); leave this unset to disable access. Configure the monitoring collector with this secret before launch. Health endpoints expose no sensitive details. Do not enable Spring request-body/SQL parameter trace logging in production. Ensure the hosting environment does not inherit a DEBUG variable that enables Spring debug mode. Local verification scripts force DEBUG=false. Request completion logs contain a generated requestId, framework route template, resource UUID, HTTP operation/status, and elapsed milliseconds; they omit bodies, query strings and authorization headers. Scheduling scoring has a separate `gather.scheduling.scoring` timer.

Review pending/failed deliveries and outbox age. A process restart should recover pending work and expired leases. Do not manually reset sent deliveries without understanding duplicate-message risk. One Telegram HTTP call can have an ambiguous outcome; exactly-once external delivery is not guaranteed.

Enable managed PostgreSQL backups and test a restore into an isolated database before launch. A manual logical backup uses `pg_dump --format=custom` with credentials supplied securely in the environment; restore with `pg_restore` into a new database, then verify Flyway history, counts, and application readiness. Backups contain private community data.

Roll back application images only when their schema remains compatible. Do not use destructive down migrations. For data incidents, stop writes and restore into a separate database for verification before switching connections.

## Release checklist

- All automated phase checks, generated-type checks, browser workflows and chosen load thresholds pass.
- Real Telegram launch, `/setup`, `/join`, membership revocation, inline cards and DMs verified.
- Reconnect/offline and theme/safe-area behavior checked on Telegram iOS and Android.
- ICS imported into Google and Apple Calendar; UTC times and local display verified.
- Railway/Vercel HTTPS and origin configuration verified; secrets, backups and monitoring configured.
- Invite 10–20 actual cohort members; measure real activity separately from synthetic traffic.

Never report seeded records, browser contexts, or load-test VUs as real users.
