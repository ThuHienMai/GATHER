I would make one important architectural change before Codex touches the code:

**Gather should be a Telegram Mini App, not merely a generic PWA.** A Telegram Mini App is still a web application—HTML/React/JavaScript served over HTTPS—but it opens directly inside Telegram's WebView. Telegram explicitly supports direct-link Mini Apps with shared chat context and describes them as appropriate for cooperative/teamwork applications. That fits Gather unusually well because Telegram is already where the problem occurs. ([Telegram Core][1])

The same frontend can remain responsive in a normal browser, but I would make **Telegram the primary production surface**. I would *not* build a native app, a separate PWA installation flow, or media storage in V1.

Below is the specification I would give Codex.

---

# Gather — Technical/Product Specification

## 1. Product definition

**Gather is a Telegram-integrated, real-time social coordination system for university communities.**

It solves four concrete problems:

1. Hangout invitations disappear in high-volume Telegram chats.
2. Organizers cannot tell reliably who is attending.
3. Organizers do not know whether shifting an event by 30–60 minutes would enable substantially more people to attend.
4. Event changes and discussions are scattered throughout Telegram rather than attached to structured event state.

Gather does **not replace Telegram chat**.

Telegram remains responsible for:

* general group conversation;
* casual messaging;
* photos/videos after events;
* arbitrary social interaction.

Gather is responsible for:

* structured event discovery;
* RSVP state;
* collaborative scheduling;
* event-specific structured discussion;
* reliable schedule-change notifications;
* event lifecycle/history;
* calendar export.

That separation is intentional.

---

# 2. Final product surface

I would call it:

> **Telegram Mini App + Telegram Bot**

not:

> Chrome extension
> native mobile application
> generic website

A user sees an event card in Telegram, taps **Open in Gather**, and Gather opens directly inside Telegram.

Telegram's current Mini App system supports direct links such as `t.me/botusername/appname?startapp=...`, provides Telegram user information through signed initialization data, and supports shared-chat context. Telegram explicitly warns that client-side `initDataUnsafe` must not be trusted; the raw `initData` should be validated server-side. ([Telegram Core][1])

---

# 3. High-level architecture

```text
                    ┌───────────────────────────────┐
                    │           Telegram            │
                    │                               │
                    │ group posts / bot / DMs       │
                    └──────────────┬────────────────┘
                                   │
                       Bot API + webhook
                                   │
                                   ▼
┌───────────────────────────────────────────────────────────────┐
│                    Spring Boot Backend                        │
│                                                               │
│  Auth        Events       Scheduling       Discussion         │
│  │           │             Engine            │                │
│  └───────────┴───────────────┬───────────────┘                │
│                              │                                │
│                      PostgreSQL                               │
│                              │                                │
│                 Transactional Outbox                          │
│                    │               │                          │
│                    ▼               ▼                          │
│              WebSocket         Notification                   │
│              broadcast            worker                      │
│                                      │                        │
└──────────────────────────────────────┼────────────────────────┘
                                       │
                                       ▼
                                   Telegram DM


                    HTTPS / REST / WebSocket
                              ▲
                              │
                    ┌──────────────────┐
                    │  Next.js Mini App│
                    │ React/TypeScript │
                    └──────────────────┘
```

This is a **modular monolith**.

That is intentional.

I do **not** want microservices.

For a system with perhaps dozens or hundreds of real users, microservices would create deployment/networking complexity without solving an actual problem. We can still separate the backend into domain modules cleanly enough that individual modules could later become services.

---

# 4. Exact technology choices

| Layer                   | Choice                                                                     | Why                                                                                                                                                                                    |
| ----------------------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Primary client          | **Telegram Mini App**                                                      | Eliminates installation friction and lives where users already coordinate                                                                                                              |
| Frontend                | **Next.js 16.3.6 + React + TypeScript**                                    | Mature React ecosystem; 16.3.6 is currently Active LTS and includes the Sep. 22 security update. ([Next.js][2])                                                                        |
| Styling                 | **Tailwind CSS + shadcn/ui**                                               | Fast implementation; accessible components; does not become an architectural dependency                                                                                                |
| Server-state management | **TanStack Query**                                                         | Excellent cache invalidation/re-fetch model for realtime server state                                                                                                                  |
| Forms                   | **React Hook Form + Zod**                                                  | Client UX validation; backend remains authoritative                                                                                                                                    |
| Backend                 | **Java 21 + Spring Boot 3.5.x**                                            | Deliberately conservative LTS Java + mature Spring ecosystem instead of adopting a major framework release just for novelty. Spring currently maintains 3.5 alongside 4.x. ([Home][3]) |
| Database                | **PostgreSQL**                                                             | Transactions, locking, constraints, JSONB and strong relational modeling all matter for Gather                                                                                         |
| ORM                     | **Spring Data JPA/Hibernate**                                              | Fast enough to implement while giving us transactions and `@Version` optimistic locking                                                                                                |
| Migrations              | **Flyway**                                                                 | Every schema change version-controlled                                                                                                                                                 |
| Realtime                | **Native WebSockets with small JSON protocol**                             | We have genuine realtime requirements but no need for STOMP/RabbitMQ                                                                                                                   |
| Async reliability       | **PostgreSQL transactional outbox**                                        | Gives us reliable post-transaction work without inventing Kafka infrastructure                                                                                                         |
| Telegram integration    | **Telegram Bot API + Mini App API**                                        | Native discovery, identity and notifications                                                                                                                                           |
| Calendar V1             | **Google Calendar deep link + `.ics`**                                     | No OAuth or verification needed just to add an event                                                                                                                                   |
| Testing                 | **JUnit 5, Testcontainers, Vitest, React Testing Library, Playwright, k6** | Covers unit, DB integration, E2E and performance                                                                                                                                       |
| API contract            | **OpenAPI + generated TypeScript types**                                   | Prevent frontend/backend contract drift                                                                                                                                                |
| Frontend deployment     | **Vercel**                                                                 | Near-zero operational overhead for Next.js                                                                                                                                             |
| Backend/database        | **Railway**                                                                | Dockerized Java service + managed PostgreSQL + WebSocket support                                                                                                                       |
| CI                      | **GitHub Actions**                                                         | Test/build on every PR                                                                                                                                                                 |

I deliberately **do not add Redis initially**.

I deliberately **do not add Kafka**.

I deliberately **do not add Kubernetes**.

PostgreSQL alone can handle the initial consistency, persistence, job scheduling and outbox requirements.

---

# 5. Major product flows

## Flow A — onboarding

A Minerva student opens Gather through Telegram.

```text
Telegram
    ↓
Launch Gather
    ↓
Telegram supplies signed initData
    ↓
POST /api/v1/auth/telegram
    ↓
backend verifies Telegram signature
    ↓
find/create User
    ↓
issue short-lived Gather JWT
    ↓
Home feed
```

The client never trusts Telegram's unsigned user object.

The backend verifies:

* signature/hash;
* Telegram user ID;
* `auth_date`;
* freshness of initialization data.

The Gather JWT is short lived, e.g. **60 minutes**.

It is stored **only in memory**, not `localStorage`.

If the page reloads, the Mini App simply authenticates again from Telegram's signed initialization payload.

This gives us a simpler security model than refresh-token infrastructure.

---

# 6. Community model

Gather should **not be Minerva-hardcoded** internally.

The first deployment contains:

> Minerva Tokyo 2026–27

but the data model supports:

> Minerva Buenos Aires
> Minerva Seoul
> another university society
> another Telegram community

The core structure is:

```text
Community
    ├── Members
    └── Events
```

Telegram group onboarding provides membership.

Initial bot commands:

```text
/setup
/join
/start
/help
```

### `/setup`

Used once by a Telegram group administrator.

Backend stores:

```text
Community
telegram_chat_id
name
timezone
```

### `/join`

Executed inside the registered Telegram group.

That gives Gather both:

```text
telegram_chat_id
telegram_user_id
```

The backend creates:

```text
CommunityMembership
```

This is preferable to distributing a permanent invite code.

---

# 7. Event model

Every event has two possible scheduling modes:

```text
FIXED
FLEXIBLE
```

## Fixed event

Example:

> Dinner, Friday 19:00–21:00

Organizer already knows the time.

## Flexible event

Example:

> Karaoke Friday evening
> sometime between 18:00 and 23:00
> duration = 2 hours

Users provide availability and Gather determines which start times maximize attendance.

This flexible scheduling engine is one of the project's primary technical differentiators.

---

# 8. Event lifecycle

Do not create twelve redundant statuses.

Use:

```text
OPEN
LOCKED
CANCELLED
COMPLETED
```

### OPEN

RSVPs, availability and discussion accepted.

### LOCKED

Organizer has frozen major planning.

Discussion remains available, but schedule editing is restricted.

### CANCELLED

Event will not happen.

### COMPLETED

Automatically assigned after event completion.

"Past events" and "archive" are **queries/views**, not additional database states.

Example:

```text
WHERE status IN ('COMPLETED', 'CANCELLED')
   OR end_at < now()
```

---

# 9. Database schema

I would use UUID primary keys for internal entities.

## `users`

```sql
id                  UUID PRIMARY KEY
telegram_user_id    BIGINT UNIQUE NOT NULL
telegram_username   VARCHAR(64)
first_name          VARCHAR(128)
last_name           VARCHAR(128)
timezone            VARCHAR(64)
created_at          TIMESTAMPTZ NOT NULL
updated_at          TIMESTAMPTZ NOT NULL
last_active_at      TIMESTAMPTZ
telegram_dm_enabled BOOLEAN NOT NULL DEFAULT FALSE
```

Never use Telegram username as identity.

Usernames can change.

Telegram numeric ID is the external identity.

---

## `communities`

```sql
id                  UUID PRIMARY KEY
name                VARCHAR(150) NOT NULL
slug                VARCHAR(80) UNIQUE NOT NULL
telegram_chat_id    BIGINT UNIQUE
timezone            VARCHAR(64) NOT NULL
created_at          TIMESTAMPTZ NOT NULL
```

---

## `community_memberships`

```sql
community_id        UUID NOT NULL REFERENCES communities(id)
user_id             UUID NOT NULL REFERENCES users(id)
role                VARCHAR(20) NOT NULL
joined_at           TIMESTAMPTZ NOT NULL

PRIMARY KEY (community_id, user_id)
```

Roles:

```text
MEMBER
ADMIN
```

---

# 10. `events`

```sql
id                      UUID PRIMARY KEY
community_id            UUID NOT NULL
organizer_id            UUID NOT NULL

title                   VARCHAR(120) NOT NULL
description             VARCHAR(2000)
location_text           VARCHAR(250)
location_url            VARCHAR(500)

scheduling_mode         VARCHAR(20) NOT NULL

start_at                TIMESTAMPTZ
end_at                  TIMESTAMPTZ

flex_window_start       TIMESTAMPTZ
flex_window_end         TIMESTAMPTZ
duration_minutes        INTEGER

timezone                VARCHAR(64) NOT NULL

capacity                INTEGER

status                  VARCHAR(20) NOT NULL

version                 BIGINT NOT NULL DEFAULT 0

created_at              TIMESTAMPTZ NOT NULL
updated_at              TIMESTAMPTZ NOT NULL
```

`version` is crucial.

It powers optimistic concurrency control.

---

# 11. RSVP model

```sql
event_rsvps

event_id       UUID
user_id        UUID
status         VARCHAR(20)
created_at     TIMESTAMPTZ
updated_at     TIMESTAMPTZ

PRIMARY KEY(event_id, user_id)
```

States:

```text
GOING
MAYBE
WAITLISTED
```

No permanent `NOT_GOING` record is necessary.

Removing an RSVP means not participating.

---

# 12. Add optional capacity + waitlist

I recommend including this.

It naturally creates a meaningful concurrency problem.

Example:

```text
Karaoke
Capacity: 8

7/8 attending
```

Alice and Bob simultaneously press **Going**.

There is only one place.

Gather must never produce:

```text
9 / 8
```

The backend performs the transition transactionally.

Conceptually:

```text
BEGIN

lock capacity state

if confirmed < capacity:
    RSVP = GOING
else:
    RSVP = WAITLISTED

COMMIT
```

When someone cancels:

```text
BEGIN

remove RSVP

if event has waitlisted users:
    promote oldest WAITLISTED user → GOING
    create EVENT_WAITLIST_PROMOTED outbox event

COMMIT
```

Now you have an extremely concrete interview discussion around:

* race conditions;
* row-level locking;
* transaction boundaries;
* consistency;
* fairness.

And the feature is genuinely useful for restaurants, karaoke, tickets, etc.

---

# 13. Availability representation

Do **not** store one database row per 30-minute cell.

Store intervals.

```sql
availability_intervals

id            UUID PRIMARY KEY
event_id      UUID NOT NULL
user_id       UUID NOT NULL
start_at      TIMESTAMPTZ NOT NULL
end_at        TIMESTAMPTZ NOT NULL
created_at    TIMESTAMPTZ NOT NULL
```

Example:

```text
19:00–20:30 available
22:00–23:30 available
```

rather than:

```text
19:00
19:30
20:00
22:00
22:30
23:00
```

The API canonicalizes user input:

```text
19:00–20:00
20:00–20:30
```

becomes:

```text
19:00–20:30
```

Overlapping intervals are rejected or merged.

---

# 14. Scheduling engine — core algorithm

This should be a real technical feature, not:

> "loop over every time and count everybody."

Suppose:

```text
window:
Friday 18:00 → 23:00

duration:
120 minutes

granularity:
30 minutes
```

Possible starts:

```text
18:00
18:30
19:00
19:30
20:00
20:30
21:00
```

For each user's availability interval:

```text
19:00 → 23:00
```

a 2-hour event can start anywhere from:

```text
19:00 → 21:00
```

So instead of comparing every user against every candidate slot, convert each availability interval into a **range of valid start positions**.

Then use a **difference array / range-add sweep**.

Example:

```text
scores[] = 0

user available for candidate starts 2 through 6:

diff[2] += weight
diff[7] -= weight
```

Prefix sum:

```text
score[i] = score[i-1] + diff[i]
```

This produces all candidate attendance scores in roughly:

```text
O(A + S)
```

where:

```text
A = number of availability intervals
S = number of candidate start slots
```

instead of repeatedly scanning every interval for every candidate.

That is exactly the sort of small but legitimate algorithmic optimization worth benchmarking.

---

# 15. RSVP weighting in scheduling

Use:

```text
GOING → weight 2
MAYBE → weight 1
```

Maintain two separate difference arrays anyway so the UI can show:

```text
20:00
11 Going available
4 Maybe available

20:30
13 Going available
1 Maybe available
```

Ranking order:

1. maximize available `GOING`;
2. maximize combined weighted attendance;
3. earlier slot wins ties.

Do not use an opaque AI model.

The organizer should understand *why* a time is recommended.

---

# 16. Scheduling UI

Flexible event page:

```text
When can you come?

Friday

18:00  ○
18:30  ○
19:00  ●
19:30  ●
20:00  ●
20:30  ●
21:00  ○
```

Users can drag over slots.

Gather merges adjacent selections into intervals before sending them.

Organizer sees:

```text
BEST TIMES

1. Fri 20:00–22:00
   13 going + 3 maybe available

2. Fri 19:30–21:30
   12 going + 4 maybe available

3. Fri 20:30–22:30
   12 going + 2 maybe available
```

Organizer explicitly presses:

> **Finalize 20:00**

Gather does **not** automatically change the event time.

Human remains in control.

---

# 17. Concurrency control for event editing

This is the second major technical centerpiece.

JPA entity:

```java
@Version
private long version;
```

API uses HTTP ETags.

GET:

```http
GET /api/v1/events/abc
```

Response:

```http
ETag: "12"
```

Organizer edits:

```http
PATCH /api/v1/events/abc
If-Match: "12"
```

If version remains 12:

```text
update succeeds
version → 13
```

If another organizer already changed it:

```text
version = 13
```

return:

```http
412 Precondition Failed
```

Frontend displays:

> This event changed while you were editing it. Review the latest version before saving again.

This prevents silent lost updates.

---

# 18. Discussion design

Do not try to recreate Telegram.

Comments are **structured planning comments**.

Three channels:

```text
GENERAL
TIME
LOCATION
```

Example:

```text
TIME
"Could we start at 8:30 instead?"

LOCATION
"The Shibuya location is fully booked."

GENERAL
"Anyone coming from Takanawa?"
```

Support one level of replies.

Not Reddit-style arbitrary nesting.

Schema:

```sql
comments

id                  UUID PRIMARY KEY
event_id            UUID NOT NULL
author_id           UUID NOT NULL
section             VARCHAR(20) NOT NULL
parent_comment_id   UUID
body                VARCHAR(2000) NOT NULL
created_at          TIMESTAMPTZ NOT NULL
edited_at           TIMESTAMPTZ
deleted_at          TIMESTAMPTZ
```

Comments are plain text.

No raw HTML.

---

# 19. Real-time architecture

Use **WebSockets**, but importantly:

### WebSockets are NOT the source of truth.

PostgreSQL + REST are.

WebSockets merely tell clients:

> something changed; refresh this data.

This dramatically reduces synchronization complexity.

Message:

```json
{
  "type": "RSVP_UPDATED",
  "eventId": "abc",
  "changeId": "019...",
  "occurredAt": "2026-09-24T11:23:41Z"
}
```

Client receives it and runs conceptually:

```typescript
queryClient.invalidateQueries(["event", eventId]);
queryClient.invalidateQueries(["event", eventId, "rsvps"]);
```

Why this architecture?

Because WebSocket delivery can be duplicated, delayed or briefly disconnected.

We should not attempt to maintain a perfect replicated client-side event state.

On reconnect:

```text
refetch everything relevant
```

Therefore lost realtime messages cannot corrupt persistent state.

---

# 20. Custom WebSocket protocol

Do **not** add STOMP.

Our protocol is tiny.

Connection:

```text
wss://api.../ws
```

First client message:

```json
{
  "type": "AUTH",
  "token": "<short-lived-jwt>"
}
```

Server:

```json
{
  "type": "AUTH_OK"
}
```

Then:

```json
{
  "type": "SUBSCRIBE_EVENT",
  "eventId": "..."
}
```

Server checks membership before subscribing.

Heartbeat:

```text
30 seconds
```

Reconnect:

```text
1s
2s
4s
8s
max 30s
```

with jitter.

After reconnect:

```text
authenticate
subscribe
refetch event state
```

---

# 21. Why not Redis Pub/Sub?

Because initial deployment has **one backend instance**.

WebSocket subscriber maps can remain in process.

That is a conscious scalability boundary.

Document the future evolution:

```text
single instance:
Spring memory → WebSocket clients

multiple backend instances:
PostgreSQL
    ↓
Redis Pub/Sub
    ↓
backend instances
    ↓
their connected WebSocket clients
```

But do not implement it until needed.

That's better engineering than pretending a 50-user campus app needs distributed pub/sub.

---

# 22. Transactional outbox

This is the third major technical centerpiece.

Suppose organizer changes:

```text
19:00 → 20:00
```

We need:

1. database update;
2. WebSocket broadcast;
3. Telegram notifications.

Bad approach:

```text
UPDATE database
send Telegram
broadcast WebSocket
```

What happens if the server crashes after the database update but before Telegram?

Persistent state changed, notification disappeared.

Instead:

```text
BEGIN

UPDATE event

INSERT INTO outbox_events(
    type = EVENT_TIME_CHANGED
)

COMMIT
```

The state change and the record saying:

> this notification work must happen

commit atomically.

---

# 23. `outbox_events`

```sql
id              UUID PRIMARY KEY
aggregate_type  VARCHAR(50)
aggregate_id    UUID
event_type      VARCHAR(80)
payload         JSONB
occurred_at     TIMESTAMPTZ
processed_at    TIMESTAMPTZ
```

Worker:

```text
SELECT ...
FROM outbox_events
WHERE processed_at IS NULL
ORDER BY occurred_at
FOR UPDATE SKIP LOCKED
LIMIT 50
```

For each event:

1. broadcast appropriate WebSocket invalidation;
2. create notification deliveries;
3. mark outbox event processed.

Duplicate WebSocket broadcasts are harmless.

Notification creation is protected by a uniqueness constraint.

---

# 24. Notification delivery table

```sql
notification_deliveries

id                  UUID PRIMARY KEY
outbox_event_id     UUID NOT NULL
recipient_user_id   UUID NOT NULL
channel             VARCHAR(20)
status              VARCHAR(20)

attempt_count       INTEGER
next_attempt_at     TIMESTAMPTZ
locked_until        TIMESTAMPTZ

telegram_message_id BIGINT

created_at          TIMESTAMPTZ
sent_at             TIMESTAMPTZ

UNIQUE (
    outbox_event_id,
    recipient_user_id,
    channel
)
```

States:

```text
PENDING
IN_PROGRESS
RETRY
SENT
FAILED_FINAL
SKIPPED
```

---

# 25. Retry behavior

Do not hold a database transaction open while calling Telegram.

Worker:

```text
claim delivery
↓
COMMIT
↓
HTTP call to Telegram
↓
mark SENT / RETRY
```

Retry schedule:

```text
5 sec
30 sec
2 min
10 min
1 hour
```

maximum:

```text
5 attempts
```

Telegram `429` responses should honor Telegram's retry delay rather than our normal backoff.

---

# 26. Notification rules

Per-event subscription:

```text
ALL_ACTIVITY
IMPORTANT_ONLY
MUTED
```

Defaults:

```text
Organizer        ALL_ACTIVITY
Going            ALL_ACTIVITY
Maybe            ALL_ACTIVITY
No RSVP          IMPORTANT_ONLY or none
```

`IMPORTANT_ONLY`:

```text
event cancelled
time changed
location changed
waitlist promoted
event starting soon
```

`ALL_ACTIVITY` additionally includes:

```text
new comment
new time proposal
schedule finalized
```

---

# 27. Telegram integration

The bot has four responsibilities.

### Identity/onboarding

`/start`

### Community membership

`/join`

### Event discovery/sharing

Telegram event cards contain:

```text
🎤 Karaoke in Shinjuku
Fri Sep 25 · 8 PM
7 going · 4 maybe

[ Open in Gather ]
```

The button uses:

```text
t.me/gatherbot/gather?startapp=event_<id>
```

Telegram supports direct Mini App links with `startapp` parameters precisely for this sort of shared collaborative interface. ([Telegram Core][1])

### Notifications

Bot DMs participating users.

No photo storage.

No event-chat replacement.

---

# 28. Idempotent Telegram webhooks

Telegram sends an `update_id`.

Store processed IDs:

```sql
telegram_updates

update_id       BIGINT PRIMARY KEY
received_at     TIMESTAMPTZ
processed_at    TIMESTAMPTZ
```

Webhook receives update:

```text
attempt INSERT update_id

success:
    process

unique violation:
    already handled
    return HTTP 200
```

This prevents duplicate commands/events when Telegram retries delivery.

---

# 29. Telegram webhook security

Configure a webhook secret and verify the Telegram secret header before parsing the payload.

Reject invalid requests:

```http
401 Unauthorized
```

Webhook endpoint is the only endpoint not requiring normal Gather authentication.

---

# 30. Sharing into Telegram

I would enable the bot's **inline mode**.

User taps:

> Share to Telegram

Gather invokes Telegram's inline sharing flow.

Bot produces the event card.

Why?

Because Mini Apps opened from direct links do not simply receive arbitrary permission to post into the surrounding chat; Telegram's documented flow intentionally constrains this and provides inline mechanisms for sharing. ([Telegram Core][1])

This is much cleaner than asking the bot to silently post everywhere.

---

# 31. Calendar integration — deliberately simple V1

For a finalized event provide:

### Add to Google Calendar

Generate the standard pre-populated Google Calendar event link.

No OAuth.

### Download calendar file

```http
GET /api/v1/events/{id}/calendar.ics
```

returns:

```text
text/calendar
```

This works with:

* Apple Calendar;
* Outlook;
* Google Calendar import;
* many other clients.

---

# 32. Why I do NOT use Google OAuth initially

We do **not** need permission to somebody's calendar merely to let them add an event.

OAuth creates:

* consent-screen configuration;
* token storage;
* token refresh;
* revocation handling;
* privacy concerns;
* possible Google verification requirements.

Google explicitly recommends requesting the narrowest Calendar scope needed, and public applications requesting user-data scopes can face verification requirements. ([Google for Developers][4])

So V1 deliberately avoids it.

---

# 33. Google Calendar Free/Busy — Phase 2

Once manual scheduling works, optional enhancement:

> Import my availability from Google Calendar.

Request only:

```text
calendar.freebusy
```

rather than broad calendar access.

Google provides a dedicated free/busy scope. ([Google for Developers][4])

Gather should store only derived unavailable intervals required for that scheduling session—not complete meeting titles/descriptions.

That is both privacy-preserving and technically cleaner.

---

# 34. Event feed

Home view should answer:

> What's happening?

Sections:

```text
Happening soon

Today

This week

My events

Past events
```

Event card:

```text
🎳 Bowling in Shinjuku

Friday · 7:30 PM
Shinjuku

8 Going
3 Maybe

[Maybe] [Going]
```

Flexible event:

```text
🍜 Dinner

Friday evening
Time not finalized

11 interested
Best current time: 8:00 PM

[Add availability]
```

---

# 35. Discovery does NOT need recommendation AI

Sort primarily by:

```text
event start time
```

and optionally:

```text
recently created
events requiring your availability
```

No ML recommender.

No "AI-powered event suggestions."

The technical story is already strong.

---

# 36. Event creation workflow

Create event wizard:

### Step 1 — basics

```text
Title
Description
Location
Location URL
Capacity optional
```

### Step 2 — scheduling mode

```text
Fixed time
Flexible time
```

### Fixed

```text
Date
Start
End
```

### Flexible

```text
Earliest possible time
Latest possible time
Required duration
```

Granularity:

```text
30 minutes
```

Do not let users configure arbitrary granularity in V1.

One setting is enough.

---

# 37. Input limits

Server-enforced:

```text
title                3–120 characters
description          <= 2000
location             <= 250
comment              1–2000
flexible window      <= 14 days
duration             30–480 minutes
```

Fixed event:

```text
end > start
```

Flexible:

```text
window_end > window_start
duration <= window size
```

No client validation substitutes for server validation.

---

# 38. Time handling

This is important.

Store every actual timestamp in:

```text
UTC / TIMESTAMPTZ
```

Store event timezone separately:

```text
Asia/Tokyo
```

Never store ambiguous values like:

```text
2026-09-25 20:00
```

without timezone context.

Java:

```text
Instant
ZoneId
ZonedDateTime
```

Browser:

use an IANA-aware date library.

A cohort moves cities every semester, so timezone modeling is not optional.

---

# 39. REST API

Version everything:

```text
/api/v1
```

## Authentication

```http
POST /api/v1/auth/telegram
GET  /api/v1/me
```

---

## Communities

```http
GET /api/v1/communities
GET /api/v1/communities/{communityId}
GET /api/v1/communities/{communityId}/events
```

---

## Events

```http
POST  /api/v1/communities/{communityId}/events
GET   /api/v1/events/{eventId}
PATCH /api/v1/events/{eventId}
POST  /api/v1/events/{eventId}/cancel
POST  /api/v1/events/{eventId}/lock
```

PATCH requires:

```http
If-Match
```

---

## RSVP

Make setting RSVP idempotent:

```http
PUT /api/v1/events/{eventId}/rsvp
```

Body:

```json
{
  "status": "GOING"
}
```

Delete:

```http
DELETE /api/v1/events/{eventId}/rsvp
```

---

# 40. Availability API

Replace the user's entire availability atomically:

```http
PUT /api/v1/events/{eventId}/availability
```

Body:

```json
{
  "intervals": [
    {
      "start": "2026-09-25T10:00:00Z",
      "end": "2026-09-25T12:00:00Z"
    },
    {
      "start": "2026-09-25T13:00:00Z",
      "end": "2026-09-25T15:00:00Z"
    }
  ]
}
```

Backend validates and canonicalizes.

---

## Recommendations

```http
GET /api/v1/events/{eventId}/schedule-recommendations
```

Response:

```json
{
  "slots": [
    {
      "start": "...",
      "end": "...",
      "goingAvailable": 13,
      "maybeAvailable": 3,
      "score": 29
    }
  ]
}
```

---

## Finalize

```http
POST /api/v1/events/{eventId}/schedule/finalize
If-Match: "8"
```

Body:

```json
{
  "start": "...",
  "end": "..."
}
```

---

# 41. Comments API

```http
GET  /api/v1/events/{eventId}/comments
POST /api/v1/events/{eventId}/comments

PATCH  /api/v1/comments/{commentId}
DELETE /api/v1/comments/{commentId}
```

Filters:

```text
?section=TIME
?section=LOCATION
?section=GENERAL
```

---

# 42. Notification API

```http
GET /api/v1/me/notifications

PUT /api/v1/events/{eventId}/notification-preference
```

Body:

```json
{
  "level": "IMPORTANT_ONLY"
}
```

---

# 43. API error format

Use Spring `ProblemDetail`.

Example:

```json
{
  "type": "https://gather.app/problems/version-conflict",
  "title": "Event changed",
  "status": 412,
  "detail": "The event was modified after you loaded it."
}
```

Don't invent ten unrelated error shapes.

---

# 44. Backend architecture

Package **by feature**, not:

```text
controllers/
services/
repositories/
```

for the entire application.

Use:

```text
com.gather

auth/
    AuthController
    TelegramAuthService
    JwtService

community/
    Community
    CommunityMembership
    CommunityController
    CommunityService

event/
    Event
    EventRsvp
    EventController
    EventService
    EventRepository

scheduling/
    AvailabilityInterval
    SchedulingEngine
    SchedulingController

discussion/
    Comment
    CommentService

notification/
    OutboxEvent
    OutboxDispatcher
    NotificationDelivery
    TelegramNotificationWorker

telegram/
    TelegramWebhookController
    TelegramBotClient
    TelegramUpdateService

realtime/
    GatherWebSocketHandler
    WebSocketRegistry

common/
    errors
    security
    time
```

This is a **modular monolith** with explicit domain boundaries.

---

# 45. Transaction boundaries

Every state-changing use case should have an explicit service method.

Example:

```text
EventService.updateEvent()
```

transaction:

```text
read event
authorize organizer
validate version
modify event
insert outbox event
commit
```

Do not let controllers manipulate repositories directly.

---

# 46. Database indexes

At minimum:

```sql
CREATE INDEX idx_events_community_start
ON events(community_id, start_at);

CREATE INDEX idx_events_community_status
ON events(community_id, status);

CREATE INDEX idx_rsvps_event_status
ON event_rsvps(event_id, status);

CREATE INDEX idx_comments_event_created
ON comments(event_id, created_at);

CREATE INDEX idx_availability_event_user
ON availability_intervals(event_id, user_id);

CREATE INDEX idx_outbox_pending
ON outbox_events(processed_at, occurred_at)
WHERE processed_at IS NULL;

CREATE INDEX idx_delivery_pending
ON notification_deliveries(status, next_attempt_at);
```

Performance work later should be based on actual query plans, not random indexing.

---

# 47. Database migrations

Never allow Hibernate to mutate production schema automatically.

Development:

```text
ddl-auto=validate
```

Flyway owns schema evolution.

Example:

```text
V001__initial_users_communities.sql
V002__events.sql
V003__rsvps.sql
V004__availability.sql
V005__comments.sql
V006__transactional_outbox.sql
```

---

# 48. Frontend state model

Use TanStack Query for **server state**.

Examples:

```text
["communities"]
["events", communityId]
["event", eventId]
["rsvps", eventId]
["comments", eventId, section]
["availability", eventId]
["scheduleRecommendations", eventId]
```

Do not duplicate these into Redux.

We do not need Redux.

Local component state handles:

```text
form steps
selected availability cells
modal visibility
temporary UI state
```

---

# 49. Optimistic UI

Safe operations like RSVP can update optimistically.

Example:

```text
Going: 7 → 8 immediately
```

Then:

```text
request succeeds → retain
request fails → rollback
```

Major organizer edits should **not** be silently optimistic because conflict detection matters.

---

# 50. Core frontend screens

```text
/auth
/home
/events/new
/events/[id]
/events/[id]/availability
/my-events
/past
/settings
```

Inside Telegram there should be no unnecessary desktop navbar.

Bottom navigation:

```text
Home
My Events
Create
Settings
```

---

# 51. Event detail screen composition

```text
EventHeader
    title
    organizer
    location
    current schedule

RsvpBar
    Going
    Maybe

CapacityStatus
    if applicable

FlexibleSchedulePanel
    if not finalized

ParticipantSummary

DiscussionTabs
    General
    Time
    Location

OrganizerControls
    conditional
```

---

# 52. Telegram theming

Use Telegram theme parameters as CSS variables where available.

Example:

```text
--tg-theme-bg-color
--tg-theme-text-color
--tg-theme-button-color
```

Fallback to application's own theme in browser development.

Telegram's current design guidance specifically recommends adapting to Telegram theme colors and respecting WebView safe areas. ([Telegram Core][1])

---

# 53. Accessibility

Do not treat this as optional polish.

Required:

* all interactive elements keyboard accessible;
* labels on inputs;
* sufficient contrast;
* visible focus;
* `aria-live` for realtime RSVP updates where appropriate;
* availability cells have textual accessible labels, not color alone.

Telegram's Mini App guidance also explicitly calls out labels/accessibility. ([Telegram Core][1])

---

# 54. Security model

Major rules:

```text
TLS everywhere

Telegram initData verified server-side

JWT short lived

JWT stored in memory only

strict CORS allowlist

comments plain text

authorization checked server-side

Telegram webhook secret validated

all SQL parameterized through ORM/query APIs

never trust community/event IDs from client

rate-sensitive endpoints constrained
```

Event authorization:

```text
must be community member
```

Organizer actions:

```text
must be event organizer
OR community admin
```

---

# 55. JWT choice

JWT payload only:

```json
{
  "sub": "<gather-user-id>",
  "telegramUserId": 123456789,
  "iat": "...",
  "exp": "..."
}
```

Do not put full community membership into JWT because memberships can change.

Authorization checks database membership.

JWT lifetime:

```text
60 minutes
```

Re-authentication is trivial because Telegram already provides fresh context when the Mini App opens.

---

# 56. OpenAPI contract

Backend exposes:

```text
/api/openapi.json
```

Generate frontend types using:

```text
openapi-typescript
```

Commit generated types or regenerate during CI.

Objective:

if Java response changes from:

```text
goingCount
```

to:

```text
confirmedCount
```

frontend type checking fails rather than silently breaking.

---

# 57. Repository structure

```text
gather/
│
├── apps/
│   ├── web/
│   │   ├── app/
│   │   ├── components/
│   │   ├── features/
│   │   ├── lib/
│   │   ├── generated/
│   │   └── tests/
│   │
│   └── api/
│       ├── src/main/java/
│       ├── src/main/resources/
│       └── src/test/
│
├── infra/
│   ├── docker-compose.yml
│   └── postgres/
│
├── docs/
│   ├── architecture.md
│   ├── api.md
│   ├── benchmarks.md
│   └── adr/
│
├── load-tests/
│   └── k6/
│
├── scripts/
│
├── .github/
│   └── workflows/
│
└── README.md
```

Do not use Turborepo simply because it is fashionable.

This repository has two applications. Ordinary directories are enough.

---

# 58. Architecture Decision Records

I specifically want these committed:

```text
ADR-001 Telegram Mini App instead of native mobile app
ADR-002 Modular monolith instead of microservices
ADR-003 PostgreSQL without Redis/Kafka in V1
ADR-004 Transactional outbox for side effects
ADR-005 WebSockets used as cache invalidation, not state authority
ADR-006 Manual availability before Google Calendar OAuth
ADR-007 Interval-based availability representation
```

This gives reviewers concrete evidence that architectural choices were deliberate.

---

# 59. Local development

`docker compose up` should start:

```text
PostgreSQL
API
```

Frontend can run:

```bash
pnpm dev
```

or optionally in Docker too.

Backend:

```bash
./mvnw spring-boot:run
```

Provide:

```text
.env.example
```

Never commit secrets.

---

# 60. Testing strategy

This project should have **meaningful testing**, not "80% coverage" as a vanity target.

## Backend unit tests

Scheduling engine gets particularly strong tests.

Examples:

```text
one attendee
multiple attendees
overlapping availability
availability shorter than duration
ties
window boundaries
timezone transition
no possible time
```

---

# 61. Property-based-ish scheduling tests

Generate random availability intervals and compare:

```text
optimized range-add algorithm
```

against:

```text
simple brute-force reference implementation
```

for thousands of random cases.

The brute-force implementation becomes our correctness oracle.

This is an excellent test because we're optimizing a nontrivial algorithm.

---

# 62. Integration tests

Use **Testcontainers PostgreSQL**, not H2.

Why?

Features such as:

```text
FOR UPDATE SKIP LOCKED
JSONB
PostgreSQL locking semantics
```

need real PostgreSQL behavior.

Tests:

```text
concurrent event update conflict
last capacity slot race
waitlist promotion
outbox inserted atomically
duplicate Telegram update ignored
notification retry
membership authorization
```

---

# 63. Concurrency test

Specifically build:

```text
capacity = 1
```

Start 20 threads simultaneously attempting:

```text
RSVP GOING
```

Expected:

```text
1 GOING
19 WAITLISTED
```

Every time.

This is a strong correctness demonstration.

---

# 64. Frontend tests

Vitest + React Testing Library:

```text
event card
RSVP state
flexible availability grid
conflict dialog
schedule recommendations
```

Don't snapshot the entire application.

Test behavior.

---

# 65. E2E tests

Playwright:

```text
create event
RSVP
submit availability
finalize schedule
receive updated state
comment
cancel event
view past event
```

Use test Telegram-auth bypass **only in test environment**.

Never ship a production bypass.

---

# 66. Load testing

Use k6.

We need real performance measurements for both engineering validation and résumé bullets.

Scenarios:

### Scenario A

```text
100 concurrent users
browsing upcoming events
```

### Scenario B

```text
100 concurrent RSVP updates
```

### Scenario C

```text
500 WebSocket clients
subscribed across 25 events
```

### Scenario D

```text
500 users
7-day flexible scheduling window
30-minute granularity
```

### Scenario E

```text
Telegram webhook duplicate burst
```

---

# 67. Initial performance targets

Targets, **not résumé claims**:

```text
GET event p95          < 200 ms
RSVP write p95         < 300 ms
WebSocket invalidation < 500 ms p95
schedule ranking       < 100 ms for 500 attendees
5xx error rate         < 1% under designed load
```

If we don't meet them, profile and fix the actual bottleneck.

Then the résumé reports the measured result.

---

# 68. Benchmark the scheduling optimization

Implement two algorithms:

```text
NaiveSchedulingEngine
RangeAddSchedulingEngine
```

The naive one exists only in benchmark/test code.

Measure:

```text
100 users
500 users
1,000 users
5,000 users
```

Record:

```text
runtime
allocation
speedup
```

Commit results to:

```text
docs/benchmarks.md
```

This gives you a genuine technical-performance story.

---

# 69. Observability

Spring Boot Actuator:

```text
/actuator/health
/actuator/metrics
```

Track:

```text
HTTP latency
HTTP status counts
outbox backlog
notification success/failure
active WebSocket connections
scheduling-engine duration
```

Structured logs should include:

```text
requestId
eventId where relevant
operation
duration
```

Do not log:

```text
JWTs
Telegram initData
bot token
full private comment bodies
```

---

# 70. Product metrics for your résumé

Make these measurable from production data:

```text
registered users
weekly active users
events created
RSVPs submitted
availability submissions
comments
events finalized using recommendation
notification delivery success rate
```

Create:

```sql
user_activity_daily
```

```text
user_id
activity_date
PRIMARY KEY(user_id, activity_date)
```

Auth/session activity performs:

```text
INSERT ... ON CONFLICT DO NOTHING
```

Then DAU/WAU can be computed without third-party tracking.

---

# 71. CI

Every pull request:

### Frontend

```text
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

### Backend

```text
./mvnw spotless:check
./mvnw verify
```

`verify` includes Testcontainers integration tests.

### Schema

Run migrations against clean PostgreSQL.

### E2E

Run a smaller Playwright smoke suite.

Full load tests do **not** run on every PR.

They run manually before release.

---

# 72. Deployment

## Frontend

**Vercel**

Environment variables:

```text
NEXT_PUBLIC_API_URL
NEXT_PUBLIC_TELEGRAM_BOT_USERNAME
```

## Backend

**Railway Docker deployment**

Environment:

```text
DATABASE_URL
TELEGRAM_BOT_TOKEN
TELEGRAM_WEBHOOK_SECRET
JWT_SIGNING_SECRET
FRONTEND_ORIGIN
```

## Database

Railway managed PostgreSQL.

Run Flyway on backend startup.

Production Hibernate:

```text
ddl-auto=validate
```

---

# 73. Deployment migration safety

Before application becomes large, startup migrations are acceptable.

But migration rules:

```text
do not delete/rename columns destructively in same release
```

Use expand/migrate/contract when necessary.

Example:

bad:

```text
rename title → event_title immediately
```

better:

```text
add event_title
backfill
deploy code reading event_title
later remove title
```

This gives you realistic production-database discipline.

---

# 74. What explicitly does NOT belong in V1

This boundary should be given to Codex.

**Do not implement:**

```text
AI recommendations
LLMs
friend matching
media uploads
private messaging
native iOS
native Android
Kafka
Kubernetes
microservices
MongoDB
Redis
GraphQL
Elasticsearch
Google Calendar OAuth
complex social profiles
follower/friend graphs
payments
maps API
```

If Codex invents any of those, reject the change.

---

# 75. Implementation sequence

The order matters because we want a working vertical slice early.

## Phase 0 — repository/bootstrap

Implement:

```text
monorepo structure
Next.js
Spring Boot
PostgreSQL
Docker Compose
Flyway
GitHub Actions
health endpoint
```

Acceptance:

```text
frontend loads
backend health returns 200
backend connects to PostgreSQL
CI green
```

---

# 76. Phase 1 — Telegram authentication

Implement:

```text
Telegram Mini App initialization
server-side initData verification
User creation/upsert
JWT issuing
GET /me
authorization middleware
```

Acceptance:

Real Telegram user can:

```text
open Mini App
authenticate
see their own Telegram identity
```

Fake/tampered initData is rejected.

---

# 77. Phase 2 — community membership

Implement:

```text
Community
CommunityMembership
/setup
/join
community event feed shell
```

Acceptance:

A student runs `/join` inside registered Telegram group and subsequently sees that community in Gather.

Non-member receives:

```text
403
```

for private community endpoints.

---

# 78. Phase 3 — fixed events end to end

Implement:

```text
create fixed event
view event
edit event
cancel event
event feed
organizer authorization
ETag/versioning
```

Acceptance:

Organizer creates event.

Another user can open it.

Stale organizer edit reliably returns:

```text
412
```

---

# 79. Phase 4 — RSVP + capacity/waitlist

Implement:

```text
GOING
MAYBE
WAITLISTED
remove RSVP
capacity
automatic promotion
transactional locking
```

Acceptance:

20-user concurrency test against capacity 1 produces exactly:

```text
1 GOING
19 WAITLISTED
```

---

# 80. Phase 5 — realtime

Implement:

```text
WebSocket server
AUTH
SUBSCRIBE_EVENT
broadcast invalidations
TanStack Query invalidation
reconnect/refetch
```

Acceptance:

Two browsers open same event.

Browser A RSVPs.

Browser B updates without manual refresh.

Disconnect/reconnect and Browser B still converges to correct server state.

---

# 81. Phase 6 — discussion

Implement:

```text
GENERAL
TIME
LOCATION
one-level replies
edit own comment
soft delete own comment
WebSocket invalidations
```

Acceptance:

New comments appear to other connected users in realtime.

---

# 82. Phase 7 — flexible scheduling

Implement first:

```text
brute-force reference engine
```

Then:

```text
optimized range-add engine
```

Also:

```text
availability UI
availability persistence
recommendation endpoint
organizer finalize flow
```

Acceptance:

Optimized and naive engines produce identical results across randomized test cases.

Benchmark demonstrates the optimization.

---

# 83. Phase 8 — transactional outbox

Refactor state changes to create:

```text
outbox events
```

Implement:

```text
dispatcher
FOR UPDATE SKIP LOCKED
WebSocket dispatch
```

Acceptance:

Any event mutation atomically creates corresponding outbox row.

Crashing/retrying dispatcher does not corrupt state.

---

# 84. Phase 9 — Telegram notifications

Implement:

```text
notification delivery table
recipient calculation
Telegram Bot API client
retry/backoff
notification preferences
```

Acceptance:

Going/Maybe users receive schedule-change notification.

Muted users do not.

Duplicate outbox processing never creates duplicate delivery rows.

---

# 85. Phase 10 — Telegram event cards

Implement:

```text
event deep link
inline sharing
event card
bot launch flow
```

Acceptance:

User can share a structured Gather event from the Mini App into Telegram and another community member can open it in one tap.

---

# 86. Phase 11 — calendar

Implement:

```text
Google Calendar URL
.ics generation
```

Acceptance:

Finalized Gather event opens correctly pre-populated in Google Calendar.

`.ics` successfully imports into at least Google/Apple Calendar.

---

# 87. Phase 12 — polish

Implement:

```text
responsive Mini App UX
Telegram theme integration
loading/error/empty states
accessibility
past-events view
my-events view
notification preferences
```

This is when visual quality matters.

Not before core correctness.

---

# 88. Phase 13 — performance work

Run k6.

Do not optimize blindly.

Capture first benchmark.

Example:

```text
p95 RSVP:
410 ms
```

Profile.

Suppose query is bad.

Fix it.

Re-run:

```text
p95 RSVP:
92 ms
```

Then document:

```text
410 → 92 ms
77.6% reduction
```

That becomes résumé material.

Never invent the metric in advance.

---

# 89. Phase 14 — real-user launch

Start with your actual cohort.

Initial target:

```text
10–20 users
```

Then:

```text
30+
```

Ask people to use it for actual events.

Do not fabricate load as "users."

Load-test traffic and real-user traffic are different metrics.

Track both accurately.

---

# 90. README requirements

README should be unusually good.

Opening:

```text
Gather
Telegram-native coordination for spontaneous campus events.
```

Then:

```text
Problem
Demo GIF/screenshots
Architecture diagram
Core technical challenges
Tech stack
Scheduling algorithm
Consistency model
Transactional outbox
Local development
Testing
Benchmarks
Deployment
```

Do **not** begin with a giant logo and 30 technology badges.

---

# 91. What makes this project technically serious

After this implementation, the technical story is no longer:

> I made a social event app.

It becomes:

### Algorithmic engineering

Efficient interval-based schedule optimization.

### Database/concurrency engineering

Optimistic event updates, transactional RSVP capacity, waitlist promotion.

### Realtime engineering

WebSocket invalidation and eventual client convergence.

### Reliability engineering

Transactional outbox, idempotent Telegram webhooks, durable retryable notifications.

### Integration engineering

Telegram Mini Apps/Bot API + calendar interoperability.

### Product engineering

Real users solving a real community coordination problem.

That's the portfolio shape we wanted.

---

# 92. Target final résumé outcome

I would design the project so something like this can eventually become **truthful**:

**Gather — Real-Time Campus Coordination Platform**
*Java, Spring Boot, Next.js, TypeScript, PostgreSQL, WebSockets*

> • Built a Telegram Mini App used by **[N]+ students across [M]+ events**, combining structured RSVPs, collaborative availability planning, real-time discussion, waitlists, and calendar synchronization.
> • Engineered PostgreSQL-backed concurrency control, idempotent Telegram webhooks, transactional outbox processing, and WebSocket state invalidation to maintain consistent event state across **[N] concurrent clients**.
> • Designed an interval-based scheduling engine that ranks attendance-maximizing event windows in **O(A + S)**, improving recommendation latency by **[X×]** over a brute-force baseline; optimized backend performance to **[X ms] p95** under **[Y] concurrent users**.

Notice that those bullets now have **three distinct technical stories** rather than twenty feature names.

---

## The instruction I would put at the very top of your Codex task

> **Implement Gather incrementally according to this specification. Do not add technologies, services, frameworks, product features, database tables, or architectural abstractions that are not justified here. Prefer the simplest implementation satisfying the stated correctness and performance requirements. Treat PostgreSQL as the source of truth; preserve transactional integrity; write tests for every concurrency-sensitive path. Complete phases sequentially and ensure the acceptance criteria for a phase pass before implementing the next phase. Do not fabricate benchmark numbers or seed them into documentation—benchmarks must be generated from executed tests.**

That final constraint is especially important if you're using an agent to build this: **we want Codex to implement the sophistication we intentionally designed, not make the project look sophisticated by spraying infrastructure everywhere.**

[1]: https://core.telegram.org/bots/webapps?utm_source=chatgpt.com "Telegram Mini Apps"
[2]: https://nextjs.org/blog?utm_source=chatgpt.com "Next.js by Vercel - The React Framework | Next.js by Vercel - The React Framework"
[3]: https://docs.spring.io/spring-boot/?utm_source=chatgpt.com "Spring Boot :: Spring Boot"
[4]: https://developers.google.com/workspace/calendar/api/auth?utm_source=chatgpt.com "Choose Google Calendar API scopes  |  Google for Developers"
