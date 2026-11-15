# NotiFilter 1.0.27

Semicolon is now the sole AND separator. OR expressions are removed; use separate rules for alternatives. Commas are ordinary characters. Editor hints, errors, help and guides updated.

Legacy rules migrate once atomically with format version 2: comma AND becomes semicolon; legacy OR alternatives become distinct rules with preserved scope, enabled state and collision-free IDs. Literal rules are unchanged. Invalid legacy expressions stay invalid. No import/export feature exists.

Base: origin/main 9b60d2a, clean source and zero ahead/behind before edits. Remote fetched again before publication with no divergence. Opus claude-opus-5-5 medium implemented; Codex reviewed diff and independently ran release build, lint and JVM tests.

Validation: testReleaseUnitTest (34 tests, zero failures/errors), assembleRelease and lintRelease succeeded. Lint has zero errors and eight warnings. Package com.local.noti_filter, version 1.0.27/code28, non-debuggable, zip alignment and signing lineage validated at APIs 26/28/32/33/35/36. APK credential-file exclusions checked. No runtime dependency, permission or third-party asset added. Existing platform-only notices retained.

APK: 66236 bytes; SHA-256 8008287c718f6e525cdde91c790a317e440cbda08bec9525e02c844cbb7bf92b. LAN HTTP download matched these bytes and hash.

Emulator update/UI verification ongoing at time of writing. Samsung/physical-device notification behavior not tested. Downgrading to the old grammar is unsupported. No ARTEMIS or vulnerability scanner used. Android instrumentation fixtures updated but not executed.
