# Deployment configuration

## Services

- Repository: https://github.com/ThuHienMai/GATHER
- Telegram bot: https://t.me/gather_minerva_bot
- Frontend: https://gather-chi-rosy.vercel.app
- API: https://gather-api-production-8dc3.up.railway.app
- Railway project: Gather; services: gather-api and Postgres.
- Mini App short name configured in the application: `gather`. Named Mini App registration is separate from the working bot menu button.

## Railway

The API builds from `apps/api/Dockerfile`, with root directory `/apps/api`. Set these in the service dashboard:

- One replica; serverless disabled.
- Healthcheck path: `/actuator/health/readiness`.
- Healthcheck timeout: 300 seconds.
- Restart policy: On Failure.

The checked-in railway.json is a legacy configuration. New services use dashboard settings.

Keep database credentials, JWT signing secret, bot token, webhook secret, and management token in Railway Variables. Set `FRONTEND_ORIGIN=https://gather-chi-rosy.vercel.app`, without a trailing slash. See [deployment instructions](deployment.md) for the full variable list.

## Vercel

Use the Next.js preset, root directory `apps/web`, Node.js 24, and include workspace files outside the root. Use the pnpm lockfile and default Next.js build settings.

```text
NEXT_PUBLIC_API_URL=https://gather-api-production-8dc3.up.railway.app
NEXT_PUBLIC_TELEGRAM_BOT_USERNAME=gather_minerva_bot
NEXT_PUBLIC_TELEGRAM_MINI_APP_SHORT_NAME=gather
```

## Updating

Push changes to `main`. Check that Railway reports the commit as Active and Vercel reports the production deployment as Ready. Changes to both frontend and backend require both deployments to finish. Close and reopen the Telegram Mini App to load the new frontend.

See [the release checklist](deployment.md#release-checklist) for live checks that remain outstanding.
