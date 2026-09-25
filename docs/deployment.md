# Deployment

Gather runs on Vercel (frontend) and Railway (API and PostgreSQL).

- Frontend: https://gather-chi-rosy.vercel.app
- API: https://gather-api-production-8dc3.up.railway.app
- Bot: https://t.me/gather_minerva_bot

## Railway

Use root directory `/apps/api` and its Dockerfile. Add a PostgreSQL service. In the API service dashboard, set one replica, disable serverless, and configure `/actuator/health/readiness` as the healthcheck path with a 300-second timeout. Use the On Failure restart policy.

Set these environment variables on the API service:

- `DATABASE_URL`: `jdbc:postgresql://host:port/database`
- `DATABASE_USERNAME` and `DATABASE_PASSWORD`: PostgreSQL credentials
- `JWT_SIGNING_SECRET`: a random secret of at least 32 bytes
- `TELEGRAM_BOT_TOKEN` and `TELEGRAM_BOT_USERNAME`: from BotFather
- `TELEGRAM_WEBHOOK_SECRET`: a separate random secret used when registering the webhook
- `TELEGRAM_MINI_APP_SHORT_NAME`: the registered Mini App short name, currently `gather`
- `MANAGEMENT_TOKEN`: a separate random secret for operational metrics
- `FRONTEND_ORIGIN`: the Vercel HTTPS origin, without a trailing slash

Railway supplies `PORT`. Use dashboard settings for new services; `railway.json` is retained for older configurations.

Flyway runs migrations at startup, and Hibernate validates the schema. Do not edit migrations that have already run; add a new migration instead.

## Vercel

Import the repository with the Next.js preset and root directory `apps/web`. Use Node.js 24 and include workspace files outside the root directory. Keep the default build settings.

Set these public environment variables before building:

```text
NEXT_PUBLIC_API_URL=https://gather-api-production-8dc3.up.railway.app
NEXT_PUBLIC_TELEGRAM_BOT_USERNAME=gather_minerva_bot
NEXT_PUBLIC_TELEGRAM_MINI_APP_SHORT_NAME=gather
```

Bot tokens and signing secrets belong only in Railway.

## Telegram

Configure the bot's menu button in BotFather to open the Vercel URL. Register the named Mini App for event deep links and enable inline mode for sharing.

Register a webhook through Telegram's `setWebhook` API:

- URL: `https://gather-api-production-8dc3.up.railway.app/api/v1/telegram/webhook`
- `secret_token`: the same value as `TELEGRAM_WEBHOOK_SECRET`
- `allowed_updates`: `message`, `inline_query`, `chat_member`, `my_chat_member`

Keep the bot token out of command history and logs. Membership updates must be explicitly included in the subscription.

Add the bot as an administrator to a group. A group administrator sends `/setup@gather_minerva_bot`; members send `/join@gather_minerva_bot`. Members can then open the Mini App from the bot's private chat. Sending `/start` privately enables notifications.

## Updates

Push to `main`. Check that Railway shows the new commit as Active and Vercel shows its production deployment as Ready. When both finish, close and reopen the Telegram Mini App. Frontend environment-variable changes require a rebuild.

## Troubleshooting

- Bot commands stay silent: inspect the API deploy logs for `telegram_update_rejected` or `telegram_api_failed`. Confirm the webhook subscription and secret match the service configuration.
- No communities appear: wait for the bot's `/join` confirmation, then reopen Gather. Both the user and bot must still belong to the group.
- Browser requests fail: check `FRONTEND_ORIGIN` against the exact Vercel production origin.
- Backend is unhealthy: inspect startup logs and database credentials. Readiness includes database connectivity.

## Operations

Metrics at `/actuator/metrics` require an `X-Management-Token` header. Monitor outbox backlog and failed notification deliveries. Avoid request-body and SQL-parameter logging in production.

Enable PostgreSQL backups and test restoring to a separate database with `pg_restore`. Roll back application deployments only when they remain compatible with the current schema. Avoid destructive down migrations.
