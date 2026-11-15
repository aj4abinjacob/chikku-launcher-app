# Beta 12 (build 13) — redesigned Settings pages and compact long-press menus, 7 November 2026

Version 0.1.0-beta.12 (13) brings development main `c44cab4` onto the beta 11 release line and was
submitted to the existing Closed testing – Alpha audience. Production remains NO-GO.

- **Changes:** a redesigned Settings front page (full-colour White Coat icon, search field,
  default-home status line, tiles for Home screen, App drawer, Dock and Folders), Backup & restore
  led by Save and Restore buttons, and an About app card with "No ads", "No account" and "Works
  offline" (development `41823e8`). Long-press menus are more compact: app-icon actions are small
  tinted buttons in a fixed order with only Uninstall in red, and the empty-space menu uses large
  buttons for Widgets, Wallpapers and Cards, a pair for Add page and Remove page, and a full-width
  Settings button (development `c9cc942`). Two cherry-picks; conflicts in `AboutSettingsSection.kt`
  (kept both imports, including the release line's legal-document dialog) and
  `LauncherBrandIconTest.kt` (kept the release line's White Coat version). No permission,
  dependency, database migration or backup-format change.
- **Candidate:** commit `3549bd1b7c2946d472a37048d1fd4b3053824b16`, tree
  `c2406e501346bb5f7c371874049861da2f5df6bb`.
- **Signed AAB:** SHA-256 `5d8be90cb42d6501b8f10564862641928643e59d562aae285e99a6d8fe2d8f3d`; upload
  certificate `6ac3c3c0…2bc3d6` (unchanged). R8 mapping SHA-256
  `b58c47fc0b47b211983a714d7763b10485e3fda5c7602e07f24a35e72c56e638`, attached to build 13.
- **Matching source:** <https://www.abinjacob.in/chikku/source/3549bd1b7c29/>, public archive SHA-256
  `b7ec80a5acd7bdbefbf9077fa247be8e0cbb0e164194af9971f345af8b4f557e`, same historical-art
  exclusions as beta 11. Served by Worker version `eedd8a7c-6401-45be-855e-091217798282`. All three
  parts were downloaded back from the public URL and matched. Earlier versions remain listed. A
  clean extract rebuilt `assembleRelease` offline.
- **Local checks:** core 248/248 and app 1309/1309 unit tests, `lintDebug`/`lintRelease`, privacy
  gate (permissions and 113 modules unchanged), license audit (109 components, 0 exceptions),
  `bundletool validate` 1.18.3, AAB signature verification, static APK inspection (non-debuggable,
  min 29/target 36, 16 KB alignment pass, permissions identical to build 12).
- **Play Console:** uploaded with the Play Developer API (`play-upload.py`). A validation-only edit
  passed first; the committed edit `02109846204851408987` set Closed testing – Alpha to
  0.1.0-beta.12 (13) at full rollout with release notes. Google review is expected to follow;
  managed publishing is off.
- **Not done:** Google approval, Play-signed APK download/verification, and any emulator or
  physical-device acceptance of this build, including the new Settings pages and menus.

Receipts (build, signing, audit, source and submission logs) are in the owner's local
`chikku-update-evidence/2026-11-07/beta12-code13/`. Signing and API credentials stay outside the
repositories and archives.
