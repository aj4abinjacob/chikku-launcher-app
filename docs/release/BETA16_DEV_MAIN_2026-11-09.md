# Beta 16 (build 17) — search window card and menu polish, 9 November 2026

Version 0.1.0-beta.16 (17) brings development commits `26f7914`, `1d419d5` and `5025744` onto the
beta 15 release line and was submitted to the existing Closed testing – Alpha audience. Production
remains NO-GO.

- **Changes:** search in window mode is a floating card with the field as its header; tapping outside
  closes it, and with results kept near the keyboard it docks above the keyboard. Shortcuts an app lists
  twice under the same name appear once. Home menu buttons follow the card's corner radius, slightly wide
  labels shrink instead of being cut, and the menu is 264 dp wide. The app-menu pointer stays on the flat
  part of the card edge. A folder's long-press menu uses the labelled buttons Edit folder, Open, Remove.
  Three cherry-picks, no conflicts. No settings, backup, permission, dependency or database-schema change.
- **Candidate:** commit `fea11ff97b521fd9ef21740a1a6f11da5f4e9a1d`, tree
  `03873a9d23cc114a45fe8cdbf32bedf79615ae12`.
- **Signed AAB:** SHA-256 `2904db24205749d3ee7ac83bf4bd6c9a40a23f4ea915bf0898b7d9292c1175ca`; upload
  certificate `6ac3c3c0…2bc3d6` (unchanged). R8 mapping attached to build 17.
- **Matching source:** <https://www.abinjacob.in/chikku/source/fea11ff97b52/>, public archive SHA-256
  `f900b41617a9d98b671569ca449d64cc10e82200d4c97d19443a36cd124de24a`. Served by Worker version
  `2c8f2347-4b38-4333-9f1b-80ec26cd17e7`. All three parts were downloaded back from the public URL and
  matched. A clean extract rebuilt `assembleRelease` offline.
- **Local checks:** `:core:model:test`, `testDebugUnitTest`, `lintDebug`, `lintRelease` and the
  privacy gate passed; license audit (109 components, 0 exceptions); `bundletool validate` 1.18.3;
  AAB signature verification; static APK inspection (non-debuggable, min 29 / target 36, 16 KB aligned).
- **Play Console:** uploaded with the Play Developer API (`play-upload.py`). A validation-only edit
  passed first; the committed edit `07393680143044420435` set Closed testing – Alpha to
  0.1.0-beta.16 (17) at full rollout with release notes. Google review is expected to follow;
  managed publishing is off.
- **Not done:** Google approval, Play-signed APK download/verification, and any physical-device or
  emulator acceptance of the new search card and menu layout (unit tests only).

Receipts are in the owner's local `chikku-update-evidence/2026-11-09/beta16-code17/`. Signing and API
credentials stay outside the repositories and archives.
