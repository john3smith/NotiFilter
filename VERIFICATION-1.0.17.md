# NotiFilter 1.0.17 - skip obsolete history reads

## Problem and change

HistoryActivity already ignored outdated results using a request generation, but
HistoryStore still queried, decrypted and decoded every queued read. Repeated
refreshes or leaving the activity could leave obsolete reads ahead of history
writes on the shared serial executor.

HistoryStore.load now accepts a thread-safe current-request predicate. It checks
before database access, before decoding each row, and again when delivering on
the main thread. HistoryActivity exposes its existing request generation through
a volatile field so the worker sees refresh, clear and destroy invalidation.
Current failures still reach the existing failure UI; stale failures/results are
not delivered. Cursor cleanup remains try-with-resources.

This is cooperative read cancellation: queued tasks still drain briefly, an
already-running SQLite call or individual decryption is not interrupted, and
already-decoded entries become eligible for garbage collection. No writes or
clear operations are cancelled. No schema, encryption, retention, permission,
dependency, third-party asset or layout change is introduced.

## Reproduction and scope

`HistoryLoadChecks` runs in the isolated UI-test app. It inserts one encrypted
synthetic row, uses executor/latch checkpoints and deletes only that row by its
exact ID in finally. No production history is used or cleared.

Run after building and installing `-PuiTest` debug and androidTest APKs:

```text
adb -s emulator-5554 shell am instrument -w -e historyLoadChecks true com.local.noti_filter.uitest.test/com.local.noti_filter.RuleUiInstrumentation
```

Four checks passed on the regular emulator:

1. Queue 100 reads behind a blocked worker while advancing the generation:
   99 are rejected before DB access; one current read delivers success.
2. Invalidate at the first row boundary: no decoding/result callback proceeds.
3. Invalidate after worker completion but before main-thread delivery: suppress
   the obsolete result.
4. A current read with deliberately invalid synthetic ciphertext delivers its
   failure callback, preserving fail-closed encryption behavior.

The 99 skipped reads are a deterministic work-count result, not a timing,
battery, memory or full notification-throughput benchmark. Physical-device
stress and complete history-screen lifecycle scenarios are not claimed by these
store-level tests. Existing catalog/edit/picker and sorting checks cover nearby
app interactions separately.

Final build, signed-artifact, source/Release and emulator-update evidence is
retained with this cycle; see IMPROVEMENT_LOG.md. Existing lint warnings and
unrelated notification-listener reconnect/attribution questions remain.

Final checks passed: 26 host unit tests, four history-load checks, eight sorting
checks, catalog/edit/picker regression, release and test APK builds. Lint: zero
errors/eight warnings. Existing signing lineage verifies for APIs 26-36.
Emulator install -r retained UID and first-install time; 1.0.17 launched and
the pulled installed APK equals the signed release bytes.
