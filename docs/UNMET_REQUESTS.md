# Unmet and under-served user requests

**Date:** 2026-10-10. **Important distinction:** these are **public user requests or gaps**, not proof that Nova's owners have rejected or ignored them. We cannot see Nova's internal roadmap. Where a feature may already exist, mark it for hands-on verification. Sources are listed in [RESEARCH_SOURCES.md](RESEARCH_SOURCES.md).

## Opportunities ranked for Chikku

| Need | Public signal | Nova status / uncertainty | Feasibility | Chikku priority |
|---|---|---|---|---|
| Independent cover/inner foldable layouts | [S12][S13], Aug 2026 | Users report missing; verify latest Nova | Jetpack WindowManager + layout profiles | High, M2 |
| Search matching substrings, aliases and typos | [S15], Mar 2026 | Requested improvement, current ranking unverified | Pure local search | High, M1 |
| Widget stacks | [S15], Mar 2026 | Requested, current support unverified | Requires safe host/interaction prototype | Medium, M2 |
| Free widget resize independent of grid | [S15] | Requested; Android widget constraints apply | Partial via subgrid, not arbitrary RemoteViews control | Medium, M2 |
| Multiple dock rows | [S15] | Requested, current support unverified | Custom dock ViewGroup | Medium, M2 |
| Larger folders | [S15] | Requested, current support unverified | Folder layout and accessibility | Medium, M2 |
| Nested folders | [S35] | User interest; current Nova support unverified | Data model recursion + UX complexity | Low, M3 |
| Glass/blur dock, drawer and folder effects | [S15] | Requested, not necessarily essential | GPU/blur cost, reduced-motion fallback | Low, M3 |
| Different icon shapes on desktop/dock/drawer | [S15] | Requested | Per-surface settings + icon cache | Medium, M2 |
| Better foldable posture transitions | [S13] | User reports scaling/movement problems | Responsive grid and migration | High, M2 |
| Reliable cross-device backup with widget rebind | [S09][S10] | Historical failures, may be fixed | Versioned import + system bind consent | High, M1 |
| Privacy-verifiable ad-free launcher | [S01][S02][S31] | Free Nova contains ads; Prime promised ad-free; SDK claims disputed | No tracking SDK and auditable build | High, M0 |
| Faster stable app/search startup | [S07][S10][S11] | Historical crashes, some fixes claimed | Cancellable local index, fallback UI | High, M0 |
| Gesture search from home with keyboard focused | [S17] | Valued Nova workflow | Gesture router + local search | High, M1 |
| Folder cover: tap primary app, swipe folder | [S16][S32] | Already a Nova capability, not a missing Nova feature | Folder action model | High, M1 parity |
| Multi-action icon swipes for app clones/work apps | [S35] | Already valued Nova behavior | AppKey + userSerial actions | High, M1 parity |
| Private Space separation | [S22] Android platform | Requires current-build verification | Public API with HOME role and permission | High, M1 |
| Seamless system Recents gesture animation | [S18] and OEM complaints | Not fully controllable by normal launcher | **Not a universal promise** | Platform limitation |
| Google Discover-style embedded feed | [S14] | User desire; proprietary integration uncertainty | External handoff possible, embedded feed uncertain | Low, M3 |
| Transparent changelog, release cadence and compatibility | [S01][S05] | Trust/maintenance need rather than one UI feature | Governance and CI | High, continuous |

## What to validate with real users

1. Ask users to **rank five tasks they cannot lose**: folder covers, half-grid, icon swipes, drawer groups, dock search, backup.
2. Separate "Nova already does this" from "Nova does it poorly" from "Nova does not do this".
3. Reproduce on latest stable and beta; record version, OEM and Android release.
4. Ask foldable owners to demonstrate cover/inner transitions; do not assume all foldables behave alike.
5. For each requested visual effect, measure battery/jank and accessibility cost.
6. Publish accepted/deferred decisions with rationale rather than implying every request is on the roadmap.

## Feature selection rule

**First fix reliability and preserve Nova muscle memory.** A user is more likely to switch for a launcher that reliably restores their folder gestures and widgets than for a new glass effect with fragile HOME startup.

Related: [USER_PAIN_POINTS.md](USER_PAIN_POINTS.md), [NOVA_FEATURE_MATRIX.md](NOVA_FEATURE_MATRIX.md), [ROADMAP.md](ROADMAP.md).
