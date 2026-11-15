package io.github.aj4abinjacob.chikku.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.*
import io.github.aj4abinjacob.chikku.settings.previews.HorizontalPagingPreviewView
import org.junit.Test
import org.junit.runner.RunWith

/** Native rendering/input with unattached synthetic views. No HOME activity, inventory or persisted state. */
@RunWith(AndroidJUnit4::class)
class HorizontalPagingDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Chikku_Home)
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun layout(view: View, height: Int = 300) {
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 400, height)
    }

    @Test fun wrappedPagePixelsAndIdentitySurviveNativeSettlingInLtrAndRtl() {
        for (rtl in listOf(false, true)) {
            lateinit var pager: WorkspacePager
            main {
                pager = WorkspacePager(context).apply {
                    wallpaperUpdatesEnabled = false
                    layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                    listOf(Color.RED, Color.GREEN, Color.BLUE).forEach { color -> addView(View(context).apply { setBackgroundColor(color) }) }
                    layout(this)
                    infiniteScroll = true
                }
                pager.scrollTo(if (rtl) 1000 else -200, 0)
                val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
                pager.draw(Canvas(bitmap))
                assertThat(bitmap.getPixel(if (rtl) 300 else 100, 150)).isEqualTo(Color.BLUE)
                bitmap.recycle()
                pager.snapToPage(2)
            }
            val deadline = SystemClock.uptimeMillis() + 1500
            var normalized = false
            while (!normalized && SystemClock.uptimeMillis() < deadline) {
                main { pager.computeScroll(); normalized = pager.scrollX == if (rtl) 0 else 800 }
                if (!normalized) SystemClock.sleep(16)
            }
            main {
                assertThat(normalized).isTrue()
                assertThat(pager.childCount).isEqualTo(3)
                assertThat((0 until 3).map { pager.getChildAt(it).translationX }).containsExactly(0f, 0f, 0f)
                assertThat(pager.currentPage).isEqualTo(2)
            }
        }
    }

    @Test fun nativeIndicatorAcceptsRtlScrubAndAccessiblePageSelection() = main {
        val indicator = PageIndicatorView(context).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL; setPages(0, 3) }
        layout(indicator, 48)
        var selected = 0
        indicator.onPageSelected = { selected = it; indicator.setPages(it, 3) }
        val start = SystemClock.uptimeMillis()
        fun event(action: Int, x: Float, offset: Long) {
            MotionEvent.obtain(start, start + offset, action, x, 24f, 0).also { indicator.dispatchTouchEvent(it); it.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, 400f, 0)
        event(MotionEvent.ACTION_MOVE, 0f, 100)
        event(MotionEvent.ACTION_UP, 0f, 200)
        assertThat(selected).isEqualTo(2)
        val arguments = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 2f) }
        assertThat(indicator.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, arguments)).isTrue()
        assertThat(selected).isEqualTo(1)
    }

    @Test fun nativePreviewReflectsDraftAndReducedMotionWithoutRealAppsOrWallpaper() = main {
        val preview = HorizontalPagingPreviewView(context)
        layout(preview, 400)
        val settings = LauncherSettings(home = HomeSettings(transitionEffect = PageTransitionEffect.CUBE, pageEffectStrength = .25f))
        preview.apply(settings, false, Color.BLACK, Color.WHITE)
        preview.pager.scrollTo(200, 0)
        assertThat(preview.pager.getChildAt(0).rotationY).isWithin(.01f).of(7.5f)
        preview.apply(settings.copy(home = settings.home.copy(pageEffectStrength = .5f)), false, Color.BLACK, Color.WHITE)
        assertThat(preview.pager.getChildAt(0).rotationY).isWithin(.01f).of(15f)
        preview.apply(settings.copy(lookFeel = LookFeelSettings(animationSpeed = AnimationSpeed.NONE)), false, Color.BLACK, Color.WHITE)
        assertThat(preview.pager.getChildAt(0).rotationY).isEqualTo(0f)
        assertThat(preview.pager.wallpaperUpdatesEnabled).isFalse()
        assertThat(preview.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
    }
}
