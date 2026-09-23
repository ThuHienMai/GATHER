# Implementation and acceptance evidence

Updated September 24, 2026. The user explicitly deferred Telegram/BotFather, Vercel and Railway setup. Local implementation and acceptance are the current scope; the live release checklist remains in [deployment.md](deployment.md).

| Phase | Implementation | Executed local acceptance | Deferred live acceptance |
| --- | --- | --- | --- |
| 0 Bootstrap | Complete | Next production build; PostgreSQL Testcontainers readiness; clean Flyway migrations through V10; Hibernate validation | Hosted CI has not run |
| 1 Authentication | Complete | HMAC tampering, expiry, future/malformed data rejection; user upsert; `/me`; browser memory-only session and expiry | Real Telegram signed launch |
| 2 Communities | Complete | Setup roles; transactional rollback/deduplication; nonmember denial; leaving/rejoining; same-second out-of-order updates | Real group permissions and bot updates |
| 3 Fixed events | Complete | Authorized create/read/edit; required version preconditions; stale writes; locked planning; browser edit/cancel | Cohort use |
| 4 RSVP and capacity | Complete | Three runs of 20 concurrent requests for one seat, each 1 Going/19 Waitlisted; stable FIFO position; Going→Maybe promotion; capacity increase and rejected decrease | Cohort use |
| 5 Realtime | Complete | Actual WebSocket invalidations; existing subscription closed after revocation; browser two-client convergence and offline/reconnect refetch | Telegram mobile reconnect behavior |
| 6 Discussion | Complete | Section/parent restrictions; reply depth; soft deletion; two-client comment visibility; pagination UI | Real group discussion |
| 7 Scheduling | Complete | 4,000 seeded oracle comparisons; canonical interval boundaries; atomic replacement/finalize; DST input checks; keyboard/drag browser workflow | Real-user usability |
| 8 Outbox | Complete | Mutation rollback leaves no outbox row; replay deduplicates deliveries; cache invalidations remain harmless | Deployed restart/backlog observation |
| 9 Notifications | Complete | Retry-after; fenced lease recovery; mute filtering; blocked bot disables DM; reminder uniqueness; inactive-community history filtering | Actual Telegram delivery, blocked bot and timing |
| 10 Sharing | Complete | Inline results require membership; unauthorized users get no event data; configurable Mini App deep links | Real inline cards and event opening |
| 11 Calendar | Complete | Stable UID, UTC timestamps, escaped text, UTF-8 folding, cancellation and Google link tests; browser export controls | Google/Apple Calendar import |
| 12 UX and documentation | Complete locally | Lint/types/unit/build; all five browser workflows; mobile screenshot inspection; API contract equality; seven ADRs and operations documentation | Telegram iOS/Android theme and safe-area checks |
| 13 Performance | Local gate passed | All five k6 cases pass configured thresholds; scheduling microbenchmark executed separately | Rerun on selected hosting tier |
| 14 Launch | Deferred by user | Configuration examples and release checklist prepared | BotFather, hosting, HTTPS, backups, monitoring, real cohort |

## Final local checks

- Backend: **27 passing tests** (8 unit and 19 PostgreSQL integration executions), zero failures/errors. The opt-in microbenchmark is skipped during the normal suite and was executed separately. The three concurrency repetitions count as three integration executions.
- Frontend: **5 passing behavioral/unit tests**, ESLint, TypeScript, production build, and offline frozen-lockfile install.
- Chromium: **5 passing workflows on a fresh isolated database**, including two smoke/session checks and three real API/PostgreSQL workflows. Telegram SDK responses are test fixtures; the actual backend still validates signatures. No production bypass is included.
- Contract: exported OpenAPI matches `docs/openapi.json`; freshly generated TypeScript matches `apps/web/generated/api.ts` byte-for-byte.
- Load: event read **31.97 ms p95**, RSVP **251.46 ms p95**, WebSocket invalidation **300.00 ms p95**, scheduling HTTP **18.32 ms p95**; all scenarios had 100% checks and zero observed 5xx. See [raw evidence and limits](benchmarks.md).

`bash scripts/e2e-stack.sh test` starts and tears down the isolated services. Test-only credentials in fixtures do not represent real credentials. Deployment secrets in `.env.example` are blank. Production values must be supplied in the relevant host environment.

## Remaining release work

No real Telegram, hosted CI, deployment, calendar import, backup/restore drill or user-adoption check has been performed. Git is initialized with origin https://github.com/ThuHienMai/GATHER.git. CI configuration and generated contract files are included; a remote CI result has not yet been verified. Hosting configuration and live checks are later setup tasks.

Performance measurements are short local acceptance runs, not production capacity or adoption claims. Native apps, PWA installation, media storage, OAuth/calendar sync, Redis/Kafka and other stated V1 exclusions remain excluded. See the [original specification](product-specification.md), [accepted plan](implementation-plan.md), and [release checklist](deployment.md).
