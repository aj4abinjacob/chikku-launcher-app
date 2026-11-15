# ADR-0002: Build clean-room Kotlin launcher vs fork Launcher3/Lawnchair

- **Status:** provisional decision, validate before M0 freeze
- **Date:** 2026-10-10

## Context

The user wants a maintained, open-source, Kotlin-based alternative to Nova. Nova itself is proprietary; its code cannot be cloned. AOSP Launcher3 and Lawnchair are possible reference/fork bases [S26][S27][S28].

## Options

| Option | Benefits | Costs/risks |
|---|---|---|
| A. Kotlin-first original implementation | Clean ownership, narrow architecture, license flexibility, modern APIs | Highest risk for widget hosting, grid, drag, migration and OEM behavior |
| B. Fork AOSP Launcher3 | Mature launcher foundations, known widget/grid behaviors | Large Java/platform-oriented code, rebasing Android releases, license/attribution review |
| C. Fork Lawnchair 16 | Modern open-source features already built, community reference | Per-file/dependency licence audit, upstream complexity, inherited design/release choices |

## Provisional decision

**Start with option A as a two-week technical feasibility spike**, not as an unconditional multi-year commitment. Use public Android docs and open-source architectures as references, but do not copy proprietary Nova code. The spike must prove HOME selection, LauncherApps, workspace persistence, drag/drop, widget add/config/resize and acceptable latency.

If the spike shows unacceptable risk, **pivot to option B or C before substantial feature code is written**. Kotlin is the language for new feature/domain modules even if a fork contains existing Java. The objective is a maintained launcher, not Kotlin purity at the expense of reliability.

## Legal consequences

Inspect actual upstream license headers and NOTICE files before reuse. Current Lawnchair's root notice is Apache-2.0 [S28]; inspect the exact candidate's individual files, dependencies, assets and notices before deciding the derivative's licence. Do not infer its current licence from a historical GPL fork. See [LICENSING_AND_LEGAL.md](../LICENSING_AND_LEGAL.md).

## Exit criteria

- Working widget host with configuration and resize on two OS versions.
- App enumeration/profile identity and HOME role verified.
- Drag/drop collision engine and process-death recovery tested.
- Recorded build size, maintenance effort, license implications and OEM gaps.
- Written final ADR choosing architecture for M0.

## Spike evidence so far (2026-10-10)

The clean-room (option A) foundation reached a working HOME prototype in one day of agent-assisted
work. Evidence, from the [verification log](../IMPLEMENTATION_STATUS.md#deviceemulator-verification-log):
- Proven on a physical device (Redmi 9, Android 11/MIUI):
  - HOME candidacy, LauncherApps enumeration and launch.
  - Persisted drag and drop that survives process death.
  - Offline search.
  - Recovery handling. 19 instrumentation tests and 105 unit tests pass.
- **Not yet proven:**
  - Widget hosting (the highest-risk exit criterion).
  - Selecting and reverting the default HOME on a device.
  - Drag latency at 6x8/100 icons.
  - Work profile / Private Space.
  - A second OS version.

**The decision stays provisional.** Option A is not accepted until the widget-host spike
(add/configure/resize on two OS versions) is done. A pivot to B/C remains possible before
substantial feature code.

## Review date

At end of M0 technical spike, before repository gains a large application codebase.


## Recovery evidence continuation (2026-10-12)

The [accepted recovery run](../IMPLEMENTATION_STATUS.md#reliability-recovery-continuation--2026-10-12)
adds native older-schema fixtures and four recoveries after actual process kills on Redmi 9/Android
11. It also exercises a synthetic widget host/provider on that phone, extending the earlier S24
checks. These fixtures do not prove three configured third-party widgets or two-OS configuration/
resize acceptance, real production APK upgrade/reboot or drag/frame/battery budgets. The historical
2026-10-10 gaps above describe that checkpoint; the decision still remains provisional.
