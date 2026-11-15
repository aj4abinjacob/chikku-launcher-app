package io.github.aj4abinjacob.chikku.platform.icons

import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings

/** Global icon look: pack and shape. Part of every icon cache key. */
data class IconStyle(
    val iconPack: String? = null,
    val shape: IconShape = IconShape.SYSTEM,
    val reshapeLegacy: Boolean = false,
) {
    /** True when icons are exactly what the platform reports (badged system icons). */
    val isSystemDefault: Boolean get() = iconPack == null && shape == IconShape.SYSTEM && !reshapeLegacy

    companion object {
        val DEFAULT = IconStyle()

        fun from(lookFeel: LookFeelSettings) = IconStyle(lookFeel.iconPackPackage, lookFeel.iconShape, lookFeel.reshapeLegacyIcons)
    }
}

/** A per-app icon chosen in the picker: drawable [drawableName] from pack [iconPack]. */
data class IconOverride(val iconPack: String, val drawableName: String)

/** Everything that decides how an icon looks: global [style] plus per-app [overrides]. */
data class IconTheme(
    val style: IconStyle = IconStyle.DEFAULT,
    val overrides: Map<AppKey, IconOverride> = emptyMap(),
) {
    /** Packages whose change or removal invalidates themed icons. */
    val packPackages: Set<String> get() = buildSet {
        style.iconPack?.let(::add)
        overrides.values.forEach { add(it.iconPack) }
    }

    companion object {
        val DEFAULT = IconTheme()

        fun overridesOf(customizations: Map<AppKey, AppCustomization>): Map<AppKey, IconOverride> =
            customizations.mapNotNull { (key, c) ->
                val pack = c.iconPackPackage
                val name = c.iconDrawableName
                if (pack.isNullOrBlank() || name.isNullOrBlank()) null else key to IconOverride(pack, name)
            }.toMap()
    }
}

/** One icon cache entry: the app plus every input that changes its look. */
data class IconRequest(val key: AppKey, val style: IconStyle, val override: IconOverride?,
    val calendarDay: Int = java.time.LocalDate.now().dayOfMonth)
