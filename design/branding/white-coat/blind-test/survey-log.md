# Blind icon survey log, rounds r12–r29

This log continues [Screen Ears' log](../../screen-ears/blind-test/survey-log.md), which covers r1–r11, and uses the same harness: [survey.sh](survey.sh), [ask.sh](ask.sh) and [prep.py](prep.py).
Raw answers are in `answers/rN/`. Candidate thumbnails are in `candidates/`: roundN-cand.png, and palette-{A,B,C}.png for r25–r29. Paths in the tables below point to the original session scratchpad, which is not kept.

## Aesthetic polish round (P1-P3)

Shared changes: background #1F1D1C (P1 #211F1D); golden tan #CF9450; ears rise from the frame shoulders, curving down into its sides; folded right flap in its own tone; frame 35x43.5, corner radius 8.5 (circular C-curve corners); uniform 3-unit gaps; tile radius 3.2; dock is a pill; mark nudged for optical centre; max radius 31.4. Sources: polish/P*.svg, generator icon2/pol.py.

| Round | Candidate | Description | Top guesses (1-5) | Launcher | Dog | Browser | Result |
|---|---|---|---|---|---|---|---|
| r12 | png/round12-cand.png (P1) | refined flat version | launcher/home-screen customizer; customization/launcher; launcher/app-organizer "for a dog or pet"; pet-care/tracker; pet/animal-care | 3/5 | 5/5 | 0 | fail (a) |
| r13 | png/round13-cand.png (P2) | P1 + lit left edge, shadowed right/bottom edge, screen inset shadow, alternating tan/deep-tan tiles | launcher/home-screen customizer; customization/icon pack/launcher; phone-organizing/launcher; launcher/home-screen customizer; pet-themed customizer or launcher | 5/5 | 5/5 | 0 | PASS (single round, not yet confirmed) |
| r14 | png/round14-cand.png (P3) | tilted -7 degrees, rust accent tile, inset shadow, lighter flap | launcher/customizer; home-screen customizer/launcher; phone-themed pet or cat/fox app; pet-themed launcher/customization; home screen themer | 4/5 | 1/5 (fox/cat) | 0 | fail (b) |

## Square-body test (owner request, rejected)

P2 tones, ears, inset lip and alternating tiles, with the body changed to a rounded square. Generator: icon2/sq.py.

| Round | Candidate | Description | Top guesses (1-5) | Launcher | Dog | Browser | Result |
|---|---|---|---|---|---|---|---|
| r16 | png/round16-cand.png (Q = Q1) | 41x41 body, R11, tiles 8.25, dock pill, mark scaled 0.92 | pet/dog app; launcher customizer; pet care; pet/dog app; pet-themed launcher/customizer | 2/5 | 5/5 | 0 | fail |
| r17 | png/round16-cand.png (same image, fresh dir) | as r16 | pet/dog app; customization/launcher; pet care; pet-care/services; pet/dog app | 1/5 | 5/5 | 0 | fail |
| r18 | png/round18-cand.png (Q3) | fix attempt: 42x44 squircle, tiles 9.75, scaled 0.91 | pet/dog-care; launcher/customizer; pet care/tracker; pet care/dog; pet-sitting | 1/5 | 5/5 | 0 | fail |
| r19 | png/round18-cand.png (same image, fresh dir) | as r18 | pet/dog app; launcher/customizer; pet/dog-care; pet care; pet care | 1/5 | 5/5 | 0 | fail |

Finding: a square body reads as the dog's face or head, with the screen as a "cream face", so 4 in 5 testers guessed a pet app. The tall phone proportion (P2) is what carries the launcher meaning. P2 stays the pick. (r15 was not used in this step.)

## Colour test on the square shape (Q geometry unchanged, generator icon2/sq_k.py)

| Round | Candidate | Description | Top guesses (1-5) | Launcher | Dog | Browser | Result |
|---|---|---|---|---|---|---|---|
| r20 | png/round20-cand.png (K1) | ivory screen; tiles indigo #3F5FA8, teal #4FA89A, rust #B94318, mustard #E0A93B; grey dock | pet/dog-care; launcher/icon customizer; "pet or dog-themed app, or ... organizes your phone's apps"; home-screen organizer/launcher; launcher/home-screen organizer | 3/5 | 5/5 | 0 | fail |
| r21 | png/round21-cand.png (K2) | indigo #1E3A6B screen with lighter #34558F lip; tan/ivory tiles; cream dock | app-organizer/launcher; home-screen customizer/launcher; organizer/launcher "maybe for kids"; launcher/app-organizer; pet care/sitting | 4/5 | 5/5 | 0 | fail (a) |
| r22 | png/round22-cand.png (K3) | indigo screen + K1 tiles | launcher/app organizer; home-screen customizer/icon pack; "simple home screen / phone helper"; home-screen/app-organizer/launcher; pet-care | 4/5 | 5/5 | 0 | fail (a) |
| r23 | png/round23-cand.png (K4) | ivory "white coat" frame (tonal #FFFDF8 / #F5F0E6 / #DDD2C0), tan ears, indigo screen, K1 tiles, cream dock | launcher (organizes apps); home-screen customizer/launcher; phone helper/launcher; launcher/app organizer; customization/app-organizer | 5/5 | 5/5 ("dog or fox") | 0 | PASS |
| r24 | png/round23-cand.png (same K4 image, fresh dir) | confirmation | launcher/home-screen customizer; home-screen customizer/launcher; organizer/home-screen customizer; home-screen organizer/launcher; launcher/home-screen customizer | 5/5 | 5/5 ("dog or fox") | 0 | PASS (confirmed) |

Finding: the colour of the body, not the screen, was the "face" trigger. With a tan body, the square reads as a dog's head. With an ivory frame (Chikku's white coat), it reads as a cream phone wearing tan ears. Colour tiles or a dark screen alone helped partly (3-4/5). Caveat: every dog mention in K4 was "dog or fox".

## Brand palettes on K4 (owner: "don't take the screen colours from the photo")

A brand colour specialist built three palettes from the app's own theme, not the photo. The K4 geometry, frame and ears are unchanged.

| Round | Candidate | Palette | Top guesses (1-5) | Launcher | Dog | Result |
|---|---|---|---|---|---|---|
| r25 | candidates/palette-A.png | A Espresso: brown screen #2B211D; amber, sage, terracotta, soft blue | launcher; launcher (pet theme); launcher/organizer; pet/dog app; launcher (pet theme) | 4/5 | 5/5 | fail (a) |
| r26 | candidates/palette-B.png | B Ink-slate: slate screen #1B2A33; amber, sea-green, burnt orange, periwinkle | launcher/organizer ×5 | 5/5 | 5/5 | PASS |
| r27 | candidates/palette-C.png | C Ember: navy screen #1D2430; ember, gold, sage, dusty rose | launcher (pet theme); launcher; launcher/organizer; launcher; launcher/organizer | 5/5 | 5/5 | PASS |
| r28 | candidates/palette-B.png (fresh dir) | B confirmation | home-screen customizer/launcher ×5 | 5/5 | 5/5 | PASS (confirmed) |
| r29 | candidates/palette-C.png (fresh dir) | C confirmation | launcher/organizer ×5 | 5/5 | 4/5 | PASS (confirmed) |

Owner selected B Ink-slate (the specialist's recommendation) on 2026-11-04. r15 holds answers from a run that the comparison did not use.
