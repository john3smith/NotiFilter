# NotiFilter 1.0.28 verification

Base: origin/main d35aa05. A separate clean worktree preserved prior checkout changes. Remote fetched before modification and publication. Existing published public repository john3smith/NotiFilter retained.

Adds photo icon, private normalized background image, saved fill/crop/fit modes, save/cancel/remove. Main screen only. No new dependency, permission or third-party asset; icon authored in project. Photos are excluded from backup and transfer.

Opus claude-opus-5-5 medium implemented. Codex independently reviewed source and ran testReleaseUnitTest, lintRelease, assembleRelease: 34 tests pass, zero lint errors, eight baseline warnings after documented suppressions. Final production APK signing, alignment, package and signing lineage checks passed across APIs26/28/32/33/35/36. Version1.0.28 code29; non-debuggable. Final APK78580 bytes SHA256 8cd0a2bc260f9008c255de3e21ab3e5bd46c4a9aba62e0bacec7911fe71aa8af.

Regular emulator-5554 update installation succeeded preserving data. Main photo icon, three mode labels, Save/Cancel and system document picker visually observed. Emulator launcher ANR and input delays limited further runtime validation. Actual photo save, relaunch persistence, removal, EXIF/large/corrupt inputs and rotation not fully verified. No physical phone or ARTEMIS used. No new test code authored. Install final APK and test photo select, each mode, Save, relaunch, Cancel and delete.
