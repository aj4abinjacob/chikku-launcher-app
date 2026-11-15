# Chikku app icon: Nose notch / Ink

> Superseded on 2026-11-04 by [White Coat](../white-coat/README.md), after the Blaze House and
> Screen Ears explorations. The runtime resources now use White Coat; this directory is kept as
> history.

Selected by the owner on 15 October 2026. Original code-native vector artwork.
Foreground: `#F2F2F0`; background: `#252626`.

The SVGs retain a 108 by 108 adaptive canvas. The 0.9 scale around (54,54) is part
of the approved mark, not an extra Android inset. Keep foreground transparency,
full-bleed background and the separate monochrome silhouette. Do not bake an
outer mask or shadow into those layers. The transparent opening is intentional.

The runtime resources use the same path and transform. API 33+ adds an explicit
monochrome reference; older supported devices use the foreground/background.
The existing manifest icon reference works for all supported versions. minSdk 29
means no pre-26 legacy launcher bitmaps are necessary. The existing splash icon
reference uses the updated foreground and background colour as well.

`play-512.png` is a separate full-square, opaque 512px RGBA PNG, embedded sRGB,
without outer mask/shadow. It is a source export, not a published store asset.
Its composition uses the central nominal 72dp viewport, omitting adaptive overscan.

Checks: source path/transform match the chosen design; XML parses; foreground
bounds approximately 50.3 by 48.5dp, max radius 31.85dp about (54,54), within the
33dp safe circle. Circle/squircle/small/themed design previews were inspected.
Device-specific launcher motion and appearance remain separate validation.

Sources: [Adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive),
[Play icon specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).
