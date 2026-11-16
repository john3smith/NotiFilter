# NotiFilter 1.0.29

Removes individual rule backgrounds and the full-photo veil. Adds persistent 0-100% frame and button background transparency sliders in Settings (defaults 25/15). Text alpha stays unchanged; local text halos aid photo readability. Pause card, group frames and main controls follow settings. Photo modes remain unchanged.

Base origin/main 8a0e08b, fetched before editing; tracked sources matched. Existing unrelated untracked receipts preserved. Opus claude-opus-5-5 medium implemented; Codex reviewed diff and ran release build, 34 passing JVM tests, lint (0 errors, 9 warnings). Production signing, alignment, non-debuggable package and API26/28/32/33/35/36 signer checks passed. Emulator update install succeeded preserving firstInstallTime; main and new Settings screen visually confirmed. Pre-existing emulator input ANR/delays limited photo selection, persistence and narrow/large-font validation; these are not claimed passed. No physical-device testing. No new dependencies or third-party assets.

User test: Settings > 배경 투명도; adjust 틀 투명도 and 버튼 배경 투명도 then return. Higher values show more photo; 0% opaque, 100% transparent. Choose a photo and check row backgrounds and pause frame.
