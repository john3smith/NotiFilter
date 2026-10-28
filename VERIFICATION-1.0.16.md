# NotiFilter 1.0.16 - ordered pending-removal expiry

## Change

FilterService previously checked every pending removal on every new cancellation
request, invoking the elapsed clock for each entry. A burst of unconfirmed
requests could therefore check 1000 timestamps even when none had expired.
PendingRemovals now keeps requests in monotonic timestamp order and removes only
the expired prefix, stopping at the first valid entry. Refreshing an existing
key moves it to the tail. A single elapsed timestamp is captured per request.
Content extraction is lazy and does not run for a rejected new request at capacity.

The 1000-request cap, strictly-greater-than-30000ms expiry boundary, cancellation
error cleanup, disconnect/destroy clear, and removed-notification reason/age
checks are unchanged. No notification access, filtering rules, history schema,
encryption or UI change is introduced. The helper has the same main-thread-only
ownership as its former HashMap; it assumes monotonically offered timestamps.

## Validation and measurement

- 26 host unit tests passed, including six pending-removal tests.
- 20,000 seeded random offer/remove/clear operations match the previous full-scan
  implementation, including final remaining values.
- Exact expiry boundary, replacement ordering at capacity, removal/clear and lazy
  extraction on capacity rejection are covered.
- In a synthetic full set of 1000 unexpired requests, the previous implementation
  performed **1000 timestamp inspections**, while the new one performed **1**.
  A subsequent expiry of all 1000 requests inspected all 1000, as required.
  This is a deterministic operation-count measurement, not an elapsed-time,
  battery, memory or complete-notification-throughput benchmark.
- Release/test APK builds passed; lint: zero errors and eight existing warnings.
- Existing isolated-emulator eight sorting and async catalog/edit/picker checks
  passed. Production install -r preserved UID and first-install time; version
  1.0.16 launched and pulled installed APK bytes equal the signed artifact.
- Signed release is non-debuggable and uses the existing API-dependent identity
  and lineage for Android APIs 26 through 36. No new dependency or third-party asset.

LinkedHashMap carries ordering overhead, and a request can still remove the full
expired set. No constant worst-case or memory reduction is claimed. Real-device
notification-listener bursts and full callback latency remain unmeasured.
This change does not claim to solve unrelated removal-confirmation attribution,
service reconnection or history refresh behavior.

Reproduce the algorithm checks with `:app:testDebugUnitTest`, specifically
`com.local.noti_filter.PendingRemovalsTest`. Restore point and final distribution
verification are recorded in IMPROVEMENT_LOG.md and local cycle evidence. No
real notification content, signing material or runtime state is published.
