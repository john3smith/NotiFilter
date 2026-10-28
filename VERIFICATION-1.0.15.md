# NotiFilter 1.0.15 - bounded-history pruning

## Problem and change

Every confirmed removal inserts a history row and prunes old rows in the same
transaction. The previous NOT IN query built a list of up to 1000 retained IDs
and tested the outer table against that list. The new query obtains the 1000th
newest ID and uses the INTEGER PRIMARY KEY range to delete older rows:

```sql
DELETE FROM history WHERE id <
  (SELECT id FROM history ORDER BY id DESC LIMIT 1 OFFSET 999)
```

If there are fewer than 1000 rows the subquery yields NULL, and no row satisfies
the comparison. Gaps in IDs do not change the count. This is not `MAX(id)-1000`,
which would be incorrect after deletions. Transactional insertion/pruning,
secure_delete, AES-GCM content encryption, schema and UI are unchanged. There is
no schema migration, new permission, dependency or third-party asset.

## Reproducible Android verification

`HistoryRetentionChecks` runs only in the isolated UI-test app, creates temporary
synthetic databases and never opens the production notification-history file.
It compares the old SQL with the exact constant used by production HistoryStore.
Cases cover 0, 1, 999, 1000, 1001 and 1300 rows, gapped IDs and a larger backlog,
transaction rollback and repeated pruning. It also checks both benchmark
databases end with 1000 rows and prints SQLite's query plan.

Build with `-PuiTest :app:assembleDebug :app:assembleDebugAndroidTest`, install
the isolated app and test APK, then run on the regular emulator:

```text
adb -s emulator-5554 shell am instrument -w -e historyChecks true com.local.noti_filter.uitest.test/com.local.noti_filter.RuleUiInstrumentation
```

The benchmark uses on-disk synthetic content and 256-byte icon blobs, secure
deletion enabled, one warmup round, seven samples of 200 insert/prune transactions
per variant, with alternating old/new order. It measures SQLite transaction
time only, excluding encryption, icon rasterization and notification callbacks.
Emulator timing cannot establish physical-device battery or full-app latency.

Observed transaction medians: **48,857.291 us before; 46,364.461 us after**
(approximately 5.1% lower). Disk synchronization dominates this environment;
do not generalize this single controlled run to all devices. The new query plan
reports `SEARCH history USING INTEGER PRIMARY KEY (rowid<?)` for the outer
delete and a scalar boundary subquery. It still visits the newest 1000 IDs to
find the boundary; this is not constant-time retention.

Final checks: 20 unit tests; nine retention cases and final benchmark counts;
eight sorting checks; async catalog/edit/picker regression; release and test APK
builds all passed. Lint reported zero errors and eight existing warnings.
The non-debuggable APK verifies with the existing signing lineage for APIs 26,
28, 32, 33, 35 and 36. Emulator install -r preserved UID and first-install time;
version 1.0.15 launched and the pulled installed APK equals the signed artifact.

## Verification recovery

The initial test helper used execSQL for PRAGMA secure_delete, which returns a
row on Android. The helper was corrected to rawQuery with its result checked,
matching the existing production configuration. The production SQL change was
not weakened to accommodate this test failure. Final validation results and
timings are recorded in IMPROVEMENT_LOG.md and the local cycle evidence.

An initial host wrapper timed out at 120 seconds while the on-disk device test
was still running. It was allowed to finish, and no overlapping instrumentation
was started. The final run used a 600-second host limit and saved its synthetic
report in the isolated app cache as well as instrumentation output.

No historical notification content, signing keys or runtime data are included
in the source or Release. Existing runtime dependency inventory and notices are
unchanged. Store readiness and physical-device validation are not claimed.
