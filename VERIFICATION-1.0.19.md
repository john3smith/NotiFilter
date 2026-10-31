# NotiFilter 1.0.19 verification - 2026-10-31

Version code 20. Final publication of the pending round 13-15 changes after the
twenty-round investigation. The round 20 investigation adds no application fix.
Ten rounds made validated implementation changes; ten were investigation-only
(3, 7, 10, 11, 12, 16, 17, 18, 19, 20). Earlier changes already delivered in
1.0.18 are retained rather than uploaded again as that historical version.

Changes since 1.0.18:
- Reject Unicode whitespace-only expression terms, preserving literal nonblank
  terms and existing AND/OR semantics.
- Reject asynchronous icon results from an invalidated inventory generation.
- Coalesce refresh requests arriving during discovery into a follow-up scan.

Validation performed on the final 1.0.19 source inputs:
- Fresh 26 unit tests: no failures, errors or skips. Android lint: zero errors,
  seven existing warnings. Isolated Debug/androidTest and non-debuggable unsigned
  Release builds passed, including verifyReleaseSafety.
- All five existing isolated Android instrumentation modes passed: catalog,
  rule storage, sorting, history load cancellation and retention. Isolated
  preferences/database bytes were restored; listener/display settings preserved.
- Exact PendingRemovals compiled on JDK 17: eight fresh immutable-snapshot,
  replacement, capacity, expiry and clear observations passed. This host
  diagnostic does not establish Android callback ordering or a cross-package
  same-key platform guarantee. No new application defect was reproduced.
- Signed final APK checked independently for package/version/alignment,
  non-debuggable/non-testOnly flags, credential-file exclusions and active
  certificate at API 26/28/32/33/35/36. Existing API 33 rotation lineage retained.
- Regular API 35 x86_64 emulator: signed production package updated in place
  from 1.0.18. UID and first-install time preserved; every prior preferences/DB
  byte identical immediately after install; rules/history bytes identical after
  launch. Main UI visually inspected; process crash buffer has no fatal/ANR.

Artifact: NotiFilter-v1.0.19.apk; 58,044 bytes.
SHA-256: 54626ee5dde315454805c086ce34ed8385f332f691be3b885b7994a9afe3f394

No new permissions, runtime dependencies, external assets or license obligations
introduced. Existing bundled privacy and third-party notices retained. The APK
has no external runtime dependency artifacts; JUnit 4.13.2 remains host-test-only.
This is not a new full legal/license/security audit or a store submission.
Physical/OEM behavior, natural package-update races, process-death recovery and
real notification cancellation on this final version were not newly tested.
No performance gain is claimed from this investigation.

Publication integrity, PR required checks, remote/tag commit, asset digest plus
authenticated download, LAN full-download digest and native installation are
tracked in separate local deployment receipts. Final Telegram reporting uses the
original relay's full-message receipt; no HTML or duplicate message is sent.
