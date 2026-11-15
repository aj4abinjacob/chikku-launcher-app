# Source publication and 16 KB acceptance — 2026-10-17

**Preparation only. No Play upload, review submission, tester invitation or rollout.** Both app
repositories remain private. The original development checkout and physical devices are untouched.
This dated continuation supersedes the pending approvals in the [16 October report](SOURCE_AND_ACCEPTANCE_2026-10-16.md).

## Owner decisions and published source

The owner confirms the necessary rights to project-owned code/current branding, including contributor
or employer interests, and approves the reviewed source export with exactly12 historical non-build
PNG/prompt exclusions. The private originals are preserved; these exclusions do not clear their
unrecorded reference rights or remove any app/build/current-artwork/GeoNames/dependency source input.

The approved preparation source is published at https://www.abinjacob.in/chikku/source/ with a stable
[1840777 version page](https://www.abinjacob.in/chikku/source/1840777b3841/).
Its joined SHA-256 is `7328ea6e82a966aac96d2e0ffe074a8db7f93980b400ced78ba49b118c3a24bd`.
A real Firefox public download completed and independently matched that hash. It retains the
previously verified clean extracted offline build and all1,100 project/84 dependency-source files.
Website source commit `bd5e716` was pushed to the private portfolio branch, then directly deployed
as Cloudflare version `e64fb1d6-73d7-4915-aa99-3327a794963a`. This did not change repository visibility.

Public response inspection found Cloudflare's automatic analytics beacon appended to HTML. The
existing CSP prohibits that external script/connect destination; nevertheless, an explicit Chikku-only
`Cache-Control: public, max-age=0, must-revalidate, no-transform` header was added in website commit
`1bbe460` to suppress injection and preserve the approved response bytes. The source archive bytes
and page contents are unchanged. Final Cloudflare version `e4058ac2-b23b-4375-8af1-84a89a0be8d6` serves all11 source assets and both existing privacy/demo HTML pages byte-for-byte as approved, with the no-transform header. Raw verification is in `approved-source-live-integrity.json`.
[Cloudflare's documented behavior](https://developers.cloudflare.com/web-analytics/faq/).

The published1840777 snapshot remains a preparation candidate and lacks the later source-reader,
recovery-layout and fresh-widget-size corrections. It must not be presented as the corrected app.
The newly committed corrected app needs its own clean signed/source pair and source entry before
external distribution; exact updated publication remains a separate reviewable deliverable.

## Genuine 16 KB emulator evidence

The owner specifically authorized a second disposable emulator and Android tools on that instance.
New `chikku_acceptance_16kb_20261017` used API36 Google APIs page-size16k revision7, ARM64,
2 cores/2,048MiB and serial `emulator-5582`. Its actual `getconf PAGE_SIZE` is **16384**.
The existing 4KB emulator was stopped; no Gradle process ran concurrently with this emulator.

Following [Android's current instructions](https://developer.android.com/guide/practices/page-sizes),
`bionic.linker.16kb.app_compat.enabled=false` and `pm.16kb.app_compat.disabled=true` were set and
read back on this disposable instance before installing the app. No production manifest override
or general device security setting was changed.

`NativePageSizeDeviceTest` passed, asserting16384 and actually initializing the packaged
AndroidX graphics-path JNI library and converting synthetic conics into finite quadratics.
The optimized nondebuggable production APK, locally signed with the standard debug certificate
without changing its payload, also installed and cold-launched successfully into actual onboarding.
The crash buffer was empty and the visible activity/XML/screenshot were retained. This is a local
installation, **not Play-signed**. The native reflection probe ran in the debug package; production
R8 removes that public reflective ABI, so no optimized reflective-probe pass is claimed.

This closes the missing ARM64/API36 16KB runtime smoke check. It does **not** establish other ABIs,
OEMs, full workflow coverage or conformance of every GNU_RELRO header to the stricter documented
formula. Existing ELF LOAD/ZIP alignment checks pass; the unchanged dependency's recorded RELRO
end offsets remain nonzero modulo16KB and need upstream review before a blanket compliance claim.
The emulator was stopped after evidence collection; daily devices were never operated.

## Retained checks and remaining gates

The37 prepared files exactly matched their16October handoff hashes before work resumed. Prior
checks remain:1,065 app unit tests,230 core tests (latest task up to date, earlier fresh pass),88 Python,
4 browser-client tests, optimized/debug builds, zero lint errors, privacy/static package gates,
9/9 fresh external-widget stages,6/6 native profile stages and genuine SAF/upgrade/reboot workflows.
See the16October report for their precise scope, original failures and corrected reruns. Remote
GitHub Actions is unavailable due to its account budget; it is not reported as passing.

Still needed: owner-provided testers, owner-secured upload-key backup, first-upload certificate
registration/Play-delivered signing verification, AccessibilityService-specific Console declaration
when exposed, broader physical/TalkBack/OEM/credential-lock/profile-derived-surface and performance
acceptance, and aggregate MET traffic/proxy review before broader rollout. The closed test remains
inactive with no uploaded bundle. The owner-approved178 selectable countries and feedback address
are saved. No production readiness or closed-test completion is inferred from these local checks.

Raw evidence: `chikku-release-evidence/2026-10-17/` includes deployment/build logs, public download
integrity, native16KB environment/test log, optimized APK/XML/screenshot/crash buffer and final
candidate/source manifests. Upload credentials remain outside Git and source archives.
