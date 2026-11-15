# Beta 13 (build 14) — app actions in long-press menus and regrouped Gestures page, 8 November 2026

Version 0.1.0-beta.13 (14) brings development main `d587617` onto the beta 12 release line and was
submitted to the existing Closed testing – Alpha audience. Production remains NO-GO.

- **Changes:** long-press menus on app icons show app actions, with a Folder option on app-covered
  folders; a short last row of actions becomes wide buttons; the empty-space menu pairs its small
  buttons two to a row and stays clear of rounded card corners (development `743dd67`, `ce789db`,
  `ffbca76`, `98d43f7`, `0e7a005`, `f4c0abb`). The Gestures page is regrouped into everyday gestures
  and an expandable "More gestures" list with a clearer accessibility prompt (`bdcae4e`, `0728807`).
  Seven cherry-picks. Conflicts in `GesturesSettingsSection.kt` and `strings_gestures.xml`: kept the
  release line's accessibility disclosure sheet and made the Gestures "Turn on" rows open it first;
  dropped two strings the new page no longer uses. No permission, dependency, database migration or
  backup-format change.
- **Candidate:** commit `6f7efc20e951f34a0a8e48b9d053fe45ccd855b5`, tree
  `889da82045444cdf6495f86e17e28180192f15b6`.
- **Signed AAB:** SHA-256 `9ebfcf9ed16d6783886ab94b6c3fdf9259f648e892fab5dc47a93bc93b660708`; upload
  certificate `6ac3c3c0…2bc3d6` (unchanged). R8 mapping SHA-256 `4157b3a52046bf8c451a0f9a7d366ddb75420d26877afcd470218be495c5e28c`, attached to build 14.
- **Matching source:** <https://www.abinjacob.in/chikku/source/6f7efc20e951/>, public archive SHA-256
  `ca459e487f3015ab6724f3f760c8eeae2c5cda55d5aac3e7ccc1fe50be987d41`. Served by Worker version
  `02682b06-2952-4135-83f6-c9378048a0d8`. All three parts were downloaded back from the public URL and
  matched. A clean extract rebuilt `assembleRelease` offline.
- **Local checks:** `:core:model:test`, `testDebugUnitTest`, `lintDebug`, `lintRelease` and the privacy
  gate passed; license audit (109 components, 0 exceptions); `bundletool validate` 1.18.3; AAB
  signature verification; static APK inspection (non-debuggable, min 29/target 36).
- **Play Console:** uploaded with the Play Developer API (`play-upload.py`). A validation-only edit
  passed first; the committed edit `02670438134990788368` set Closed testing – Alpha to
  0.1.0-beta.13 (14) at full rollout with release notes. Google review is expected to follow;
  managed publishing is off.
- **Not done:** Google approval, Play-signed APK download/verification, and any emulator or
  physical-device acceptance of this build, including the new menus and Gestures page.

Receipts are in the owner's local `chikku-update-evidence/2026-11-08/beta13-code14/`. Signing and API
credentials stay outside the repositories and archives.
