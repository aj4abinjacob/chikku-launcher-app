# Research: building an open-source Nova alternative

Research date: **10 October 2026**. This is desk research, not an APK audit, representative survey, or device reproduction. This dated supplement extends the main [research summary](../../RESEARCH_SUMMARY.md). Primary product/architecture decisions remain in the main documents. Source IDs resolve in [the source register](04-sources.md). Recommendations are our synthesis.

## What changed at Nova

Instabridge announced its acquisition on 20 January 2026 and committed to maintenance [NS04]. Its current FAQ confirms advertising in the free edition and ad-free Prime, with annual and lifetime options [NS02]. The Play listing was updated on 7 October 2026 [NS11]. Therefore, describing Nova as currently abandoned is inaccurate.

Nova's publicly readable beta history still starts with June 2024 entries [NS06, NS07]. That page is useful historical evidence, not a trustworthy latest-version index. We did not independently determine the newest version available to every region or rollout channel.

The April 2026 privacy policy describes advertising-related data processing and third-party sharing, depending on service use and consent [NS03]. This creates a credible privacy concern. It does not prove every listed SDK is active on every device or that every possible category of data is collected from every user.

A February owner post describes crash fixes and preservation of existing Prime purchases [NS12]. Users nevertheless reported ads despite owning Prime in January, with mixed outcomes and workarounds [NS13]. These are reported failures, not proof that the official ad-free commitment was deliberately revoked.

## The actual product opportunity

The strongest opportunity is a trustworthy, customisable daily-driver launcher. Removing ads alone is insufficient. People use different combinations of dock pages, icon actions, folders, widgets, and layouts; alternatives can satisfy one setup while missing another [NS21, NS22].

Chikku should retain freedom without requiring people to navigate a maze of settings. A simple initial setup and searchable advanced controls can coexist. Customisation should not require online services, app-usage surveillance, or a mandatory subscription.

## Evidence method

We reviewed:

- Nova's site, FAQ, policy, feedback page, Play listing, and historical release documents.
- Dated Reddit reports from 2024–2026, including recent Private Space requests.
- Primary GitHub discussions and the source/licence/release information of candidate open-source launchers.
- Android/AOSP documentation for profiles, widgets, launcher APIs, notifications, wallpaper, and system integration.

Search families included Nova crashes, widgets, backup restoration, badges, sorting, Private Space, ads, maintenance, and replacement difficulties. Recent sources were checked against older reports to avoid treating a fixed historical defect as a current universal defect.

Evidence grades:

| Grade | Meaning | Appropriate use |
| --- | --- | --- |
| A | Official product/platform documentation or repository artifact | Documented capability, API constraint, published policy |
| B | Direct user report with identifiable context | Workflow hypothesis and reproduction case |
| C | Indexed excerpt when full retrieval failed | Supporting signal; lower confidence and explicit limitation |
| P | Product or engineering proposal | Our recommendation; no implied external proof |

There is no statistically defensible prevalence estimate. Search ranking, angry-user selection, regional rollouts, duplicate reports, and incomplete version information bias this sample. Vote counts are not user-population percentages. We have not inferred market size from installs or complaint counts.

## Findings worth acting on

| Theme | Evidence | Interpretation |
| --- | --- | --- |
| Trust and privacy | Current policy; ad reports [NS03, NS13] | Publish a verifiable no-ad/no-tracker architecture and clear data handling |
| Crash and widget resilience | Search crashes; owner fixes; resolved wallpaper incident [NS14, NS12, NS15] | Treat launcher availability as a release gate, including failure recovery |
| Migration | Version/channel restoration problems [NS18, NS19] | Validate before applying; explain incompatibility; preserve old workspace |
| Modern Android profiles | Requests as recent as September 2026 [NS16, NS17] | Native Private Space is a meaningful differentiator on supported devices |
| Missing controls | FAQ exclusions [NS02] | Manual sorting and mixed badge styles are practical candidates |
| Configuration continuity | Replacement and missing-feature discussions [NS21, NS22] | Preserve workflows rather than forcing a minimalist redesign |
| Feedback visibility | No public Nova feature tracker [NS05] | A public backlog and reasoned decisions reduce uncertainty |

See [the problem register](03-problems.md) for claim status and [requirements](../../PRODUCT_REQUIREMENTS.md) for proposed acceptance criteria.

## Requests Nova documents as unsupported

The FAQ excludes manual drawer sorting, mixed badge types, per-page wallpaper, separate drawer wallpaper, and drawer/folder blur [NS02]. Treat these as documented limitations, not evidence that the team intentionally ignores all users.

We found direct recent requests for Private Space and detailed discussion of controls lost during migration [NS16, NS17, NS22]. For attractive ideas such as undo, setup previews, automatic local snapshots, and presets, we are proposing improvements; this research does not establish their population-wide demand.

## What can and cannot be fixed

App-owned problems include persistence, drag/drop hit testing, search indexing, deterministic sorting, permission recovery, layout migration, and widget-host lifecycle handling.

Android-owned restrictions include stock overview integration, some OEM gesture-navigation behaviour, waking a sleeping display, and unavailable private profiles. A launcher can invoke supported system actions and improve its own transitions; it cannot promise to replace privileged system components [NS35, NS41].

Private Space support is feasible through documented Android integration on supported systems [NS37, NS38]. Cosmetic hiding is a different feature. Work profiles, Android private profiles, and Samsung Secure Folder must not be conflated.

Notification badges normally describe active notifications, not every app's total unread database [NS09, NS39]. Widget providers and OEM power management affect refresh; hosting correctly does not grant control over another app's background work [NS36].

Blur is conditional on platform/hardware support; wallpaper access is restricted on newer Android [NS40, NS42]. Solid/translucent fallback surfaces should be designed first.

## Alternatives and reuse candidates

These are foundation judgments, not independent hands-on ratings.

| Project | Verified evidence at research date | Fit and risk |
| --- | --- | --- |
| Lawnchair | Android 16 development branch; Apache-2.0 root licence; 6 October nightly marked prerelease [NS25–NS27] | Closest first evaluation candidate for a configurable Launcher3-derived base; development builds are not proof of production reliability |
| AOSP Launcher3 | Upstream source and system build definition [NS35, NS41] | Strong core provenance; standalone packaging and removal of privileged assumptions require engineering |
| Neo Launcher | README says Android 16 work is ongoing; GPLv3; GitHub's latest listed release is an older re-release [NS28–NS30] | Relevant customisation reference; check Codeberg and current build readiness before judging cadence |
| Kvaesitso | Search-oriented project; GPLv3-family licence; 15 September v1.41.0 release [NS31, NS32] | Excellent research reference for search/profile UX; its central interaction model differs from Nova |
| Fossify Launcher | GPLv3-family root licence; offline/privacy positioning [NS33, NS34] | Simpler offline reference; feature parity and current release readiness need further evaluation |

Do not assume a project's root licence covers every dependency, bundled asset, or copied file. Record provenance per component before reuse. [The foundation ADR](../../decisions/0002-build-vs-fork.md) sets a build-and-device evaluation gate.

## Recommended strategy

Compare a maintained Launcher3-derived engine against an original implementation before a major workspace/widget investment. Our reuse-first preference is an engineering recommendation, not an accepted architecture change: the main ADR currently proposes an original Kotlin feasibility spike and a fallback to reuse. Preserve that provisional decision until comparable build/device evidence supports changing it.

Make reliability, offline operation, recoverable backups, and a usable editing experience the core. Add Private Space, drawer organisation, icon gestures, and Nova migration in independently verified stages. Keep feed integrations and decorative parity out of the critical path.

Do not promise all feature parity at v1. A useful release needs a clear supported feature subset, compatibility matrix, and restoration guarantees that have actually been tested.

Android 17/API 37 is also in the current compatibility scope; do not stop testing at Android 16 [NS48].

## Research gaps to close during development

1. Inspect current stable and beta Nova on a device; record versions, settings, and entitlement boundaries.
2. Collect consented, sanitised Nova backups across supported versions. No backup fixture was supplied here.
3. Reproduce profile/gesture behaviour on Pixel and Samsung; check an OEM with restrictive launcher integration.
4. Validate the proposed minSdk against the chosen engine and user audience.
5. Interview 8–12 prospective users across minimal, widget-heavy, folder-heavy, work-profile, and accessibility setups. Ask them to show workflows and rank trade-offs.
6. Run a small daily-driver beta before calling the app a replacement.

These are implementation research tasks, not completed validation.

