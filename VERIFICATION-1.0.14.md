# NotiFilter 1.0.14 - rule matching reuse

## Change and tradeoff

Rules are immutable. A rule now lazily retains its parsed expression for its own
lifetime. Previously each notification consulted a synchronized 128-entry shared
cache; a sequential scan of more than 128 distinct applicable rules could evict
every earlier entry and parse the entire set again on the next notification.
The shared cache remains bounded for one-off expression validation. Package and
enabled checks still run before expression access. Invalid expressions still fail
closed. Edits create a new Rule, so they cannot reuse the old parsed expression.
Volatile publication makes concurrent first use safe; redundant cold parsing in
a race is harmless. No persistence schema, permissions or filtering syntax change.

Retained parsed memory is now proportional to the live rules actually matched,
rather than just the shared 128 entries. Discarded rule objects are collectible.
This trades a bounded-by-live-rules memory cost for avoiding recurring allocation
and parsing. Peak memory, physical-device battery impact and end-to-end listener
latency have not been measured.

## Reproduction and measurements

`tools/MatcherBench.java` uses synthetic no-match notifications, two OR groups and
an AND group per rule, 2,000 warmup scans, then seven samples of 3,000 scans. It
reports the median microseconds per scan in one Java process, not Android callback
latency. Compile Rule.java, RuleExpression.java, RuleMatcher.java and this harness
with `javac -encoding UTF-8 -d <output> ...`; run
`java -Xms128m -Xmx128m -cp <output> com.local.noti_filter.MatcherBench`.
Use baseline source commit 73045524d55f980678ed9f6a71350f4db0cd97b3 and the updated
sources separately with identical inputs and JDK 17.0.20.1 on Windows.

| Applicable rules | Before median (us) | After median (us) |
| --- | ---: | ---: |
| 64 | 2.119 | 0.746 |
| 256 | 47.087 | 3.539 |
| 512 | 94.408 | 6.977 |

This supports a steady-state matcher improvement for this synthetic workload,
not a claim that the entire app is 13 times faster. Cold load is not accelerated.

## Validation

- Unit tests: 20 passed, zero failures/errors (14 matcher, 3 catalog, 3 ordering).
- Regression cases include 512-rule scans, shared-cache eviction, malformed rules,
  Korean/case-insensitive AND/OR, package scope, disable/edit replacement and
  concurrent first use.
- `testDebugUnitTest`, `lintDebug`, `assembleRelease`: successful. Lint: zero
  errors, eight warnings. Existing deprecated API/Gradle warnings remain.
- Isolated UI clone passed the existing catalog/edit/picker and eight sorting
  checks on the regular Android emulator; production data is not used as fixtures.
- Production emulator updated using install -r to 1.0.14, kept its original
  installation date, launched successfully and had no fatal exception in its
  process log at verification. Pulled installed APK bytes equal the signed file.
- Release is non-debuggable, versionCode 15, signed using the existing protected
  identity and lineage, verified for APIs 26, 28, 32, 33, 35 and 36.
- No new dependencies or third-party assets. Existing runtime inventory has no
  external artifacts; JUnit remains test-only. Store-release eligibility is not
  claimed by this GitHub release.

Final emulator and GitHub asset readback evidence is retained with the cycle
record. No real notification data or signing credentials are published.
