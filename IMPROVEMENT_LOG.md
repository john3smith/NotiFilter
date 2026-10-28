# Improvement log

## Optimization, stability and latency review setup - 2026-10-29

- Scope: preserve existing filtering behavior while improving measured performance, reliability and maintainability. Maximum 10 cycles with evidence-based early termination and user stop support.
- Baseline: source and Release v1.0.13 at 379535a30b9c2c708e580b1547dc6fdd14d5d4a4; regular Android emulator package reports version 1.0.13, code 14.
- Registration only (cycle zero): no implementation, build, release or performance improvement is claimed.
- First investigation: notification receive/match/storage flow, avoidable repeated parsing or I/O, rule persistence, history retention and service reconnect behavior. Rank by user impact, reproducibility and regression risk.
- Measurement: use reproducible synthetic notifications/rules and before/after timings; do not expose real notification content. Preserve filtering results and verify regular emulator behavior, tests and actual builds before declaring improvement.
- Record each cycle's cause, patch, restore point, test/build results, measurements, remaining risks and next scope here. Preserve unrelated working trees, user data and existing authorization boundaries.

## Cycle 1 - retain parsed expressions in immutable rules - 2026-10-29

- Problem/cause: scanning more than 128 distinct applicable rules repeatedly thrashes the shared expression LRU, reparsing expressions for every notification. Prioritized over speculative history changes because the hot-path cost is directly reproducible and the patch is small.
- Change: lazy volatile parsed-expression reference per immutable Rule; RuleMatcher reuses it after scope/enabled checks. Invalid syntax still fails closed and editing creates a fresh Rule. Version 1.0.14 (15).
- Restore point: 73045524d55f980678ed9f6a71350f4db0cd97b3; original changed files and SHA-256 manifest saved locally before editing. Restore only scoped originals after checking for intervening changes; no automatic Git reset.
- Measurement: 64/256/512-rule synthetic steady-state scan medians, before 2.119/47.087/94.408 us, after 0.746/3.539/6.977 us. JDK 17 host microbenchmark; no end-to-end or battery claim. Reproducible harness and details in VERIFICATION-1.0.14.md.
- Validation: 20 unit tests passed; lint zero errors/eight warnings; release and isolated test APK builds passed. Existing emulator catalog/edit/picker and eight sorting checks passed. Production emulator updated with install -r to 1.0.14, retaining its original installation date; pulled APK bytes match the signed artifact and launch succeeds. Signed release is verified against the existing API-dependent certificate lineage.
- Verification recovery: the host wrapper initially expected a standard instrumentation result line even though this custom runner returns a PASS stream, then expected userId instead of the current Android appId field. Actual UI checks and installation had succeeded. Corrected the evidence parser and continued only unfinished checks, without repeating installation; prior UID was not retained, so a before/after UID equality claim is not made.
- Self-evaluation: measured repeated parsing overhead removed while preserving semantics. Parsed memory now scales with live evaluated rules; peak heap impact is unmeasured. No schema, permissions or third-party dependency changes.
- Remaining/next investigation: notification-removal confirmation, bounded encrypted-history persistence and reconnect behavior; do not count rechecking this cache as another improvement. Physical-device behavior and full notification end-to-end timing remain unmeasured.
- Delivery: source through the protected-branch PR checks and matching versioned GitHub Release; final asset/readback and emulator update receipts retained locally. One cycle only; subsequent admission belongs to the coordinator.
