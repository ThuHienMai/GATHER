# Load tests

Install k6, then start the local test stack from the repository root:

```sh
bash scripts/e2e-stack.sh start
docker compose -p gather-e2e -f infra/e2e-compose.yml exec -T postgres psql -U gather_test -d gather_test < scripts/load-seed.sql
docker compose -p gather-e2e -f infra/e2e-compose.yml exec -T postgres psql -U gather_test -d gather_test < scripts/load-reset.sql
mkdir -p load-tests/results
cd load-tests/k6
k6 run -e CASE=feed scenarios.js
k6 run -e CASE=rsvp scenarios.js
k6 run -e CASE=ws scenarios.js
k6 run -e CASE=schedule scenarios.js
k6 run -e CASE=webhook scenarios.js
cd ../..
bash scripts/e2e-stack.sh stop
```

Results are written to `load-tests/results/`, which is ignored by Git. Run the reset script before repeating the scenarios so existing RSVPs do not change the workload. The scenarios accept only local targets.

The scheduling microbenchmark runs separately:

```sh
bash scripts/check-api.sh test -Dtest=SchedulingBenchmarkTest -Dgather.benchmark=true
```
