# Chikku app icon

> **Current icon (2026-11-04): [White Coat](white-coat/README.md),** chosen through blind tests.
> It superseded [Screen Ears](screen-ears/README.md) and [Blaze House](blaze-house/README.md),
> which were never committed, and Nose notch / Ink.
>
> Superseded on 2026-10-15 by [Nose notch / Ink](nose-notch-ink/README.md). The tail-shaped C
> below is no longer installed; its Android PNG layers were removed.

The installed icon is an orange-and-cream, softly sculpted tail-shaped C on charcoal (`#191919`),
adapted from the user's attached reference on 2026-10-14. Earlier dog-face images in this directory
remain concepts; they are not the installed icon.

## Assets and provenance

- [Generated foreground master](chikku-tail-c-master-v1.png): 1254 × 1254 transparent RGBA PNG,
  created with built-in imagegen. [Exact generation prompt](chikku-tail-c-v1.prompt.txt).
- [Google Play icon](chikku-play-icon-512.png): 512 × 512, 32-bit RGBA PNG with embedded sRGB profile,
  opaque full-square charcoal background, 198,996 bytes (below the 1024 KB limit). No rounded tile,
  outer shadow or surrounding mockup; volume and lighting belong to the emblem itself.
- [Mask preview](chikku-icon-mask-preview.png): simulated store square, Android circle/rounded-square
  and themed circle, rendered from the exported layers. This is an asset preview, not a device screenshot.
- Android `ic_launcher_foreground.png` and `ic_launcher_monochrome.png` live in
  `app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}`. Every canvas is 108 dp:
  108, 162, 216, 324 and 432 pixels respectively. The monochrome version uses the same alpha
  silhouette with white RGB, so Android can tint it. Both layers have transparent backgrounds.
- [Adaptive definition](../../app/src/main/res/mipmap-anydpi/ic_launcher.xml) combines the foreground
  with the charcoal color and supplies the monochrome layer. Existing manifest, splash and About
  references use the new resources. The separately installed Discover bridge retains its own icon.

The supplied reference was used as an image-edit input. Its black rounded tile was removed by
imagegen; the generated foreground was inspected against charcoal before packaging. No Android,
Nova, Lawnchair, icon-pack or stock-library artwork was copied. The user authorized this reference
adaptation, but did not state the reference's origin or license. Record those rights before public
distribution alongside the repository's existing [licensing review](../../docs/LICENSING_AND_LEGAL.md);
this change does not select a public asset or repository license.

## Packaging

The generated master is preserved unchanged. Exports use its centered 1100 × 1100 region starting
at pixel (83, 81). For Android, that region is resized to a 62 dp square and centered in the
108 dp transparent layer, keeping the visible emblem inside Android's central 66 dp safe zone.
The store version resizes the same region to 384 × 384 and centers it at (64, 64) on the 512px canvas.
Resampling, layer placement, monochrome tint and PNG export used Sharp from the bundled workspace
runtime; it is not an app dependency. No live data, preferences or backups change.

Checked official guidance on 2026-10-14:

- [Google Play icon design specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications):
  store-listing assets are full square, 512px, 32-bit PNG, sRGB, at most 1024 KB. Play supplies the
  outer rounding and shadow; lighting inside artwork is allowed.
- [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive):
  installed icons use separate 108 dp layers, a central 66 dp safe zone and an optional monochrome
  layer. Store and APK assets serve different purposes.

## Verification (2026-10-14)

Asset checks passed: store dimensions, 32-bit format, embedded sRGB, opaque pixels and file-size
limit; all five Android density canvases; exact foreground/monochrome alpha equality and containment
inside the central 66 dp safe zone. Square/circle/rounded-square/themed simulations were inspected.
`assembleDebug`, `:core:model:test`, `testDebugUnitTest` and `lintDebug` passed on macOS with Android
Studio's JBR 25.0.2; the core suite was up to date and the app unit suite ran against the changed
resources. `scripts/check-privacy.sh` passed. These checks establish packaging and local regressions.
Physical-device/emulator appearance, OEM mask animation, themed-icon behavior and Android splash
appearance have not been verified for this artwork. No connected instrumentation suite was run.
