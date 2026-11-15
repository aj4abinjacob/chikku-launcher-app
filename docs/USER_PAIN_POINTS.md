# User pain points, hypotheses and testable fixes

**Evidence snapshot:** 2026-10-10. These are **reported experiences**, not prevalence estimates. Old Nova bugs may have been fixed; the goal is to avoid repeating failure modes. References: [RESEARCH_SOURCES.md](RESEARCH_SOURCES.md).

Priority: **P0** HOME unavailable, data loss or security; **P1** core workflow broken; **P2** major gap; **P3** polish. Status of Nova issues is **unknown on current build** unless specifically noted.

| ID | User problem / need | Evidence | Root-cause hypothesis (not established) | Chikku response | Priority |
|---|---|---|---|---|---|
| U01 | Search typing crashes launcher | [S07] 2025; fixes claimed [S11] | Stale result or UI state | Pure cancellable search; crash isolation; fuzz tests | P0 |
| U02 | Setup or first launch crashes | [S06] 2026 | Startup race/migration | Transactional migrations; recovery screen | P0 |
| U03 | Icons vanish after returning home | [S06][S10] | Cache/profile event race | Stable IDs, package observer, placeholder repair | P1 |
| U04 | Widgets freeze or disappear after OS update | [S08] | Host lifecycle/OEM | Widget host tests; rebind and fallback | P1 |
| U05 | Wallpaper picker/action widget crashes | [S29], reportedly fixed in 8.7.7 | Broken provider intent | Defensive intents; system picker fallback | P1 |
| U06 | Backup/restore screen crashes | [S09] | Settings navigation/permissions | Isolated export flow; instrumentation | P0 |
| U07 | Restore incomplete on new phone | [S10] | App/widget IDs and grid mismatch | Import preview, app mapping, widget placeholders | P1 |
| U08 | External search intermittently blank | [S10] | External IPC/lifecycle | Always-working offline local search | P1 |
| U09 | Home/Recents gesture freeze on OEM | [S18][S10] | System-owned navigation | Capability check, graceful fallback; disclose limits | P1 |
| U10 | Asked to reselect HOME after crash | [S06] | Process crash / role handling | Safe startup and recovery | P0 |
| U11 | Update regresses working workflow | [S07][S06] | Incomplete compatibility matrix | CI smoke tests, beta, rollback artifacts | P0 |
| U12 | Ads/tracker uncertainty erodes trust | [S01][S02][S31] | Monetization and disputed SDK behavior | No ad/analytics SDK; permission/network audit | P1 |
| U13 | Maintenance roadmap unclear | [S01][S05] | Ownership continuity | Public roadmap, release cadence, succession policy | P1 |
| U14 | Foldable needs independent cover/inner layouts | [S12][S13] | Single layout model | Display/posture profiles and migration | P1 (M2) |
| U15 | Folder cover tap/swipe muscle memory | [S16][S32] | Alternative launchers omit it | Tap app / swipe folder; reverse configurable | P1 |
| U16 | Half-grid placement | [S14][S36] | Coarse occupancy model | Subgrid with collision tests | P1 |
| U17 | Drawer tabs/folders and hidden apps | [S03][S14] | Flat drawer alternatives | First-class groups, accessible sorting | P1 |
| U18 | Paged dock and search below dock | [S14][S35] | Limited layout options | Dock pages and flexible search position | P1 |
| U19 | Substring, alias and fuzzy search | [S15] | Prefix-only match | Deterministic local search ranking | P2 |
| U20 | Stacked widgets and free resizing | [S15] | Grid/widget constraints | Prototype stack host, preserve widget semantics | P2 |
| U21 | Large/nested folders and glass | [S15] | UX/theme gap | Research/prototype, not assumed MVP | P2/P3 |
| U22 | Per-surface icon shapes | [S15] | Global theme setting | Separate home/dock/drawer configs | P2 |
| U23 | Multiple app actions per icon | [S35] | Alternatives lack icon gestures | Gesture-to-action mapping with preview | P1 |
| U24 | Pull down notifications from desktop | [S17] | Gesture/system action restrictions | Use supported API/fallback, no privileged hacks | P1 |
| U25 | Google feed/provider choice | [S14][S35] | Proprietary integration | Documented external app handoff; no scraping | P3 |
| U26 | Private Space/work apps privacy | [S22] Android requirement | Hidden profile state leakage | Dedicated locked container, search isolation | P0 |
| U27 | Android 17 widget/memory failures | [S23][S24] Android requirement | RemoteViews bitmap limits | Target-37 widget memory stress tests | P0 |
| U28 | Gesture conflicts with Back/TalkBack | [S34] Android requirement | Edge gesture regions | Insets-aware hit testing and accessibility tests | P1 |

## Release-blocking acceptance criteria

**Search (U01/U08):** 10,000 synthetic apps/shortcuts with malformed labels/icons and rapid queries cause no unhandled exception or UI hang. Older queries cannot replace newer results. Index failure must not crash HOME.

**Startup (U02/U10):** Interrupted migrations, corrupt optional settings and missing icon packs open a safe recovery view, not a crash loop. Layout data is never silently deleted.

**Icons (U03):** App install/update/removal, work-profile quiet mode, icon pack change, rotation and process death retain valid placements. Missing apps show an explicit placeholder.

**Widgets (U04/U05):** Add, bind, configure, resize, move and delete manage widget IDs correctly; broken providers cannot prevent HOME rendering. Imported widget IDs are never reused across devices.

**Backup (U06/U07):** Export has schema version and checksum; import previews missing apps/widgets; invalid input leaves the original workspace untouched; old fixtures remain importable.

**Foldables (U14):** Separate inner/cover layout profiles survive posture change, process death and rotation without overlap or loss.

**Private Space (U26):** Locked apps are absent from drawer, search, predictions, logs and backup previews. "Hide apps" is not marketed as a secure vault.

## Platform limits that cannot simply be fixed

Non-system launchers cannot universally replace OEM/system Recents/Quickstep, gesture animations, restricted status-bar actions or proprietary Google feed behavior [S18][S26]. Do not promise these capabilities on every stock unrooted phone.

## Research still needed

- 15–30 structured interviews with Nova users on Pixel, Samsung, OnePlus, Motorola and foldables.
- Current stable/beta Nova hands-on reproduction on Android 15, 16, 17.
- Re-test historical bugs and mark fixed/unfixed/unknown; the February 2026 team post [S11] explicitly claims fixes.
- Compare competing launchers with identical tasks, not marketing screenshots.

## New GitHub issue checklist

Device/OEM; Android build; launcher build; HOME role; exact steps; expected/actual; reproducibility; redacted logs; source/evidence grade; platform vs app cause; acceptance test; verification build.
