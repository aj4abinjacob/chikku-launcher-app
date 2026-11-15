# Beta 10 (build 11) — development main update, 4 November 2026

Version 0.1.0-beta.10 (11) brings development main `ffe0f05` onto the beta 9 release line and was
submitted to the existing Closed testing – Alpha audience. Production remains NO-GO.

- **Candidate:** commit `8a68bcfbf531f203cac46ae48b63df5a70a6acf0`, tree `c4d6ea876b981e43accaf73e7af4372ceaa2ff5d`.
- **Signed AAB:** SHA-256 `5bdb37d21a343ddfaf9488d4738f6a586b6a8e0e06618cd119c9d7e6c7235ec6`; upload
  certificate `6ac3c3c0…2bc3d6` (unchanged).
- **Matching source:** <https://www.abinjacob.in/chikku/source/8a68bcfbf531/>, public archive SHA-256
  `2112cab484fefca6a3c9b3635db8e434a1bf546db8dad21ea453043c66778a66`, same historical-art
  exclusions as build 10. Downloaded back from the public URL and verified; earlier versions remain
  listed. A clean extract rebuilds `assembleRelease`.
- **Local checks:** core 235/235 and app 1306/1306 unit tests, `lintDebug`/`lintRelease`, privacy gate
  (permissions and 113 modules identical to build 10), license audit (0 exceptions), static APK
  inspection (non-debuggable, min 29/target 36, 16KB alignment pass).
- **Play Console:** release 5 on Closed testing – Alpha, full rollout, sent for review on 4 November
  with quick checks running. Only warning: native debug symbols not uploaded. Supported devices
  unchanged.
- **Not done:** Google approval of the White Coat store listing icon (sent for review 5 November)
  and a White Coat feature graphic (still the Nose notch art; the bundle only changes the installed
  launcher icon, see `CONSOLE_AND_LISTING.md`), Google approval of the build, Play-signed
  APK download/verification, and any emulator or physical-device acceptance of this build.

Integration choices (search top-result design, drop settle, bind window, highlight glide, app menu
stale-close) are described in the release PR and `CHANGELOG.md`.
