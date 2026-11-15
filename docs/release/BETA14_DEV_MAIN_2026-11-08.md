# Beta 14 (build 15) — choose up to 4 app-menu actions, Remove page with items, 8 November 2026

Version 0.1.0-beta.14 (15) brings development main `5dd5274` onto the beta 13 release line and was
submitted to the existing Closed testing – Alpha audience. Production remains NO-GO.

- **Changes:** an app icon's long-press menu shows at most 4 actions, chosen under Settings →
  Appearance → Menus (one switch per action for Home and for the drawer; a fifth waits until one is
  off; Home defaults Edit, App info, Remove, Uninstall; folder-cover icons keep Folder and drop the
  lowest-priority action if needed). Actions are always tinted chips: beta 13's wide last-row bars are
  removed (development `872dfb8`, `1b173c8`). The empty-space menu always shows Remove page: an empty
  page goes at once, a page with items asks first and removes them with it, one Undo restores
  everything, and the only page cannot go (`5dd5274`). The Settings button is named "Settings" and the
  popup corner radius defaults to 14 dp (a saved radius is kept). Six new menu settings are in
  backups; older backups use defaults. Three cherry-picks, no conflicts. No permission, dependency or
  database-schema change.
- **Candidate:** commit `b878c3ee463b9abb90485b76a66a5a17f7cfda32`, tree
  `1262ae7cd8f7a8229adcc7ea54f98dc7472f3be4`.
- **Signed AAB:** SHA-256 `a2d2a292f2eb08477296eb368ee1e608bb58c95a4877b680dd3d36607ca906fe`; upload
  certificate `6ac3c3c0…2bc3d6` (unchanged). R8 mapping SHA-256 `43937ce2f9fe600f41e2f47c9ba2f5831b13c94af1b6b99db897e585698ba9b7`, attached to build 15.
- **Matching source:** <https://www.abinjacob.in/chikku/source/b878c3ee463b/>, public archive SHA-256
  `d2dedabe1e44559688deefecbcb4f292090d462d5b0e7d840bb925b64da67952`. Served by Worker version
  `8aaa332e-11e9-49b7-9c45-069f59926843`. All three parts were downloaded back from the public URL and
  matched. A clean extract rebuilt `assembleRelease` offline.
- **Local checks:** `:core:model:test`, `testDebugUnitTest`, `lintDebug`, `lintRelease` and the
  privacy gate passed; license audit (109 components, 0 exceptions); `bundletool validate` 1.18.3;
  AAB signature verification; static APK inspection.
- **Play Console:** uploaded with the Play Developer API (`play-upload.py`). A validation-only edit
  passed first; the committed edit `07479840315109507450` set Closed testing – Alpha to
  0.1.0-beta.14 (15) at full rollout with release notes. Google review is expected to follow;
  managed publishing is off.
- **Not done:** Google approval, Play-signed APK download/verification, and any emulator or
  physical-device acceptance, including the menu switches, Remove page confirmation and Undo.

Receipts are in the owner's local `chikku-update-evidence/2026-11-08/beta14-code15/`. Signing and API
credentials stay outside the repositories and archives.
