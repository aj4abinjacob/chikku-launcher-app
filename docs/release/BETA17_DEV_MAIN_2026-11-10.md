# Beta 17 (build 18) — search highlight and overlap fixes, 10 November 2026

Version 0.1.0-beta.17 (18) brings development commits `7004ebc` and `bbca49b` onto the beta 16
release line and was submitted to the existing Closed testing – Alpha audience. Production remains
NO-GO.

- **Changes:** in search, the top-result highlight is inset 8 dp from the screen edges so rounded
  screen corners and screen guards no longer clip it. Rows that leave the result list vanish at once
  instead of fading out, which stops old results being drawn over new ones while typing. Two
  cherry-picks, no conflicts. No settings, backup, permission, dependency or database-schema change.
- **Candidate:** commit `63380476aa5acf5ff7e7c419eb655eb7f886c55e`, tree
  `1e8638e1178551f48fb3cf429b8418c4af041731`.
- **Signed AAB:** SHA-256 `7dc039bdaf32c45b936fad5107b68ebdd55aa2ad50e83d9792cb19fedf98dbe1`; upload
  certificate `6ac3c3c0…2bc3d6` (unchanged). R8 mapping attached to build 18.
- **Matching source:** <https://www.abinjacob.in/chikku/source/63380476aa5a/>, public archive SHA-256
  `c38b94ce66e83159e766120b44e882ff9889e664b8db119afdbf9b11c5c170da`. Served by Worker version
  `80a53b15-ba6f-4b81-8bec-4163a673fb49`. All three parts were downloaded back from the public URL and
  matched. A clean extract rebuilt `assembleRelease` offline.
- **Local checks:** `:core:model:test`, `testDebugUnitTest`, `lintDebug`, `lintRelease` and the
  privacy gate passed; license audit (109 components, 0 exceptions); `bundletool validate` 1.18.3;
  AAB signature verification; static APK inspection (non-debuggable, min 29 / target 36).
- **Play Console:** uploaded with the Play Developer API (`play-upload.py`). A validation-only edit
  passed first; the committed edit `00434021307658474559` set Closed testing – Alpha to
  0.1.0-beta.17 (18) at full rollout with release notes. Google review is expected to follow;
  managed publishing is off.
- **Not done:** Google approval, Play-signed APK download/verification, and any physical-device or
  emulator acceptance of the highlight inset or the overlap fix (compile and unit tests only; the
  overlap cause was inferred from a screenshot and code, not reproduced).

Receipts are in the owner's local `chikku-update-evidence/2026-11-10/beta17-code18/`. Signing and API
credentials stay outside the repositories and archives.
