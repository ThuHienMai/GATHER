# Gather deployment handoff

Confirmed public configuration:

- GitHub: https://github.com/ThuHienMai/GATHER
- Telegram bot: `gather_minerva_bot`
- Railway account: signed in through GitHub.
- Vercel account: signed in through Google and linked to GitHub.
- Mini App short name: `gather` (planned; register in BotFather).

## Railway

After publishing the code, create a project with PostgreSQL and a GitHub-backed API service. Select repository `ThuHienMai/GATHER`, Root Directory `/apps/api`, and Config File `/apps/api/railway.json`. Keep one replica. The config uses the existing Dockerfile and database readiness health check. Generate a public HTTPS domain.

Enter secrets only in Railway Variables. See deployment.md for database references, JWT signing secret, bot token, webhook secret and management token. Set `TELEGRAM_BOT_USERNAME=gather_minerva_bot` and `TELEGRAM_MINI_APP_SHORT_NAME=gather`. Set `FRONTEND_ORIGIN` to the eventual Vercel production origin, with no trailing slash.

## Vercel

Import the same repository with Next.js preset, Root Directory `apps/web`, Node.js 24, and workspace source files outside the root included. Use the pnpm lockfile and default Next.js build settings.

Production variables:

```text
NEXT_PUBLIC_API_URL=<Railway API HTTPS origin>
NEXT_PUBLIC_TELEGRAM_BOT_USERNAME=gather_minerva_bot
NEXT_PUBLIC_TELEGRAM_MINI_APP_SHORT_NAME=gather
```

## Still needed

- Code published to GitHub.
- Railway project/service names and backend HTTPS origin.
- Vercel project name and production HTTPS origin.
- Secrets entered privately in Railway, not in the repository or chat.
- Named Mini App registration, webhook registration and two-account live checks.

No deployment or webhook registration is implied by these local files. Account login in a browser does not automatically authenticate command-line tools in this workspace.
