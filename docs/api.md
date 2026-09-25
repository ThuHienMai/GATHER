# API contract

The canonical generated snapshot is [openapi.json](openapi.json). The running service exposes `/api/openapi.json`. Frontend types are generated from it in `apps/web/generated/api.ts`; event and RSVP components derive their response types from these schemas.

## Requests

Use `Authorization: Bearer <JWT>` on protected routes. Authentication is `POST /api/v1/auth/telegram` with `{ "initData": "<raw Telegram initialization data>" }`. `/me` returns the current identity. Invalid/expired launches must be reopened in Telegram.

`GET /events/{id}` returns `ETag: "N"`. Send this value as `If-Match` for editing, locking, cancellation, and flexible finalization. PATCH currently accepts the complete editable event form, with omitted nullable fields cleared; it is not JSON Patch. All routes above use the `/api/v1` prefix.

Event feeds and My Events accept `page` (zero-based, 30 results); `view=upcoming|past` selects the lifecycle view. Discussion pages contain 50 entries; use `section=ALL` for the combined feed. Notification history pages contain 30 entries. Interval timestamps require ISO-8601 instants. Timezone fields use IANA names.

`PUT /events/{id}/rsvp` accepts `{"status":"GOING"}` or `{"status":"MAYBE"}`; the server decides whether Going is waitlisted. DELETE withdraws. `GET /events/{id}/rsvps` returns counts, current user status and participants. Availability PUT replaces the current user's entire interval list; an empty list clears it. Recommendations return the best three slots. Finalization requires a valid future candidate and locks planning.

Calendar downloads use authenticated `GET /events/{id}/calendar.ics`; Google links use `/calendar/google`. Notification preferences support ALL_ACTIVITY, IMPORTANT_ONLY and MUTED.

## WebSocket

Connect to `/ws` from the configured frontend origin. Within ten seconds send `{ "type": "AUTH", "token": "..." }`; await AUTH_OK. Subscribe with `{ "type": "SUBSCRIBE_EVENT", "eventId": "..." }`; SUBSCRIBED acknowledges authorization and prompts a REST refetch. UNSUBSCRIBE_EVENT removes a subscription. Reply PONG to PING. Sessions expire with the JWT. Reconnect uses exponential backoff and jitter, capped at 30 seconds.

Invalidations carry `type`, `eventId`, `changeId`, and `occurredAt`. Treat duplicates as normal. They never substitute for authoritative REST state.

## Errors and limits

Application validation errors use Spring ProblemDetail. 401 means authenticate/reopen; 403 means membership/role denied; 409 means lifecycle/capacity conflict; 412 means review stale edit; 428 means missing precondition. Request bodies are limited to 128 KiB. In-process per-identity limits protect authentication and mutations. They assume a single backend and do not trust arbitrary forwarded client-IP headers.

## Regenerate

```sh
bash scripts/check-api.sh verify
cp apps/api/target/openapi.json docs/openapi.json
pnpm --dir apps/web generate:api
pnpm typecheck
```

CI compares regenerated contract artifacts to the committed files.

## Discussion

The event screen displays one comment feed. `GET /api/v1/events/{id}/comments?section=ALL&page=0` returns all existing categories in chronological order, 50 comments per page. New top-level comments use `GENERAL`; replies retain the parent category for compatibility with existing clients. Categories are not shown in the UI.
