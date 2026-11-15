package io.github.aj4abinjacob.chikku.home

import android.graphics.Typeface
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont

/**
 * The one mapping from a [DockLabelFont] to the system font that draws it. Home's View labels and
 * the settings font chooser both read it, so the chooser shows each option in the real label font.
 *
 * [familyName] is the Android system font family; [weight] is the weight that family draws at
 * (CSS scale), which Compose needs to resolve the same face.
 */
data class LabelFontSpec(val familyName: String, val weight: Int)

fun DockLabelFont.labelFontSpec(): LabelFontSpec = when (this) {
    DockLabelFont.NORMAL -> LabelFontSpec("sans-serif", 400)
    DockLabelFont.MEDIUM -> LabelFontSpec("sans-serif-medium", 500)
    DockLabelFont.CONDENSED -> LabelFontSpec("sans-serif-condensed", 400)
    DockLabelFont.LIGHT -> LabelFontSpec("sans-serif-light", 300)
}

/** The typeface home draws icon labels with. */
fun DockLabelFont.labelTypeface(): Typeface = Typeface.create(labelFontSpec().familyName, Typeface.NORMAL)
