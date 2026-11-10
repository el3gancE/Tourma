# Database and request performance

All DAOs obtain leases through `DBContext` and `JdbcConnectionPool`.
The pool limits physical connections, including connections currently in use.
It validates idle connections before reuse, closes outstanding statements, rolls
back uncommitted transactions and resets JDBC session settings on return.
Closing a lease twice does not return capacity twice. Closed leases cannot be used.
SQL exceptions retain their original type rather than being wrapped by reflection.
The application lifecycle listener closes the pool on undeploy; in-flight leases
are discarded on return rather than retained by an old application classloader.

## Defaults and configuration

Set JVM `-D` properties before starting/reloading the application:

| Property | Default | Meaning |
| --- | --- | --- |
| `tourma.db.poolSize` | 12 | Maximum physical connections per application instance |
| `tourma.db.acquireTimeoutMs` | 5000 | Maximum wait for a pool permit |
| `tourma.db.loginTimeoutSeconds` | 5 | Driver login timeout |
| `tourma.db.queryTimeoutSeconds` | 20 | Default deadline for newly created statements |
| `tourma.db.socketTimeoutMs` | 45000 | Driver network-read timeout |
| `tourma.http.maxConcurrentRequests` | 6 | Concurrent dynamic requests per application instance |

Dynamic requests wait at most one second for admission. When saturated, new
requests receive HTTP 503 and `Retry-After: 2`; writes are never automatically
retried. CSS, JavaScript, fonts and image paths bypass dynamic admission.
Keep database capacity above request concurrency: existing transactional flows
sometimes open a second connection through another DAO.
Statements can explicitly override their default timeout for a known operation.
These are operation deadlines, not a total end-to-end request deadline.

Slow statement executions and dynamic requests (one second or more) are logged.
Request logs include a generated `X-Request-ID`, path, elapsed time, status and
pool occupancy. They omit query parameters, form values, SQL text and credentials.

## SQL memory pressure

On 2026-11-10 the local SQL instance reported low physical memory, with only
about 550 MB available on the 16 GB machine. Queries were waiting on
`RESOURCE_SEMAPHORE` rather than row locks. That is a machine/server condition;
pool admission and deadlines limit its impact but do not create available RAM.
No server memory setting, database isolation level or server-wide query hint is
changed by this implementation.

Use the SQL DMVs below during a slow period. These checks do not change data:

```sql
SELECT total_physical_memory_kb, available_physical_memory_kb,
       system_memory_state_desc FROM sys.dm_os_sys_memory;
SELECT physical_memory_in_use_kb, process_physical_memory_low
FROM sys.dm_os_process_memory;
SELECT session_id, requested_memory_kb, granted_memory_kb,
       required_memory_kb, wait_time_ms FROM sys.dm_exec_query_memory_grants;
SELECT session_id, blocking_session_id, wait_type, wait_time, total_elapsed_time
FROM sys.dm_exec_requests WHERE database_id = DB_ID();
```

The profile win/loss snapshot is shared per Series until application data changes. Concurrent
misses use one loader; `RollingWindowPointService.clearAllCaches()` invalidates it
on score/participant changes. It no longer expires every 60 seconds: that expiry
reintroduced the slow SQL read after brief idle periods. Out-of-band SQL edits
require explicit recalculation or application reload to invalidate this snapshot.
A generation check prevents an older in-flight read
from becoming a valid cache entry after invalidation. Failed queries are not cached.

The shared tournament summary now aggregates within each selected tournament
instead of globally grouping all matches. `TournamentSummaryCheck` compares the
three counts against raw rows; this passed for all 98 tournaments in the tested
Series. A full DAO read took about 931 ms in that run; a separate cold summary
read took about 10 seconds under memory pressure. Neither is a latency guarantee.

## Verification and rollout

- `tests/dao/JdbcConnectionPoolTest.java`: bounded contention, transaction reset,
  broken connections, statement deadlines, exception types and permit release.
- `tests/filter/RequestTimingFilterTest.java`: overload response, asset bypass and
  release after application failures.
- `tests/integration/DatabaseResilienceCheck.java`: session-local temp-table
  rollback, real query cancellation and concurrent reads. Does not change domain data.
- `tests/integration/TournamentSummaryCheck.java <seriesId>`: raw match counts
  versus shared summaries and full DAO load.
- `tests/integration/ProfileMatchStatsCheck.java`: offline win/loss regression;
  with a Series ID it additionally compares the original SQL aggregate. That
  optional aggregate can still time out when SQL is under memory pressure.

Build with Ant, then reload/redeploy Tourma before evaluating HTTP behavior.
The local Tomcat context points at `build/web` but does not declare automatic
class reloading. Compilation alone does not prove that the running application
uses the new connection pool or filter. Tourma was reloaded locally for this check;
HTTP responses contained the new filter's request ID.

After reload, one cold standings read took 2815 ms and the first profile took
3317 ms. Four further profile reads across three teams took 122–143 ms.
A mixed run of 12 HTTP GETs with four workers (standings, profiles, tournament
list and Series list) returned HTTP 200 throughout: median 133 ms, maximum 433 ms.
These short read checks are evidence for the tested state, not sustained-load SLAs.

Remaining validation: sustained load and representative concurrent writes.
Some existing DAOs catch SQL failures and return
empty/fallback values; a query deadline does not by itself provide a consistent
error screen for those paths. The request admission limit also does not serialize
background jobs or eliminate duplicate cold-cache calculations.

## Browser follow-up

The earlier repeated-HTTP checks did not cover the statistics-cache expiry or
client initialization. Phase badge evaluation now treats an empty array as an
authoritative result, avoiding five fallback Series calculations for teams with
no Phase badges. Profile rendering builds detailed performance history only for
the selected team; standings and Phase winner calculations are preserved.
`tests/team-profile-badges.cjs` and `tests/team-profile-scope.cjs` cover these cases.
The profile root element exposes `data-profile-timing` for read-only diagnostics
(TTFB, HTML completion, client initialization and initialization completion).

Browser verification after the follow-up changes: opening Shane Van Boening from
the standings took 664 ms to complete profile initialization (97 ms TTFB,
177 ms HTML completion, 165 ms client initialization). A Joshua Filler reload
recorded 1736 ms total and 266 ms client initialization. A cold application
reload during the same investigation still incurred a much longer SQL read;
these browser measurements do not establish a cold-start latency guarantee.
