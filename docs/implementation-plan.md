# Accepted implementation plan

The original specification is in `product-specification.md`. Implement phases 0–14 in order. Automated acceptance gates block advancement; live Telegram and hosting checks block release, not implementation.

## Current scope

The application is deployed on Vercel and Railway. Continue verifying changes locally and in CI; keep deployment secrets in hosting environment variables. Remaining release checks are tracked in deployment.md.

## Accepted clarifications

- Revoke community access on leaving/removal from Telegram, including active subscriptions. Rejoining requires `/join`.
- Synchronize Telegram administrator roles; community administrators manage orphaned events.
- Finalize flexible schedules and lock together. Locked events still accept RSVP changes, promotions, and discussion. Planning is frozen.
- Rank only Going and Maybe attendees. Preserve waitlisted availability for promotion.
- Five-minute initialization freshness, 30-second future tolerance, 60-minute in-memory JWT. Reopen Telegram when initialization expires.
- Serialize RSVP/capacity mutations with an event row lock. Queue by waitlist-entry timestamp, preserve position on repeat requests, reject capacity below confirmed count.
- Five total delivery attempts; retry after 5 seconds, 30 seconds, 2 minutes, and 10 minutes. Telegram retry-after takes precedence. External delivery is not exactly once.
- Organizer/Going/Maybe default ALL_ACTIVITY; waitlist IMPORTANT_ONLY; nonparticipants unsubscribed. Explicit preferences override.
- UTC instants and IANA timezone; 30-minute candidate spacing; elapsed-time duration. Top three recommendations, ordered by Going, weighted total, then earliest.
- One reminder 30 minutes before the current schedule; revalidate before sending.
- Calendar links and files are one-time exports, not synchronization.
- Test authentication must be absent from production artifacts. No persistent browser token storage.

## Sequence

0. Bootstrap, PostgreSQL connectivity, migrations, frontend build, CI.
1. Telegram authentication and `/me`.
2. Bot commands, communities, membership, webhook deduplication.
3. Fixed events and conditional updates.
4. RSVP/capacity/waitlist races.
5. WebSocket cache invalidation and convergence.
6. Structured discussion.
7. Flexible availability, scoring oracle/optimized implementation, finalization.
8. Transactional outbox and durable invalidation.
9. Notification preferences, leasing, retries, reminders.
10. Inline sharing and event deep links.
11. Google Calendar link and ICS export.
12. Complete mobile UI, accessibility, ADRs and operations documentation.
13. Executed load tests and scheduling benchmarks.
14. Live verification and cohort launch after external setup.

No runtime benchmarks, CI success, deployment, or adoption is claimed without evidence. No additional technologies or product scope beyond the specification.
