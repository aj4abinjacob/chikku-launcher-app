package io.github.aj4abinjacob.chikku.badges

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.BadgePosition
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Synthetic overlays only: verifies rendering and rebind without accessing notifications. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BadgeCustomizationTest {
    private fun renderedBounds(style: BadgeStyle, position: BadgePosition, rtl: Boolean, scale: Float = 1f, avatar: Bitmap? = null): Rect? {
        val bitmap = Bitmap.createBitmap(180, 180, Bitmap.Config.ARGB_8888)
        BadgeOverlay(style, 3, Color.RED, avatar, position, scale).draw(Canvas(bitmap), 40, 40, 80, rtl)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
            left = minOf(left, x); right = maxOf(right, x)
            top = minOf(top, y); bottom = maxOf(bottom, y)
        }
        return if (right < 0) null else Rect(left, top, right + 1, bottom + 1)
    }

    @Test fun everyCornerMirrorsInRtlForDotsCountsAndDynamicAvatars() {
        val avatar = Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        for (style in listOf(BadgeStyle.DOT, BadgeStyle.COUNT, BadgeStyle.DYNAMIC)) {
            for (position in BadgePosition.entries) for (rtl in listOf(false, true)) {
                val bounds = renderedBounds(style, position, rtl, avatar = if (style == BadgeStyle.DYNAMIC) avatar else null)!!
                val end = position == BadgePosition.TOP_END || position == BadgePosition.BOTTOM_END
                val bottom = position == BadgePosition.BOTTOM_END || position == BadgePosition.BOTTOM_START
                val left = if (end) rtl else !rtl
                if (left) assertThat(bounds.centerX()).isLessThan(80) else assertThat(bounds.centerX()).isGreaterThan(80)
                if (bottom) assertThat(bounds.centerY()).isGreaterThan(80) else assertThat(bounds.centerY()).isLessThan(80)
            }
        }
    }

    @Test fun sizePresetsIncreaseRenderedAreaAndInvalidScaleFallsBack() {
        for (style in listOf(BadgeStyle.DOT, BadgeStyle.COUNT, BadgeStyle.DYNAMIC)) {
            val small = renderedBounds(style, BadgePosition.TOP_END, false, 0.75f)!!
            val medium = renderedBounds(style, BadgePosition.TOP_END, false)!!
            val large = renderedBounds(style, BadgePosition.TOP_END, false, 1.25f)!!
            assertThat(small.width()).isLessThan(medium.width())
            assertThat(medium.width()).isLessThan(large.width())
            assertThat(renderedBounds(style, BadgePosition.TOP_END, false, Float.NaN)).isEqualTo(medium)
        }
        assertThat(renderedBounds(BadgeStyle.OFF, BadgePosition.BOTTOM_END, false)).isNull()
    }

    @Test fun changingPositionAndSizeWithoutNewNotificationsRebindsAppsAndFolders() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val binder = BadgeBinder(context)
        val key = AppKey("example", "example.Main", 0)
        val app = AppIconView(context, AppIconView.Style.WORKSPACE).apply { contentDescription = "Sample" }
        val folder = AppIconView(context, AppIconView.Style.WORKSPACE).apply { contentDescription = "Folder Sample" }
        binder.bindApp(app, key)
        binder.bindFolder(folder, listOf(key))
        val data = mapOf(BadgeKey.of(key) to BadgeInfo(3))
        binder.update(data, LauncherSettings(badges = BadgeSettings(style = BadgeStyle.COUNT)))
        val before = app.badge
        binder.update(data, LauncherSettings(badges = BadgeSettings(style = BadgeStyle.COUNT, position = BadgePosition.BOTTOM_START, badgeScale = 1.25f)))
        assertThat(app.badge).isNotSameInstanceAs(before)
        assertThat(app.badge!!.position).isEqualTo(BadgePosition.BOTTOM_START)
        assertThat(app.badge!!.badgeScale).isEqualTo(1.25f)
        assertThat(app.contentDescription.toString()).isEqualTo("Sample, 3 notifications")
        assertThat(folder.badge!!.style).isEqualTo(BadgeStyle.DOT)
        assertThat(folder.badge!!.position).isEqualTo(BadgePosition.BOTTOM_START)
        assertThat(folder.badge!!.badgeScale).isEqualTo(1.25f)
    }

    @Test fun appearanceChangesDoNotCrossProfilesOrExposeOffBadges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val binder = BadgeBinder(context)
        val key = AppKey("example", "example.Main", 0)
        val view = AppIconView(context, AppIconView.Style.WORKSPACE).apply { contentDescription = "Sample" }
        binder.bindApp(view, key)
        binder.update(mapOf(BadgeKey("example", 10) to BadgeInfo(3)), LauncherSettings(badges = BadgeSettings(style = BadgeStyle.COUNT, position = BadgePosition.BOTTOM_END)))
        assertThat(view.badge).isNull()
        assertThat(view.contentDescription.toString()).isEqualTo("Sample")
        binder.update(mapOf(BadgeKey.of(key) to BadgeInfo(3)), LauncherSettings(badges = BadgeSettings(style = BadgeStyle.OFF, position = BadgePosition.BOTTOM_END)))
        assertThat(view.badge).isNull()
        assertThat(binder.badgeFor(key)).isNull()
    }
}
