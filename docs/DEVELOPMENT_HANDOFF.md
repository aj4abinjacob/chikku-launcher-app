# Development handoff

The planning and research are ready for implementation. **No Android app code, APK, build, handset run or benchmark is provided by this documentation task.**

## Reading order

1. [Main README](../README.md), [requirements](PRODUCT_REQUIREMENTS.md), [architecture](ARCHITECTURE.md), [data model](DATA_MODEL.md).
2. [Build-vs-fork decision](decisions/0002-build-vs-fork.md), [roadmap](ROADMAP.md), [setup](DEVELOPMENT_SETUP.md).
3. [Dated research supplement](research/nova-2026-10-10/README.md), especially its licensing review and additional verification cases.
4. [Tests](TEST_STRATEGY.md), [privacy](PRIVACY_SECURITY.md), [maintenance](MAINTENANCE_POLICY.md).

## Initial implementation brief

> Implement the feasibility spike described by this repository's provisional build-vs-fork ADR.
>
> Recheck current upstream/platform facts and inspect licences. Produce a normal non-root selectable HOME app. Demonstrate activity/profile-aware app launching, deterministic workspace persistence, drag cancellation, one real configurable/resizable widget, local app search and recovery after process recreation.
>
> Compare original-engine risk with an appropriately pinned licensed Launcher3/Lawnchair candidate before substantial feature development. Do not interpret the supplement's reuse preference as an already accepted decision.
>
> Run the build and available emulator/device tests. Record actual commands, commit, versions, device/OS, measured behaviour and limitations. If physical-device coverage is unavailable, leave that gate open explicitly.
>
> Update the ADR with evidence and commit verified work. Do not claim full Nova parity, tested Nova backup import, privileged Recents control or universal OEM gesture compatibility.

## Resolve before a large feature build

Engine choice and exact revision; compatible source licence and notices; package ID; supported Android floor; widget/persistence ownership; restore journal/recovery design; current Android 17/API 37 coverage; signing/distribution and real migration fixtures.

Core should remain usable with all optional sensitive access denied. No fixture in this task proves universal Nova migration.

## Source and scope discipline

The main backlog is the planning baseline. The supplement adds 149 evidence-labelled feature entries, 22 problem signals, 10 explicitly proposed improvements, 48 sources and 20 further acceptance cases. Its identifier namespace is separate.

No repository publication, signing-key setup, store listing, user recruitment, automated messaging or app distribution was performed. Future publishing should follow the owner's authorization and verified release gates.

