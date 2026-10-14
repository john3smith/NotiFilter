# NotiFilter 1.0.4 verification

2026-10-14. Runtime project: NotiFilter. Source snapshot: github-projects/NotiFilter.

- JDK 17, Android SDK 36, Gradle wrapper: testDebugUnitTest lintDebug assembleDebug PASS.
- RuleMatcher: 12 JVM tests, zero failures/errors/skips. Tests cover AND, OR, AND-before-OR precedence, empty/malformed/null expressions, trimming/case, app scope/enabled state, multiple rules, and literal non-ASCII punctuation.
- lint: zero errors, four existing localization warnings.
- Regular emulator emulator-5554, Android 15: data-preserving upgrade to versionName 1.0.4 / versionCode 5 verified.
- Rule-add dialog question-mark button opens a readable help dialog including mixed precedence examples and limitations. Existing single-dialog guard retained.
- Rule NFTEST,apple;pear,done saved through UI: synthetic NFTEST apple and pear done notifications were removed; NFTEST done remained. A matching already-posted pear done notification was removed when saving the new rule.
- Invalid bad; input: Add kept the dialog open and no new rule was persisted.
- Initial numeric synthetic titles received different notification text lengths in the listener and did not match. Re-tested with non-numeric synthetic content above; no security controls were bypassed. OEM/sensitive-notification redaction remains a platform limit.
- Temporary diagnostic logging was removed before the final build. No notification content was logged by the temporary code.
- Existing user/previous QA rules and history were not cleared. Newly-created synthetic QA rules are disabled after testing.
- Physical-phone/OEM-specific behavior was not tested. No ARTEMIS or new libraries were used.
- Public GitHub publication requires current approval; no repository visibility changes are authorized.
