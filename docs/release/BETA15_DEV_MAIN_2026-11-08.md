# Beta 15 (build 16) — home screen stays portrait on phones, 8 November 2026

Version 0.1.0-beta.15 (16) brings development commit `165b124` onto the beta 14 release line and was
submitted to the existing Closed testing – Alpha audience. Production remains NO-GO.

- **Changes:** on phones (smallest screen width under 600 dp) the home screen stays in portrait. In
  landscape a phone is about 390 dp tall, too little for the 5×5 grid, dock and page indicator: desktop
  icons shrank to the minimum size and lost their labels. Tablets and an unfolded foldable (600 dp or
  more) still rotate; the rule is re-evaluated on every start. Settings, search and the app drawer are
  unchanged. One cherry-pick, no conflicts. No settings, backup, permission, dependency or
  database-schema change.
- **Candidate:** commit `2fc1bc814ef4e1745ccb60596cef9079c5a790b7`, tree
  `0720231d974e8cebd89f560ebd339ba37468f650`.
- **Signed AAB:** SHA-256 `b8dbba461800f184340697c7745e736c3018394bbae83309733767fd73542d84`; upload
  certificate `6ac3c3c0…2bc3d6` (unchanged). R8 mapping attached to build 16.
- **Matching source:** <https://www.abinjacob.in/chikku/source/2fc1bc814ef4/>, public archive SHA-256
  `4632790b89d5e5b7741260a2aecc27ffd1561999af53a0c36287e1a7fe3b2e46`. Served by Worker version
  `d669b420-5d43-4285-8a90-291ba9beceda`. All three parts were downloaded back from the public URL and
  matched. A clean extract rebuilt `assembleRelease` offline.
- **Local checks:** `:core:model:test`, `testDebugUnitTest`, `lintDebug`, `lintRelease` and the
  privacy gate passed; license audit (109 components, 0 exceptions); `bundletool validate` 1.18.3;
  AAB signature verification; static APK inspection (non-debuggable, min 29 / target 36, 16 KB aligned,
  no new permissions).
- **Play Console:** uploaded with the Play Developer API (`play-upload.py`). A validation-only edit
  passed first; the committed edit `16948513388074834106` set Closed testing – Alpha to
  0.1.0-beta.15 (16) at full rollout with release notes. Google review is expected to follow;
  managed publishing is off.
- **Not done:** Google approval, Play-signed APK download/verification, and any tablet, foldable or
  physical-device acceptance. The lock was seen only as the requested orientation reported by Android
  on a Redmi 9 whose screen then went off; no screenshot of the final portrait screen was taken.

Receipts are in the owner's local `chikku-update-evidence/2026-11-08/beta15-code16/`. Signing and API
credentials stay outside the repositories and archives.
