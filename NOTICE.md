# Chikku Launcher notices

Project-owned source is **GPL-3.0-or-later**; see [LICENSE](LICENSE). The app displays the
full terms and third-party notices offline in **Settings → About**. This project is independent
of Google, Nova Launcher, Lawnchair and third-party widget/icon-pack providers.

- [Material icon inventory](docs/release/material-icons.json): 124 vector XMLs with upstream
  attribution. Apache-2.0; adapted for Android vectors/tinting. Full [Apache licence](LICENSES/Apache-2.0.txt).
  Original copy revisions were not recorded; the inventory identifies the separate licence-check revision.
- [Exact release dependency audit](docs/release/RUNTIME_DEPENDENCIES.md) and
  [artifact/POM/source hashes and embedded notices](docs/release/runtime-dependencies.json).
  Transitive dependencies retain their own copyright and licence statements.
- Gradle wrapper scripts/JAR: Gradle, Apache-2.0. The JAR retains `META-INF/LICENSE`.
  Toolchain/developer tools are distinct from the shipped Android runtime dependencies.
- Optional live forecasts: MET Norway; bundled offline city data: GeoNames, CC BY 4.0.
  Attribution and modification details appear in the weather UI and offline legal notices.
  Raw GeoNames inputs, hashes and regeneration recipe are in [data/weather](data/weather/geonames-2026-10-15/README.txt);
  full [CC BY 4.0](app/src/main/assets/legal/cc-by-4.0.txt) is bundled offline.
  Data rights do not grant unrestricted use of the hosted API service.
- Branding provenance and unresolved rights are recorded in
  [the release review](docs/release/PLAY_PREPARATION.md). Historical concepts are not shipped app assets.

No contributor's existing notice is removed or replaced by this file. New repository history
starts from a complete reviewed source snapshot; it does not establish a new origin date for
previous code or remove historical activity from the development repository or elsewhere.

Every external binary distribution, including testing, must have its exact matching source
available to recipients under the applicable licence. See [source delivery](docs/release/SOURCE_DELIVERY.md).
