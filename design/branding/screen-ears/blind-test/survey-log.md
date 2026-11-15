# Blind icon survey log (icon2)

Harness: `/private/tmp/claude-501/-Users-abinjacob--claude-spares-spare-umZZ6A/ddd531b1-bcbc-4cc7-9fa9-dc0b44cbe22c/scratchpad/ut/tools/survey.sh` (5 headless sonnet testers, personas Priya, Marcus, Helen, Diego, Aisha; they see only icon.png and icon-small.png).
Candidate PNGs: `/private/tmp/claude-501/-Users-abinjacob--claude-spares-spare-umZZ6A/ddd531b1-bcbc-4cc7-9fa9-dc0b44cbe22c/scratchpad/icon2/png/roundN-cand.png`; sources in `/private/tmp/claude-501/-Users-abinjacob--claude-spares-spare-umZZ6A/ddd531b1-bcbc-4cc7-9fa9-dc0b44cbe22c/scratchpad/icon2/src/`; answers in `/private/tmp/claude-501/-Users-abinjacob--claude-spares-spare-umZZ6A/ddd531b1-bcbc-4cc7-9fa9-dc0b44cbe22c/scratchpad/ut/rN/answer-*.txt`.
Pass = (a) 5/5 TOP GUESS launcher/home-screen/app-organizer, (b) >=3/5 WHAT I SEE mention dog, (c) no browser-dominated guess.

| Round | Candidate | Description | Top guesses (1-5) | Launcher | Dog | Browser | Result |
|---|---|---|---|---|---|---|---|
| r1 | blaze-house/play-512.png | Blaze House (installed): tan head, blaze drawn as house | pet app; pet app; pet/animal-care; pet/animal-care; pet app | 0/5 | 5/5 ("fox or dog", 4/5 fox) | in others | fail |
| r2 | png/round2-cand.png (src A.svg) | 3x3 tile grid coloured as Chikku's face, ears on top, dark nose tile | puzzle/word game; puzzle/word game; puzzle/word game; pet app; puzzle/word game | 0/5 | 5/5 | 0 | fail (reads Wordle) |
| r3 | png/round3-cand.png (src B.svg) | tan phone with ears, ivory screen, 2x2 tiles + dock bar, pointed right ear | launcher/app-organizer; home-screen customizer/launcher; organizer/launcher; launcher customizer; launcher/customizer | 5/5 | 0/5 (cat/fox) | 0 | fail (b) |
| r4 | png/round4-cand.png (src B2.svg) | as r3, no dock, dark dog nose under screen | organizer/launcher; home-screen customizer; "fox or dog app for organizing your phone apps"; launcher/organizer; organizer/launcher | 5/5 | 2/5 | 2 in others | fail (b) |
| r5 | png/round5-cand.png (src B3.svg) | as r4 with right ear flicked out and folded at the tip | pet app; customization/launcher; "app organizer for dogs, like pet-care"; pet-care; home-screen organizer | 2/5 | 5/5 | 0 | fail (a) |
| r6 | png/round6-cand.png (src B4.svg) | folded right ear, no nose, 2x2 tiles + dock bar | launcher/home-screen organizer; home screen customizer/icon pack; home-screen/app launcher; home screen/launcher customizer; customization/app-organizer | 5/5 | 3/5 | 0 | PASS |
| r7 | png/round7-cand.png (src B5.svg) | B4 plus dark nose under screen | launcher/app-organizer; home-screen customizer/launcher; pet app; pet-care; pet care | 2/5 | 5/5 | 0 | fail (a) |
| r8 | png/round8-cand.png (src B4.svg, unchanged) | confirmation of r6 design | launcher/app-organizer; launcher/icon-pack customizer; phone-organizer/app-launcher; home screen/launcher customizer; launcher/home-screen customizer | 5/5 | 4/5 | 0 | PASS (confirmed) |

Notes
- r2-r3, r4-r5, r6-r7 were run as parallel pairs to compare directions.
- The nose is the switch between "launcher" and "pet app": with the nose, 2/5 launcher (r5, r7); without it, 5/5 (r6, r8).
- The folded right ear is what makes it "dog": pointed ears gave cat/fox (r3, r4), the fold gave dog mentions (r5-r8).
- Confidence stayed 2-4 throughout; nobody was sure, but every launcher guess was the top guess.

## Blaze-strip test (brand tweak, rejected)

final.svg + an ivory strip (3.6 units, ~10% of frame width) from the frame top, centred, into the screen; rounded top (final/final_blaze.svg, mono final/final_blaze_mono.svg).

| Round | Candidate | Description | Top guesses (1-5) | Launcher | Dog | Browser | Result |
|---|---|---|---|---|---|---|---|
| r9 | png/round9-cand.png | blaze strip 3.6 wide | phone-style/app-launcher utility (maybe pet); launcher/customizer; launcher/customizer; launcher/widget customizer w/ pet theme; phone game/kids app | 4/5 | 2/5 | 0 | fail |
| r10 | png/round9-cand.png (same image, fresh dir) | blaze strip 3.6 wide | apps/widgets/notes organiser (pet-themed); home-screen customizer/launcher; pet/dog-care; pet care/smart pet device; "phone-themed app, like a dog or pet app" | 1-2/5 | 4/5 | 0 | fail |
| r11 | png/round11-cand.png | narrower strip, 2.4 wide | phone keypad/calculator; launcher; phone utility/calculator-style; pet-care; customization/app-organizer | 2/5 | 2/5 | 0 | fail |

Finding: the strip splits the top bezel, so the screen reads as a keypad, calculator or panel, not a phone. "Keypad/calculator" appeared in 8 of 15 answers, against 0 for r6/r8. The current final (no strip) stays.
