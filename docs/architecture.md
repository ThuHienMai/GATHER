# Architecture

Gather has a Next.js frontend, one Spring Boot backend, and PostgreSQL. Telegram supplies the signed login data and sends bot commands to the backend webhook. The frontend uses REST for data and WebSockets for update notifications.

## Authentication

The backend validates Telegram launch data and issues a 60-minute JWT. The frontend keeps it in memory. Community membership is checked on every protected request and WebSocket subscription.

`/setup` requires both the user and bot to be group administrators. `/join` enrolls a member. Leaving the group revokes access and releases upcoming reservations. Community administrators can manage events whose organizers have left.

## Events and attendance

An event row lock serializes RSVP and capacity changes. Waiting members are promoted in order of their waitlist-entry time, with UUID as a tie-breaker. HTTP RSVP requests also pass through a bounded admission queue so a busy event does not occupy every database connection.

Organizer edits require an `If-Match` version. RSVPs and comments do not change that version, so they do not invalidate an open edit form.

## Scheduling

Availability is stored as merged intervals, visible only to its owner. The scheduler scores possible start times in 30-minute increments using Going and Maybe attendees. Waitlisted members count only after promotion. Finalizing selects a time and locks the schedule.

The scoring algorithm uses difference arrays to count attendance across candidate slots. A brute-force reference implementation in the tests checks its results.

## Updates and notifications

Each mutation writes an outbox record in the same transaction. A worker dispatches WebSocket notifications and creates Telegram delivery records. Clients refetch the affected data; reconnecting also triggers a refetch.

Telegram deliveries use leases and up to five attempts. Workers recheck membership, preferences, and event status before sending. A network failure after Telegram accepts a message can cause a duplicate on retry.

WebSocket connections belong to one backend process. Deploy one API replica until cross-instance broadcasting is implemented.
