package io.github.aj4abinjacob.chikku.search

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.search.MicroResult
import io.github.aj4abinjacob.chikku.core.model.search.SearchResults
import io.github.aj4abinjacob.chikku.core.model.search.SearchRow
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class MicroHandoffTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun phoneActionsOpenReviewableDialerAndComposerWithoutSending() {
        val dial = SearchHandoff.microIntent(context, MicroResult.Action(MicroResult.Kind.DIAL, "+919876543210"))!!
        val sms = SearchHandoff.microIntent(context, MicroResult.Action(MicroResult.Kind.SMS, "+919876543210"))!!
        assertThat(dial.action).isEqualTo(Intent.ACTION_DIAL)
        assertThat(dial.data.toString()).isEqualTo("tel:%2B919876543210")
        assertThat(sms.action).isEqualTo(Intent.ACTION_SENDTO)
        assertThat(sms.data?.scheme).isEqualTo("smsto")
        assertThat(SearchHandoff.microIntent(context, MicroResult.Action(MicroResult.Kind.DIAL, "123;evil"))).isNull()
    }

    @Test
    fun mapQueryIsFullyEncodedAndUrlOffersAChooser() {
        val map = SearchHandoff.microIntent(context, MicroResult.Action(MicroResult.Kind.MAP, "Main St & cafes#nearby"))!!
        assertThat(map.data.toString()).isEqualTo("geo:0,0?q=Main%20St%20%26%20cafes%23nearby")
        val chooser = SearchHandoff.microIntent(context, MicroResult.Action(MicroResult.Kind.URL, "https://example.com"))!!
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        @Suppress("DEPRECATION")
        val target = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertThat(target.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(target.categories).contains(Intent.CATEGORY_BROWSABLE)
        assertThat(SearchHandoff.microIntent(context, MicroResult.Action(MicroResult.Kind.URL, "intent://evil"))).isNull()
    }

    @Test
    fun goChoosesExplicitMicroActionBeforeWebButNeverAutomaticallyCopiesConversion() {
        val action = SearchRow.Micro(MicroResult.Action(MicroResult.Kind.MAP, "Main St"))
        assertThat(SearchResults("Main St", microResults = listOf(action.result)).goRow()).isEqualTo(action)
        assertThat(SearchResults("32F", microResults = listOf(MicroResult.Conversion("0 °C"))).goRow()).isNull()
    }
}
