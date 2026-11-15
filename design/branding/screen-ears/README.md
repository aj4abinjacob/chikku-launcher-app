# Chikku app icon: Screen Ears

> Superseded the same day by [White Coat](../white-coat/README.md), a rounded-square version with an
> ivory frame and a brand palette. Screen Ears was never committed as the app icon. It is kept as
> design history, together with blind-test rounds r1–r11.

Selected on 4 November 2026. It replaces [Blaze House](../blaze-house/README.md). Original,
hand-authored vector artwork drawn from the owner's photo of Chikku. No image generator was used,
and no third-party, launcher or stock artwork was copied.

## Idea

The icon is a tan phone with an ivory screen. The screen shows a 2×2 grid of app tiles above a dock
bar. On top of the phone sit Chikku's big ears:
- the left ear stands upright
- the right ear flicks outward with its tip folded, as in his photo

The design reads as "home-screen app" first. The ears, his golden-tan and white colours and the
flat tonal shading carry Chikku. There are no eyes, nose or mouth.

| Role | Hex |
|---|---|
| Background | `#252626` (`@color/chikku_icon_background`) |
| Coat tan (frame, ears, tiles) | `#C98A4A`, with shades `#B57A3E`, `#B3763F` |
| Screen ivory | `#F4EFE6` |

## Why it changed from Blaze House: blind tests

Blaze House drew the house as his white blaze. People who already knew it was there could see the
house. Blind testers could not.

**The harness.** The files are in [blind-test/](blind-test/): `prep.py`, `ask.sh` and `survey.sh`.
- Each round runs five independent headless Claude (Sonnet) sessions in a neutral folder, with no
  project context.
- Each tester has a different everyday-user persona.
- Each tester sees only the icon, large and at 64px on a busy wallpaper, and answers what kind of
  app it is.
- Every round uses fresh testers.

**Pass rule:**
- all five top guesses are launcher, home-screen or app-organizer apps
- at least three describe a dog
- the pass is confirmed by a second fresh round

| Round | Candidate | Launcher | Dog |
|---|---|---|---|
| r1 | Blaze House | 0/5 (all "pet app", mostly "fox") | 5/5 |
| r2 | 3×3 tiles coloured as his face | 0/5 ("word puzzle") | 5/5 |
| r3–r4 | Tan phone with ears, pointed ears | 5/5 | 0–2/5 (cat/fox) |
| r5, r7 | With folded ear plus a nose | 2/5 ("pet app") | 5/5 |
| **r6, r8** | **Folded ear, no nose, grid + dock (this icon)** | **5/5, 5/5** | **3/5, 4/5** |
| r9–r11 | This icon plus a white blaze strip | 1–4/5 ("keypad") | 2–4/5 |

Findings:
- **The phone screen with an app grid and dock** carries the launcher meaning.
- **The folded right ear** is what makes testers say "dog" rather than cat or fox.
- **A nose** turns it back into a pet app.
- **A blaze strip** splits the frame so it looks like a keypad. The strip was rejected, and its
  sources are kept in `iterations/`.

[blind-test/survey-log.md](blind-test/survey-log.md) records every round. It also has the raw
answers in `answers/` and the candidates as thumbnails in `candidates/`. The log's absolute paths
point to the session scratchpad, which is not kept.

**Limits.**
- The testers are simulated personas, not people. The result is directional, and confidence
  ranged from 2 to 4 out of 5.
- Testers still often say "fox or dog" or "dog or cat". The brand review scored Chikku's identity
  3/5: it reads as his ears and colours, not his markings.
- Check with a few real people before release.

## Files and packaging

- [master.svg](master.svg): the background plus an 11-path foreground on the 108-unit adaptive
  canvas. Runtime: `app/src/main/res/drawable/ic_launcher_foreground.xml`, which has flattened
  coordinates, solid fills and no group transform. The furthest point is about 32.1 units from the
  centre, inside the 33-unit safe circle.
- [monochrome.svg](monochrome.svg): one silhouette with the screen and grid cut out, with gaps of
  at least 3.5 units. Runtime: `ic_launcher_monochrome.xml`, which Android 13+ tints.
- [play-512.png](play-512.png): a full-square, opaque, 32-bit RGBA PNG with embedded sRGB. It is
  the central 72dp area at 512px. It is a source export, not a published store asset.
- [review.png](review.png): previews in circle and squircle masks, at 48px and 24px, as the dark
  and light themed icon, and on a busy wallpaper.

The manifest, adaptive-icon XML and splash references are unchanged and use the new foreground.

## Verification

- `assembleDebug` and `lintDebug` pass.
- The previews were rendered from the SVGs.
- Not yet checked: appearance on a device or emulator, OEM masks, themed-icon tinting on a real
  launcher, and the splash screen.
