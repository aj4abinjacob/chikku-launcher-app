# Long-term maintenance and governance policy (proposal)

Chikku's differentiator is not merely being open source; it must stay compatible with Android and make maintenance visible. This policy becomes active once the repository is public and releases begin.

## Cadence and ownership

- **Weekly:** review new issues, label severity/platform/OEM, respond to P0 and security reports promptly.
- **Every 2–4 weeks during active development:** beta candidate when quality gates pass.
- **Quarterly:** Android platform/Play policy review, dependency updates, privacy audit, roadmap refresh.
- **Each Android beta/stable:** run HOME/widget/search/profile smoke suite before declaring support.
- **Each release:** signed artifact, source commit/tag, changelog, known limitations, migration notes and rollback guidance.

Do not promise calendar releases if the quality gate fails. Publish the delay and blocker.

## Service targets (aspirational, contingent on maintainers)

| Severity | Acknowledge target | Response |
|---|---|---|
| P0 — HOME unusable/data loss/security | 48 hours | Reproduce, hotfix/rollback guidance, affected builds |
| P1 — core workflow broken | 7 days | Triage and fix or documented workaround |
| P2 — feature/compatibility gap | 14 days | Label, estimate, roadmap decision |
| P3 — polish | Best effort | Community voting and periodic review |

These are **goals**, not guarantees; update them when maintainer capacity is known.

## Release engineering

- Main branch protected after code bootstrap; PR review and CI required.
- Separate nightly, beta and stable channels; do not auto-promote.
- No forced updates, silent schema resets or hidden permissions.
- Maintain backup fixtures and migrations for at least two previous stable schema versions (extend as adoption grows).
- Keep old signed release APKs and SHA-256 checksums accessible for recovery.
- Use semantic-ish versions, e.g. 0.1.0-alpha.1, 0.2.0-beta.1, 1.0.0.

## Community process

- Publish roadmap and rationale for accepted/deferred feature requests.
- Require reproducible reports, but offer a friendly issue template.
- Label platform/OEM restrictions explicitly rather than leaving them as perpetual bugs.
- Maintain a list of good first issues, architecture docs and coding standards.
- Recruit at least two release-capable maintainers; document succession and access recovery.
- Publish financial/sponsorship policy if donations are accepted; never make tracking a condition of use.

## Compatibility dashboard

For each supported Android/OEM combination, record: build, default-HOME selection, app list, widgets, gestures, work/private profiles, backup restore, status (verified / known limitation / untested), test date and issue links.

## When maintenance cannot continue

Publish an honest notice, preserve build instructions and signed artifacts, document known security risks, and offer maintainer transfer under the chosen open-source license. Avoid silent abandonment.
