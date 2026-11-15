# Chikku app icon: Blaze House

> Superseded the same day by [Screen Ears](../screen-ears/README.md), and then by
> [White Coat](../white-coat/README.md). In blind tests, 0/5 testers
> recognised it as a launcher (they read it as a pet app, mostly a fox), so it was never committed
> as the app icon. It is kept as design history.

Proposed on 4 November 2026 to replace [Nose notch / Ink](../nose-notch-ink/README.md). Original,
hand-authored vector artwork. It was drawn from the owner's photo of Chikku. No image generator
was used, and no third-party, launcher or stock artwork was copied.

## Idea

Chikku's tan head and large upright ears form the outer silhouette. His white forehead blaze,
which widens into his muzzle, is drawn as a house, Android's long-standing "home" glyph. Read at a
glance it is a home app. On a second look it is a particular tan-and-white dog. There are no eyes,
nose or mouth, so it is not a dog face.

Traits taken from the photo:
- tan head with a white central blaze
- big triangular ears, asymmetric: the left stands up, the right flicks about 28° outward and sits
  lower

The asymmetry and the muzzle taper keep it from reading as a cat or as horns.

| Role | Hex |
|---|---|
| Background | `#252626` (unchanged charcoal, `@color/chikku_icon_background`) |
| Coat tan | `#C98A4A` (5.2:1 on charcoal) |
| Blaze / house ivory | `#F4EFE6` |

Flat tonal shading, lit from the top-left, all solid fills with no gradients:

| Tone | Hex |
|---|---|
| Lit edge, left side of the head | `#D69A5A` |
| Shadow on the right cheek and ear, and the rim under the crown | `#B3763F` |
| Inner ears, left / right | `#B57A3E` / `#A86E38` |
| Shaded right wall of the house | `#E2D7C6` |
| Lit edge of the left roof slope | `#FDFAF4` |

The inner ears are tan, not pink. Pink inside pointed ears is the usual shorthand for a cat or
fox, and Chikku's pink is on his nose.

## How it was chosen

The brand strategist and the icon specialist each took a role in a review loop. The
[round 1](review-round1.png) sheet compares four directions:
- **A1:** the blaze is the house
- **A2:** a tan roof with an ear as a chimney
- **B:** ears peeking over a dock
- **C:** a 2×2 app grid with a dog tile

The rejected sources are in `iterations/`. Findings:
- Putting ears on a roof always gave three spikes, which read as a crown or a castle.
- B read as a cat behind a sign.
- C was the most "launcher" but lost Chikku at 24px. It is kept as a possible onboarding or
  marketing motif.

[Round 2](review-round2.png) changed A1 to fix the cat and fox read:
- a wider head with a domed crown
- broader ears with asymmetric tilt
- a muzzle taper
- a larger house

Round 3 added depth after the owner asked for "a few more shades".
[Round 3](review-round3.png) compares three versions:
- **S1:** flat tones
- **S2:** soft gradients
- **S3:** gradients plus accents on a brown-charcoal background

The strategist chose S1. The gradients added nothing at icon size and S3 looked glossy. In
[round 4](review-round4.png) S1's pink inner ears became tan, and the owner selected that version.
The flat R2 and the other shaded versions are kept in `iterations/`.

## Files and packaging

- [master.svg](master.svg): a background rect plus the 9-path shaded foreground, on the 108-unit adaptive
  canvas. Runtime: `app/src/main/res/drawable/ic_launcher_foreground.xml`, with the same
  flattened paths and no group transform.
- [monochrome.svg](monochrome.svg): unshaded, since themed icons are one colour. The head with a house-shaped notch plus a separate house,
  with a gap of about 2.1 units. Runtime: `ic_launcher_monochrome.xml`, which Android 13+ tints.
- [play-512.png](play-512.png): a full-square, opaque, 32-bit RGBA PNG with embedded sRGB. It
  is the central 72dp viewport scaled to 512, with no mask or shadow. It is a source export, not a
  published store asset.

The furthest foreground point is at radius ≈31.3 from (54,54), inside the 33-unit safe circle. The
manifest, adaptive-icon XML and splash references are unchanged and pick up the new foreground.

## Verification and limits

Asset previews were rendered from the SVGs:
- circle and squircle masks
- 48px and 24px
- monochrome
- on a busy wallpaper next to dummy icons

The debug build compiles with the new resources. Not yet verified:
- appearance on a device or emulator
- OEM masks
- themed-icon tinting on a real Android 13+ launcher
- splash appearance

A "fox or shiba" read is still possible because of the tan and white colouring with pointed ears.
A quick test with people who don't know Chikku is recommended before release.
