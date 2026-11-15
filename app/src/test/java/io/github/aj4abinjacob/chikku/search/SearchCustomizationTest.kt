package io.github.aj4abinjacob.chikku.search

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.ImageView
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.search.SearchRow
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.home.app
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class SearchCustomizationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun privateKeyboardAddsStandardFlagAfterDelegateWithoutReplacingGoOrConnection() {
        val connection = BaseInputConnection(View(context), false)
        val delegate = object : PlatformTextInputMethodRequest {
            override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                outAttributes.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_FULLSCREEN
                return connection
            }
        }
        for (private in listOf(false, true)) {
            val info = EditorInfo()
            val actual = privateKeyboardRequest(delegate, private).createInputConnection(info)
            assertThat(actual).isSameInstanceAs(connection)
            assertThat(info.imeOptions and EditorInfo.IME_MASK_ACTION).isEqualTo(EditorInfo.IME_ACTION_GO)
            assertThat(info.imeOptions and EditorInfo.IME_FLAG_NO_FULLSCREEN).isNotEqualTo(0)
            assertThat(info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0).isEqualTo(private)
        }
    }

    @Test
    fun compactAppResultsKeepBestOrderAndDoNotDiscardExplicitNonAppActions() {
        val apps = (0 until 8).map { SearchRow.App(app("App$it")) }
        val web = SearchRow.WebSearch(WebSearchEngine.GOOGLE, "apps")
        val rows = apps.take(2) + web + apps.drop(2) + SearchRow.ContactsPermissionNeeded
        val limited = limitSearchAppRows(rows, 5)
        assertThat(limited.filterIsInstance<SearchRow.App>()).containsExactlyElementsIn(apps.take(5)).inOrder()
        assertThat(limited).contains(web)
        assertThat(limited).contains(SearchRow.ContactsPermissionNeeded)
        assertThat(limitSearchAppRows(rows, 0)).isSameInstanceAs(rows)
    }

    @Test
    fun changingBarAppearancePreservesProviderAndUsesReadableIconTint() {
        val bar = SearchWidgetView(context)
        bar.applyAppearance(SearchSettings(webEngine = WebSearchEngine.GOOGLE, widgetColor = Color.WHITE, widgetCornerRadiusDp = 0f, widgetShadow = true))
        var shape = (bar.background as RippleDrawable).getDrawable(0) as GradientDrawable
        assertThat(shape.color?.defaultColor).isEqualTo(Color.WHITE)
        assertThat(shape.cornerRadius).isEqualTo(0f)
        assertThat((bar.chikkuButton.getChildAt(0) as ImageView).imageTintList?.defaultColor).isEqualTo(0xFF1B1B1F.toInt())
        assertThat(bar.elevation).isGreaterThan(0f)
        assertThat(bar.engine).isEqualTo(WebSearchEngine.GOOGLE)
        bar.applyAppearance(SearchSettings(webEngine = WebSearchEngine.GOOGLE))
        shape = (bar.background as RippleDrawable).getDrawable(0) as GradientDrawable
        assertThat(shape.color?.defaultColor).isEqualTo(0xFF22304F.toInt())
        assertThat(shape.cornerRadius).isEqualTo(32f * context.resources.displayMetrics.density)
        assertThat((bar.chikkuButton.getChildAt(0) as ImageView).imageTintList?.defaultColor).isEqualTo(Color.WHITE)
        assertThat(bar.elevation).isEqualTo(0f)
        assertThat(bar.engine).isEqualTo(WebSearchEngine.GOOGLE)
    }

    @Test
    fun malformedBarRadiusFallsBackAndExtremeRadiusIsBounded() {
        val bar = SearchWidgetView(context)
        bar.applyAppearance(SearchSettings(widgetCornerRadiusDp = Float.NaN))
        val shape = (bar.background as RippleDrawable).getDrawable(0) as GradientDrawable
        assertThat(shape.cornerRadius).isEqualTo(32f * context.resources.displayMetrics.density)
        bar.applyAppearance(SearchSettings(widgetCornerRadiusDp = 1000f))
        val bounded = (bar.background as RippleDrawable).getDrawable(0) as GradientDrawable
        assertThat(bounded.cornerRadius).isEqualTo(48f * context.resources.displayMetrics.density)
    }
}
