# Executed scheduling benchmark

Local run: September 24, 2026, macOS arm64, Homebrew Java 21.0.12.1. Single JVM, 100 warm-up iterations and 200 measured iterations per algorithm and size. Seven-day window, 30-minute granularity, two-hour event, one canonical interval per attendee. Allocations use the current thread's ThreadMXBean counter. These are microbenchmark means, not HTTP latency, p95, or production adoption.

| Attendees | Range-add mean ms | Reference mean ms | Range-add bytes/op | Reference bytes/op | Speedup |
| --- | ---: | ---: | ---: | ---: | ---: |
| 100 | 0.0230 | 0.2269 | 42136 | 816800 | 9.86× |
| 500 | 0.0234 | 0.8467 | 64824 | 3922400 | 36.12× |
| 1000 | 0.0329 | 1.7526 | 100224 | 7804400 | 53.33× |
| 5000 | 0.1204 | 8.7780 | 383424 | 38860400 | 72.94× |

Reproduce: `bash scripts/check-api.sh test -Dtest=SchedulingBenchmarkTest -Dgather.benchmark=true`.

Scoring excludes interval normalization, database queries, serialization and network. The naive oracle is deliberately simple and allocates candidate objects and iterators; speedup depends on that baseline and this dataset. Timing is susceptible to JIT, GC, and other machine work. Multi-fork statistical benchmarking remains a limitation. HTTP/WebSocket measurements follow below.


## Executed HTTP and WebSocket load tests

September 24, 2026. Apple M4 Pro, 24 GiB host memory, macOS arm64, Java 21.0.12.1 and k6 2.3.0 on the host. PostgreSQL 17 runs in Colima (2 vCPU, 4 GiB). One backend instance, default 10-connection Hikari pool, local HTTP/WebSocket traffic. Normal request completion logging enabled. No Telegram network delivery or deployed-hosting latency is included. Each case runs separately; frontend builds and browser tests were excluded from this final load run.

Dataset: 500 synthetic enrolled attendees, 25 fixed events with capacity 500, and one seven-day flexible event with 500 Going attendees, one canonical full-window interval each, 120-minute duration and 30-minute candidate spacing. `scripts/load-seed.sql` creates the data; `scripts/load-reset.sql` clears fixed-event attendance between comparable runs. These are synthetic users, not adoption.

| Scenario | Measured p95 | Target | Checks passed |
| --- | ---: | ---: | ---: |
| Event reads (100 users) | 31.97 ms | <200 ms | 5800 / 5800 |
| RSVP writes (100 users, one event) | 251.46 ms | <300 ms | 500 / 500 |
| Invalidations (500 clients / 25 events) | 300.00 ms | <500 ms | 1000 / 1000 |
| Scheduling HTTP (500 attendees, 10 callers) | 18.32 ms | <100 ms | 710 / 710 |
| Duplicate webhook burst (100 users) | 27.51 ms | No latency target | 1000 / 1000 |

All configured thresholds passed; each scenario had zero observed 5xx responses and 100% functional checks. Raw k6 summaries are in [load-results](load-results). The feed scenario measures event-read latency while also browsing the community feed. RSVP executes 100 virtual users × five alternating Going/Maybe mutations against the same event. WebSocket clients connect for ten seconds, perform a mutation after subscription, and measure committed outbox `occurredAt` to receipt; 500 subscriptions were observed. Feed and scheduling run for 15 seconds; duplicate webhooks run 1,000 requests.

## Profiling and changes

The first valid baseline missed RSVP (437.02 ms p95) and WebSocket (3597.70 ms p95); raw baseline summaries are retained. Broadcasts originally queried membership per connected client. They now authorize subscribers with one membership query per event, retain immediate user-revocation callbacks, and recheck expiration. The outbox poll interval decreased from 100 to 25 ms and batch size increased from 50 to 200.

RSVP writes now return their participant summary after transaction commit; the summary uses one authorized SQL query. Promotion runs only when attendance releases a seat, selects FIFO users and updates them in one statement. Outbox snapshots use the already-loaded immutable mutation state. The event row lock remains mandatory.

A 30-second JFR profile of an intermediate failing RSVP run recorded 491 PostgreSQL socket waits totalling 9.074 thread-seconds and 1,861 SynchronousQueue park samples totalling 91.38 thread-seconds. Only eight CPU execution samples were captured; this short profile does not establish a precise CPU bottleneck. It was consistent with connection contention from writers waiting on one event while occupying JDBC connections. A bounded, fair, 256-stripe admission queue now admits HTTP RSVP mutations before opening their transaction. It reduces connection starvation; it is an optimization, not a replacement for database locking. Stripe collisions can serialize unrelated events. Direct service concurrency tests intentionally bypass the queue and still enforce capacity correctly.

An inherited host `DEBUG=release` also enabled Spring debug logging. Verification scripts force DEBUG=false; final measurements retain normal request logs. Baseline and final logging conditions therefore differ, so changes in latency cannot be attributed to one code change alone.

These are short single-machine acceptance runs, not sustained-capacity claims. They do not measure WAN latency, real Telegram delivery, TLS proxies, production backup activity, or multi-instance behavior. Rerun on the selected hosting tier before release. The summaries below come from the recorded test runs.
