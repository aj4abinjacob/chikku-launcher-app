# Licensing, trademarks and clean-room development

**Status:** Chikku Launcher project-owned code is licensed under **GNU GPL v3.0 or later (`GPL-3.0-or-later`)**. The full terms are in the root `LICENSE` file. The repository is still **private**; distribution still requires the remaining rights/source/service checks. The [release review](release/PLAY_PREPARATION.md) records the exact runtime and asset audit, bundled notices and remaining ownership questions. Public GitHub release follows Play and separate owner authorization.

## Recommended decision process

1. Choose architecture: original Kotlin implementation or derivative of AOSP Launcher3/Lawnchair.
2. Audit every upstream file/dependency and its license.
3. Decide distribution goals: F-Droid, GitHub Releases, Google Play.
4. Preserve the selected project licence and third-party notices; provide recipient source access for any binary distribution. Public repository visibility is a separate owner decision after Play release.
5. Consult qualified counsel for uncertain license compatibility/trademark issues; this document is not legal advice.

## License options

| Option | Suitable when | Tradeoff |
|---|---|---|
| Apache-2.0 | Fully original Kotlin code, permissive contribution/reuse desired | Downstream forks may be proprietary; preserve notices |
| GPL-3.0-or-later | Strong copyleft desired or GPL-derived code requires compatible terms | Whole derivative distribution has copyleft obligations |
| Other compatible licenses | Specific upstream requirements | Verify compatibility before copying code |

AOSP Launcher3 has its own per-file licensing and attribution requirements; inspect the actual source tree. The current Lawnchair 16-dev root LICENSE.txt contains an Apache-2.0 notice [S28]. A historical GPL notice from another revision/fork does not establish the current candidate's whole-project licence. **Audit individual files, dependencies, assets and notices before reuse; the root notice alone is insufficient.** Architectural ideas and public Android APIs are not the same as copying implementation code.

## Nova intellectual property

- Nova Launcher is proprietary; do not copy/decompile/distribute its source or paid assets.
- Do not use the Nova brand, logo, app ID, screenshots, icons or marketing assets in Chikku releases without rights.
- Comparing behavior and documenting publicly visible settings is acceptable research; do not claim affiliation.
- Do not import Nova backups until format, legal rights and privacy have been reviewed.
- Third-party icon packs and widgets remain their authors' intellectual property.

## Before publishing

- [x] LICENSE file selected and added (`GPL-3.0-or-later`)
- [x] Exact release runtime/POM/source licence inventory reviewed (scope/exceptions in release/RUNTIME_DEPENDENCIES.md)
- [x] Inventoried Apache asset/library/native notices bundled and accessible; unresolved ownership remains a separate gate
- [ ] Contributor license/certificate-of-origin policy decided
- [ ] Brand/name/package uniqueness checked
- [ ] Play Store/F-Droid policy compatibility reviewed
- [ ] Security contact and privacy policy published
- [ ] Build from a clean clone succeeds

**Current project choice:** `GPL-3.0-or-later` for project-owned source. This does not relicense third-party libraries, copied code or assets. If upstream files are reused, pin their exact revisions and verify their licenses, compatibility and attribution obligations before publication.



## Weather data revision — 16 October 2026

MET forecasts and the bundled GeoNames city index use CC BY 4.0 data with attribution and
modification notices. Full terms are shipped offline; original GeoNames inputs/hashes and the
index generator accompany the matching source. No provider logo/icon artwork was copied.
Data reuse rights remain separate from MET's service identification/cache/aggregate traffic
conditions. See [the reviewed migration](release/MET_NORWAY_SWITCH.md). The original Open-Meteo
free service is absent from this branch's production app sources. Public-policy revision,
source recipient access, existing branding/reference and contributor rights remain open.
