# Appearance settings clarity audit — 2026-10-11

Scope: follow-up to the reported search/dock gap. Traced home grid, dock, drawer, folder, widget, search and popup appearance controls from settings to their rendering code. This records local behavior and fixes; it is not a whole-app accessibility or OEM acceptance claim.

| Area | Finding | Change |
|---|---|---|
| Home grid | Horizontal/vertical padding means an inset at the desktop grid edges, not app-to-app gaps. Small/Medium/Large hid the actual amount. | Grid side space, Grid top and bottom space and explanatory text; choices display 0/4/8/16 dp from the same layout metrics. |
| Widgets | Widget padding insets content within its existing placement. | Widget inner space, with the same concrete amounts and an explanation of the cell boundary. |
| Dock apps | Auto follows only the home grid’s horizontal inset. The × summary did not identify its axes. | Space around dock apps; separate side/above-and-below labels; Follow home grid shows the effective dp amount. |
| Dock background | Inset background changes the painted shape but does not move icons. | Shrink dock background and an explicit 8 dp explanation. |
| Dock/search | Inner dock padding and the outside search gap are independent. | Expose the existing Space above search bar preference in Dock too; both screens update the same stored field. |
| Folder icon/window | One color preference under Inside folder also colored the home folder preview. | Independent Folder icon background color and Folder window background color; window opacity is named explicitly. |
| Search | Padding can still be mistaken for an outside gap. | Label horizontal/vertical padding as inside the bar; keep the search-bar words so older settings-search queries still rank correctly. |
| Drawer rows | Manual viewport-sized slots could retain compact outgoing holders while row updates animated. | Apply manual row changes without item animations; automatic spacing restores the original list animator. Opening/closing drawer transitions remain independent. |
| Numeric units | Radius controls inherited a percentage formatter; dimensions and normalized scales need different units. | All float sliders explicitly choose a formatter. Widget, folder, popup and search radii show fractional dp; opacity and icon/label scales retain percentages. Widget-card height uses configuration-aware resources and consistent whole-dp rounding. |
| Badge size | A saved nonpreset size could be described as Medium. | Include the saved custom size and show percentages alongside preset names. |
| Saved large grids | Disabling Extended grid sizes could show 8 while home, drawer or dock still used a larger saved size. | Each slider retains its own saved size in the available range, bounded by storage limits, and explains why it exceeds the usual range. No layout is reset. |
| Drawer automatic layout | Disabled manual count sliders displayed numbers unrelated to the active automatic layout. | Hide manual columns in automatic/list layouts and manual visible rows during automatic spacing. Describe automatic columns; reveal saved counts when manual layout is enabled. |
| Labs | Extended grid sizes was described as inactive, while the unfinished faster-cache switch accepted changes. | Describe the active grid-range behavior and disable the unavailable cache control, preserving existing saved preferences. |
| Search compact results | The summary always promised five apps, even when an imported setting used a different limit. | Display the actual saved limit when compact results are enabled. |

Folder color decode preserves the old shared color in both fields when the new icon-color key is missing. An explicitly stored null resets only the icon to its default. The new field travels through the existing settings and backup codecs; no Room migration, permission or dependency is added. Normal preview color controls do not recolor an app icon cover.

The drawer separates automatic row spacing from a manual visible-row target; rows are always used for horizontal pages and the viewport constrains manual scrolling-row size. Desktop-size matching disables or hides independent icon-size sliders as appropriate. Widget, folder and popup corner controls use dp, while search explains the fully-rounded saturation point. The numeric follow-up preserves stored geometry, dimensions and imported custom values; unavailable or inactive controls no longer imply an effect they do not have.

Verification results are recorded in [implementation status](IMPLEMENTATION_STATUS.md). Existing page placements, widget IDs and numeric spacing choices are preserved.

Final host verification: debug assembly, lint, 140 core tests, 55 focused appearance tests and privacy gate pass. Full app suite: 590/594 pass; four failures remain in separate media/embedded-widget card work. No device was connected after interruption, so this pass has no completed device visual review or fresh phone installation. See the implementation-status entry for exact scope.
