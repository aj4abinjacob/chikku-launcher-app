# Beta 19 (build 20) — audit follow-ups reconciled with the release line, 13 November 2026

Version 0.1.0-beta.19 (20) brings development `main` at `1d33360` onto the beta 18 release line and was
submitted to the existing Closed testing – Alpha audience. Production remains NO-GO.

- **Changes:** the drag/animation fixes `d960d22`, `2004f25` and `8b33a64`; three commits that were on
  development `main` but had never reached the release line (`8f3e631` exact-name shortcut first,
  `205deea` fast-scroller thumb with a pinned header, `a864850` App lock settings redesign); the
  contact-lookup fix `ed93cf5`; dead-code removal `2c4a76e`; the as-built architecture doc `d6e88e8`;
  and the audit follow-ups `1d33360`.
- **Not a clean pick.** `d960d22` and `2c4a76e` had one conflict each (the release line's shortcut
  binding and shortcut icon loader were kept). `1d33360` conflicted in 19 files because the release
  line already had its own icon bitmap cache, badge avatar cache with content-equal badges, dock photo
  trimming, MET weather client and pinned-shortcut menus. Those were kept and the audit changes layered
  on top: targeted override invalidation and caching a cancelled requester's finished load on the
  bitmap cache; only the 150 ms rebuild coalescing for badges; `BoundedHttpsGet` for web suggestions
  only; `ShortcutIconLoader` with both the icon cache and the shared icon-pack repository; the
  extracted home hosts carry the pinned-shortcut menu; the split settings codec includes the
  search-bar tap action and button slots. The codec goldens were regenerated from the previous
  release codec and the split codec then matched them. Tests written for the development badge and
  icon-cache designs were trimmed or resized to the release designs.
- **Dependency inventory:** Compose `ui-tooling` and `ui-tooling-preview` are no longer runtime
  dependencies; the in-app licence list and `docs/release/runtime-dependencies.json` were regenerated
  (commit `3da42ae`). A first signed build from `1fbc34a` failed the static APK inventory check for
  this reason and was discarded, not uploaded.
- **Candidate:** commit `3da42ae7c7a111e17664aaa6ede6b93204333036`, tree
  `6acdc756993f190f4a8c267ecb14ccf61f163032`.
- **Signed AAB:** SHA-256 `1eabc7304b0d8cfc3b2f3ec76f6d203f484ff15e20fca5e896d68a7e9c1ba6eb`; upload
  certificate `6ac3c3c0…2bc3d6` (unchanged). R8 mapping
  (`8ecd5751eb6722a5dc682f91e0546d6ce6abb703723af9927941c1ab1145a01e`) attached to build 20.
- **Matching source:** <https://www.abinjacob.in/chikku/source/3da42ae7c7a1/>, public archive SHA-256
  `21f30221fff71c377e4e6039fb6e829acddd51c451ddb8673d7e2ecb8d212df9`. Served by Worker version
  `6272a383-8571-4d58-af27-1ea694e6c35a`. All three parts were downloaded back from the public URL and
  matched (the new path returned 404/500 for about a minute after the deploy). A clean extract rebuilt
  `assembleRelease` offline.
- **Local checks:** `:core:model:test`, `testDebugUnitTest` (1,651 tests in all, 0 failed),
  `lintDebug`, `lintRelease` (0 errors) and the privacy gate passed; licence audit (107 components,
  0 exceptions); `bundletool validate` 1.18.3; AAB signature verification; static APK inspection
  (non-debuggable, min 29 / target 36, 16 KB alignment).
- **Emulator (API 36, `chikku_api36`):** the instrumented suite ran once on the ported code before the
  version commit: 119 tests, 10 failed and 24 skipped on unmet preconditions. The beta 18 line run the
  same way also had 10 failures; seven are common, and the three that differed on each side passed
  or failed inconsistently on rerun. `CardEditorDeviceTest` raced the Save button enabling (also
  intermittent on beta 18); the test now waits (`7e5ee84`) and passed four runs. The suite was not
  rerun on the final candidate commit, which differs only in version, changelog and the inventory.
- **Play Console:** uploaded with the Play Developer API (`play-upload.py`). A validation-only edit
  passed first; the committed edit `16483245520956098797` set Closed testing – Alpha to
  0.1.0-beta.19 (20) at full rollout with release notes. Managed publishing is off.
- **Not done:** Google approval, Play-signed APK download/verification, and any physical-device
  acceptance. The performance changes are not measured. Development `main` still lacks the
  release-only code this port had to reconcile with, so the next update will meet the same divergence
  until `main` is brought up to the release line.

Receipts are in the owner's local `chikku-update-evidence/2026-11-12/beta19-code20/`. Signing and API
credentials stay outside the repositories and archives.
