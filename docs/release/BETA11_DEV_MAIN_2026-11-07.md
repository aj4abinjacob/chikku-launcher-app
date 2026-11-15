# Beta 11 (build 12) — learned search suggestions, 7 November 2026

Version 0.1.0-beta.11 (12) brings development main `36eac25` onto the beta 10 release line and was
submitted to the existing Closed testing – Alpha audience. Production remains NO-GO.

- **Changes:** search learns which result is picked for the first one to four typed letters, and a
  clear habit becomes the top (Go) result (development `d87c09a`). Off unless **Learn from my
  searches** is on; learned letters stay on the phone and are never backed up or logged.
  Development's shortcut-icon blink fix (`0719bf4`) was not taken: the release line's `IconBinder`
  already keeps a shortcut's icon while the same shortcut re-renders. No permission, dependency,
  database migration or backup-format change.
- **Candidate:** commit `88f2c92783bd9453f91d422217ef6e9d8b15c1ea`, tree
  `fb229456cd5e7ab28d0934118c84a044c1bf990f`.
- **Signed AAB:** SHA-256 `59ea2a516e6d522c46acf62a24c7f24ec7b337842e5251225fc2a66673702eba`; upload
  certificate `6ac3c3c0…2bc3d6` (unchanged). R8 mapping SHA-256
  `589effcc7c4be05ddadc5fecfff7b03e1414f1da1468bc91cdd75668f7b21213`, attached to build 12.
- **Matching source:** <https://www.abinjacob.in/chikku/source/88f2c92783bd/>, public archive SHA-256
  `5b67307cbbf9d9c1d320c618e076eea8e718abd79a794a47bc5abd7aa8cc228a`, same twelve historical-art
  exclusions as beta 10. Served by Worker version `8e203c21-b785-4032-a231-5f4ae3d84387`.
  All three parts were downloaded back from the public URL and matched (part 2 needed retries after
  HTTP 500 responses). Earlier versions remain listed. A clean extract rebuilt `assembleRelease` offline.
- **Local checks:** core 248/248 and app 1309/1309 unit tests, `lintDebug`/`lintRelease`, privacy
  gate (permissions and 113 modules unchanged), license audit (109 components, 0 exceptions),
  `bundletool validate` 1.18.3, AAB signature verification, static APK inspection (non-debuggable,
  min 29/target 36, 16 KB alignment pass).
- **Play Console:** uploaded with the Play Developer API (`play-upload.py`, service account
  `chikku-play-upload@avid-reference-514013-c9.iam.gserviceaccount.com`, Chikku-only release
  permissions) instead of the Console UI. A validation-only edit passed first; the committed edit
  `13412943842430867003` set Closed testing – Alpha to 0.1.0-beta.11 (12) at full rollout with
  release notes. Publishing overview shows **Changes in review**; managed publishing is off.
- **Not done:** Google approval, Play-signed APK download/verification, and any emulator or
  physical-device acceptance of this build.

Receipts (build, signing, audit, source and submission logs) are in the owner's local
`chikku-update-evidence/2026-11-07/beta11-code12/`. Signing and API credentials stay outside the
repositories and archives.
