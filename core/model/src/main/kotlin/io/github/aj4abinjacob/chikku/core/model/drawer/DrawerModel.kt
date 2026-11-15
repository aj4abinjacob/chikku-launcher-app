package io.github.aj4abinjacob.chikku.core.model.drawer

import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import kotlin.math.roundToInt

/** Which built-in or user tab a [DrawerTab] is. */
enum class DrawerTabKind {
    /** Every visible app. */
    ALL,

    /** Every visible personal-profile app; replaces [ALL] while a separate work tab is shown. */
    PERSONAL,
    WORK,
    GROUP,
}

/**
 * One drawer tab and the apps it shows, in display order. [id] is stable across recompositions
 * ("all", "work" or "group:<group id>") so the selected tab survives list changes; [name] is the
 * user's group name and null for built-in tabs (the UI supplies a localized title).
 */
data class DrawerTab(
    val id: String,
    val kind: DrawerTabKind,
    val name: String?,
    val apps: List<LauncherApp>,
)

/** First drawer position of the apps whose label starts with [letter] (fast scroller entry). */
data class DrawerSection(val letter: String, val position: Int)

/**
 * Pure drawer rules: hidden-app filtering, tabs (groups and the work tab), paging, the fast-scroller
 * index, column count and colors. Identity is always [AppKey] (component + profile), never a package
 * name or label. Hiding is cosmetic only: it is not app security.
 */
object DrawerModel {

    const val TAB_ALL = "all"
    const val TAB_WORK = "work"
    private const val GROUP_PREFIX = "group:"

    /** Label bucket for labels that do not start with a letter. */
    const val OTHER_SECTION = "#"

    fun groupTabId(groupId: String) = GROUP_PREFIX + groupId

    /** Keys the user hid from the drawer and search. */
    fun hiddenKeys(customizations: Map<AppKey, AppCustomization>): Set<AppKey> =
        customizations.values.filter { it.hidden }.mapTo(mutableSetOf()) { it.key }

    fun withoutHidden(apps: List<LauncherApp>, hidden: Set<AppKey>): List<LauncherApp> =
        if (hidden.isEmpty()) apps else apps.filter { it.key !in hidden }

    /** [state] with hidden apps removed; profile listing status is unchanged. */
    fun withoutHidden(state: AppsState, hidden: Set<AppKey>): AppsState = when (state) {
        is AppsState.Loaded -> if (hidden.isEmpty()) state else state.copy(apps = withoutHidden(state.apps, hidden))
        else -> state
    }

    /**
     * Tabs for [apps] (already filtered of hidden apps, in repository display order).
     * Always starts with the "all" tab; a work tab follows when [DrawerSettings.showWorkTab] is on and a
     * work app is visible; user groups follow when [DrawerSettings.groupsEnabled]. The UI shows a tab
     * row only when there is more than one tab.
     */
    fun tabs(apps: List<LauncherApp>, groups: List<DrawerGroup>, settings: DrawerSettings): List<DrawerTab> {
        val tabs = mutableListOf<DrawerTab>()
        val separateWork = settings.showWorkTab && (apps.any { it.profileType == ProfileType.WORK } ||
            settings.tabStyle == io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabStyle.SEGMENTED)
        if (separateWork) {
            tabs += DrawerTab(TAB_ALL, DrawerTabKind.PERSONAL, null, apps.filter { it.profileType != ProfileType.WORK })
            tabs += DrawerTab(TAB_WORK, DrawerTabKind.WORK, null, apps.filter { it.profileType == ProfileType.WORK })
        } else {
            tabs += DrawerTab(TAB_ALL, DrawerTabKind.ALL, null, apps)
        }
        if (settings.groupsEnabled) {
            groups.sortedBy { it.rank }.forEach { group ->
                tabs += DrawerTab(groupTabId(group.id), DrawerTabKind.GROUP, group.name, groupApps(apps, group))
            }
        }
        return tabs
    }

    /**
     * Visible members of [group]. ALPHABETICAL keeps the drawer's own order; MANUAL follows the
     * member order. Members that are hidden, uninstalled or in an unlisted profile are skipped (kept
     * in storage so they come back when the app does).
     */
    fun groupApps(apps: List<LauncherApp>, group: DrawerGroup): List<LauncherApp> = when (group.sortMode) {
        DrawerSortMode.ALPHABETICAL -> {
            val members = group.members.toHashSet()
            apps.filter { it.key in members }
        }
        DrawerSortMode.MANUAL -> {
            val byKey = apps.associateBy { it.key }
            group.members.distinct().mapNotNull { byKey[it] }
        }
    }

    /** Index of [tabId] in [tabs], falling back to the first tab when it no longer exists. */
    fun selectedIndex(tabs: List<DrawerTab>, tabId: String?): Int =
        tabs.indexOfFirst { it.id == tabId }.coerceAtLeast(0)

    /** Splits [apps] into pages of [columns] x [rows] (paged drawer). Never returns zero pages. */
    fun pages(apps: List<LauncherApp>, columns: Int, rows: Int): List<List<LauncherApp>> {
        val perPage = (columns.coerceAtLeast(1) * rows.coerceAtLeast(1))
        return if (apps.isEmpty()) listOf(emptyList()) else apps.chunked(perPage)
    }

    /**
     * Fallback fast-scroller bucket of [label]: its first letter in upper case, or [OTHER_SECTION].
     * The app passes a locale-aware bucketing to [sections] instead.
     */
    fun sectionLetter(label: String): String {
        val first = label.trimStart().firstOrNull() ?: return OTHER_SECTION
        return if (first.isLetter()) first.uppercase() else OTHER_SECTION
    }

    /**
     * Fast-scroller entries in display order: one per bucket, at the first app in that bucket. A
     * bucket seen again later (locale collation can interleave) keeps its first position. [sectionOf]
     * should agree with the order of [apps] so each bucket is one contiguous run.
     */
    fun sections(apps: List<LauncherApp>, sectionOf: (String) -> String = ::sectionLetter): List<DrawerSection> {
        val seen = LinkedHashMap<String, Int>()
        apps.forEachIndexed { index, app -> seen.getOrPut(sectionOf(app.label)) { index } }
        return seen.map { (letter, position) -> DrawerSection(letter, position) }
    }

    /**
     * Letter groups for list rows: one entry at the first app of each group, or empty when [apps]
     * is not grouped by letter (a manual order, or a single group), since a letter shown again
     * further down would mislabel the rows between.
     */
    fun listGroups(apps: List<LauncherApp>, sectionOf: (String) -> String = ::sectionLetter): List<DrawerSection> {
        val groups = ArrayList<DrawerSection>()
        val seen = HashSet<String>()
        apps.forEachIndexed { index, app ->
            val letter = sectionOf(app.label)
            if (groups.lastOrNull()?.letter == letter) return@forEachIndexed
            if (!seen.add(letter)) return emptyList()
            groups += DrawerSection(letter, index)
        }
        return if (groups.size > 1) groups else emptyList()
    }

    /**
     * Drawer columns: the configured count, or for [DrawerSettings.AUTO_COLUMNS] as many cells of
     * [cellWidthPx] as fit [usableWidthPx], within [minAuto]..[maxAuto].
     */
    fun columns(setting: Int, usableWidthPx: Int, cellWidthPx: Int, minAuto: Int = 3, maxAuto: Int = 8): Int =
        if (setting > DrawerSettings.AUTO_COLUMNS) {
            setting
        } else if (usableWidthPx <= 0 || cellWidthPx <= 0) {
            minAuto
        } else {
            (usableWidthPx / cellWidthPx).coerceIn(minAuto, maxAuto)
        }

    /** Whether a swipe up on home may open the drawer under [method]. */
    fun allowsSwipeOpen(method: DrawerOpenMethod): Boolean = method != DrawerOpenMethod.BUTTON

    /**
     * Whether the home "All apps" button is shown under [method]. It stays when swipe-up-only is chosen
     * but the home swipe up no longer opens the drawer ([swipeUpOpensDrawer] false), so the drawer is
     * never left without a touch path.
     */
    fun showsOpenButton(method: DrawerOpenMethod, swipeUpOpensDrawer: Boolean = true): Boolean =
        method != DrawerOpenMethod.SWIPE_UP || !swipeUpOpensDrawer

    /**
     * Drawer background: [custom] (or the theme's [themeColor] when null) with its alpha multiplied by
     * [alpha] (0 = fully transparent, 1 = as chosen).
     */
    fun backgroundColor(themeColor: Int, custom: Int?, alpha: Float): Int {
        val base = custom ?: themeColor
        val baseAlpha = (base ushr 24) and 0xFF
        val a = (baseAlpha * (if (alpha.isNaN()) 1f else alpha.coerceIn(0f, 1f))).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (base and 0x00FFFFFF)
    }

    /**
     * Whether content on [background] should use light (white) text. A mostly transparent background
     * shows the wallpaper, which is assumed dark (labels get a shadow there anyway).
     */
    fun prefersLightContent(background: Int): Boolean {
        val alpha = ((background ushr 24) and 0xFF) / 255f
        val luminance = relativeLuminance(background)
        val effective = alpha * luminance + (1f - alpha) * ASSUMED_WALLPAPER_LUMINANCE
        return effective < LIGHT_CONTENT_THRESHOLD
    }

    private fun relativeLuminance(color: Int): Float {
        fun channel(shift: Int): Float {
            val c = ((color shr shift) and 0xFF) / 255f
            return if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * channel(16) + 0.7152f * channel(8) + 0.0722f * channel(0)
    }

    private const val ASSUMED_WALLPAPER_LUMINANCE = 0.05f
    private const val LIGHT_CONTENT_THRESHOLD = 0.18f
}
