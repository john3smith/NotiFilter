# NotiFilter 1.0.25 — public replacement notification content

## Changes

The listener previously inspected only the original notification's extras. It now
also considers the platform-provided `Notification.publicVersion`. Each complete
representation is matched independently against the existing package, enabled,
literal and expression rules. Words from the original and public representation
cannot combine into a new AND match. Only Android-provided content is used; no
hidden-content placeholders are invented and no protected profile access is added.

The current-notification picker offers a separate public-content row when it
differs from the original. When the original has no text, available public text
is the fallback. Other-profile notifications are labelled explicitly. Existing
rules remain package-scoped across profiles; this update does not introduce a
profile-only rule mode. Cancellation continues to use the full system notification
key, preserving its user identity. Pending history stores the representation that
actually matched, and still requires a confirmed listener-cancel callback.

On listener reconnection, currently active notifications are checked again, with
global pause and disabled rules respected. Empty/unavailable picker states remain
distinct. Picker guidance was kept short enough to leave room for the list.

## Validation

- Release build, 26 existing JVM unit tests (zero failures/errors/skips), and
  release lint passed: zero errors, six warnings.
- Existing isolated-emulator catalog/editor/cache/picker checks and four
  history-load checks passed.
- Manual regular API 35 emulator: selected a synthetic shell notification,
  saved its literal app-specific rule, observed the picker close and the source
  notification disappear. Reposting matching content was removed after callback
  processing. A matching notification remained while paused across reconnection.
  With the listener disconnected and pause removed, an existing matching
  notification was removed after reconnecting the listener.
- Picker examined at normal resolution and 320dp width/approximately 571dp height
  at 130% font size. List scrolling and close/refresh controls remained visible.
  Display/font settings and the isolated clone's prior rule preferences and
  listener permission were restored. Synthetic history remains confined to the
  diagnostic clone. No physical device was used.
- Signed production update installed over 1.0.24 on the regular emulator.
  Package identity and first-install time were retained. The pulled installed
  APK SHA-256 equals the final signed artifact. Final package is non-debuggable;
  the existing signing lineage verifies at APIs 26/28/32/33/35/36.
- No new runtime dependency, permission, asset, signing key or license obligation
  was introduced. Existing Android platform-only runtime notices are retained.
  This is not a comprehensive security or license audit.

## Failed and unverified checks

The pre-existing sorting UI instrumentation fails with `Order [] expected
[400, 300, 200, 100]`. Its helper searches only direct root children for rule
cards; the current app groups rules inside expandable app sections. This change
does not modify sorting or group rendering. The failure is recorded separately
from passing unit and UI checks; that legacy UI check was not repaired here.

Samsung Secure Folder is unavailable on the standard Google emulator. Actual
Samsung delivery, its public replacement payload, cross-profile enumeration and
cancellation remain unverified. A synthetic publicVersion posting/cancellation
was not exercised. The new representation branches were reviewed against the
Android contract, built and signed, and are provided for user testing; this is
not evidence that the reported Samsung notification has been successfully removed.

## User testing

Update using the signed APK, open NotiFilter and check notification access. While
the unwanted Secure Folder notification is present, use **현재 알림에서 규칙 추가**.
Select the corresponding notification or **내용 숨김 표시** row when offered.
This creates a literal rule for the actual listener-visible package and text.
Confirm disappearance and a new matching notification, as well as the deletion
history. Existing original-text rules cannot match content Android does not
provide. A generic public-content rule can match other notifications from the
same app that use that text, including another profile.

If the notification or its public text is absent from the picker, check Secure
Folder's own app notification settings. Android/OEM profile restrictions cannot
be bypassed by this app; system or ongoing notifications may also resist
cancellation. No workaround that weakens those restrictions was added.

Official references:

- https://developer.android.com/reference/android/app/Notification#publicVersion
- https://developer.android.com/reference/android/service/notification/NotificationListenerService
