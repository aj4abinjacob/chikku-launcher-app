# Open decisions and research gaps

**Last reviewed:** 2026-10-10. Resolve questions with evidence and an ADR; do not silently turn assumptions into requirements.

| ID | Question | Owner | Next action | Deadline trigger |
|---|---|---|---|---|
| Q01 | Original Kotlin implementation vs Launcher3 fork? | Maintainer | Two-week widget/drag/HOME spike; compare fork cost | Before M0 architecture freeze |
| Q02 | Open-source license? | Maintainer | Audit upstream licenses and distribution goals | Before public release |
| Q03 | Minimum Android version? | Maintainer | Measure device demand/support cost of API 29 vs 31 | Before project generation |
| Q04 | targetSdk 37 timing? | Android lead | Validate widget RemoteViews memory and adaptive behavior | Before target bump |
| Q05 | Nova feature list accuracy in current stable/beta? | Research | Hands-on screenshots, versioned verification | Before parity claims |
| Q06 | Nova backup import possible/legal? | Research/legal | Inspect user-owned backup format with consent, assess license | Before import feature |
| Q07 | Private Space behavior on OEMs? | Android lead | API 35–37 physical device tests | Before beta |
| Q08 | Foldable profile UX and migration? | UX lead | Interview Fold users; prototype cover/inner | Before M2 |
| Q09 | Google feed/Sesame-like integration feasible? | Research | Official SDK/API and privacy/legal review | Before M3 |
| Q10 | Icon pack protocols? | Android lead | Test popular icon packs and licensing | Before M1 |
| Q11 | CI device farm and release signing? | Maintainer | Select infrastructure and secrets management | Before alpha |
| Q12 | F-Droid and Play distribution? | Maintainer | Verify reproducible builds, policy, package ID | Before public beta |
| Q13 | Sustainable maintainers/funding? | Maintainer | Governance and succession plan | Before 1.0 |
| Q14 | Crash report collection without surveillance? | Security lead | Design user-controlled redacted export | Before alpha |
| Q15 | Quantified top user needs? | Research | 15–30 interviews and task-based comparison | Before roadmap reprioritization |
| Q16 | Which OEM gesture bugs are platform-only? | Android lead | Reproduce with latest OS and alternate launchers | Before promising fixes |

## Assumptions to verify

- Nova's 2024–26 reported bugs are **not necessarily present in current builds**.
- AOSP Launcher3 patterns may reduce risk but are not automatically a Kotlin-first codebase.
- No third-party launcher can guarantee stock Recents animations on every OEM.
- Current app-store requirements change; recheck policy before submission.
- Feature parity should be measured by real workflows, not raw feature count.

## Decision record format

Date; problem; evidence links; alternatives; chosen option; consequences; rollback condition; next review date. Add ADR files under [decisions](decisions/).
