# Competitor landscape and product differentiation

**Snapshot:** 2026-10-10. This is a **qualitative research shortlist**, not a benchmark. Only Nova and Lawnchair have been source-checked in detail. Do not publish comparative scores until hands-on testing on the same device/build.

| Launcher | Why compare | Strength / signal | Unverified questions |
|---|---|---|---|
| **Nova Launcher** | Feature-depth benchmark | Official icon packs, backup, gestures, drawer groups, hidden apps [S02][S03] | Which features survive in latest build? How stable on Android 17/foldables? |
| **Lawnchair 16** | Open-source architecture benchmark | Launcher3-derived, Material styling, global search, QuickSwitch root integration [S26] | Build/release stability, parity with Nova dock/folder gestures, current license scope |
| **Kvaesitso** | Open-source search-first alternative | Candidate for UX comparison, not yet audited | Search depth, widget/grid flexibility, source/license and update cadence |
| **KISS Launcher** | Minimalist open-source reference | Candidate for offline search/performance comparison | Gesture and grid feature gaps, real-device latency |
| **Smart Launcher** | Commercial feature-rich competitor | User discussions cite alternative folder behavior and modern layout ideas [S32] | Actual parity, pricing, foldable UX, privacy |
| **Octopi Launcher** | Nova-replacement candidate | Frequently mentioned by Nova switchers [S14][S16] | Maintainer status, license, backup, gesture/Recents behavior |
| **Niagara Launcher** | Distinct minimal UX | Alternative interaction model worth testing | Not a direct Nova parity target; which workflows transfer? |
| **OEM Pixel / One UI / OxygenOS** | System integration reference | System HOME can have privileged gesture/Recents behavior | Which UX gaps can a normal third-party app actually close? |

## Test tasks (identical on each launcher)

1. Import/create a 5-page layout with 50 icons and 6 widgets; restart phone.
2. Configure subgrid placement and folder cover: tap launches primary app, swipe opens folder.
3. Create app-drawer folder/tab and hide a non-sensitive app.
4. Add dock search, multiple dock pages and per-icon swipe actions.
5. Add/configure/resize/remove widgets and change icon pack.
6. Search apps, aliases, shortcuts and optional contacts with airplane mode on.
7. Export backup, move to another Android version/OEM, restore with missing widgets.
8. Test work profile, Android 15+ Private Space and foldable cover/inner displays.
9. Measure cold HOME, drawer latency, jank, memory and crash behavior.
10. Review permissions, tracker/network activity and open-source release process.

## Chikku positioning

Do not win by claiming every competitor is broken. Win through:
- Reliable migrations, widgets and crash recovery.
- Nova-level high-value controls: subgrid, folder covers, dock pages, icon gestures and drawer groups.
- Open-source, no-ad, offline-first, auditable permissions.
- Explicit support for foldables and modern Android profiles.
- Public compatibility dashboard and regular maintenance.

## Risks

Nova is actively maintained under Instabridge as of 2026 [S01][S11]; competing only on "Nova is dead" is strategically unsound. Lawnchair already offers a mature Launcher3-based open-source alternative [S26]. Chikku must demonstrate a specific improvement and sustainable maintenance capacity.

**Next step:** replace qualitative entries with versioned test results and links. Research source register: [RESEARCH_SOURCES.md](RESEARCH_SOURCES.md).
