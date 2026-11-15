package io.github.aj4abinjacob.chikku.settings.cards

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.CardTimeRange
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.cards.validated
import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardTemplatesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun draft(template: CardTemplate, id: String = "template-${template.name}") =
        cardTemplateDraft(template, id, template.defaultTitle?.let(context::getString).orEmpty())

    @Test
    fun everyTemplateIsAValidSavableDraft() {
        for (template in CardTemplate.entries) {
            val card = draft(template)
            // Blank keeps today's behaviour: the user names it before Save is enabled.
            val named = if (template == CardTemplate.BLANK) card.copy(title = "Synthetic name") else card
            assertThat(named.coerced()).isNotNull() // conditions.coerced() and content.validated()
            assertThat(card.content.validated()).isEqualTo(card.content)
            assertThat(card.conditions.coerced()).isEqualTo(card.conditions)
            val saved = saveCardDraft(emptyList(), named)
            assertThat(saved).isNotNull()
            assertThat(saved!!.single().id).isEqualTo(card.id)
        }
    }

    @Test
    fun namedTemplatesHaveALocalisedTitleAndBlankStartsEmpty() {
        assertThat(draft(CardTemplate.BLANK).title).isEmpty()
        assertThat(saveCardDraft(emptyList(), draft(CardTemplate.BLANK))).isNull() // unnamed blank cannot save
        for (template in CardTemplate.entries - CardTemplate.BLANK) {
            val title = draft(template).title
            assertThat(title).isNotEmpty()
            assertThat(title.length).isAtMost(CardsSettings.MAX_TITLE_LENGTH)
            assertThat(title).isEqualTo(context.getString(requireNotNull(template.defaultTitle)))
        }
    }

    @Test
    fun noTemplateEnablesOnlineWeatherOrTargetsRealAppsWidgetsOrShortcuts() {
        for (template in CardTemplate.entries) {
            val card = draft(template)
            assertThat(card.featured).isEqualTo(CardFeatured.None)
            assertThat(card.actions).containsExactlyElementsIn(List(CardsSettings.ACTION_COUNT) { GestureAction.None })
            // Available with no installed apps or Home widgets at all.
            assertThat(cardTargetsAvailable(card, emptySet(), emptySet())).isTrue()
            when (val content = card.content) {
                is CardContent.Weather -> {
                    assertThat(content.onlineEnabled).isFalse()
                    assertThat(content.location).isNull()
                }
                is CardContent.Widget -> assertThat(content.provider).isNull()
                else -> Unit
            }
        }
    }

    @Test
    fun templatesSetTheirContentAndConditions() {
        assertThat(draft(CardTemplate.BLANK)).isEqualTo(AutomationCard("template-BLANK", ""))
        draft(CardTemplate.MORNING).let {
            assertThat(it.content).isEqualTo(CardContent.Actions)
            assertThat(it.conditions.weekdays).containsExactly(1, 2, 3, 4, 5)
            assertThat(it.conditions.timeRange).isEqualTo(CardTimeRange(360, 600))
        }
        draft(CardTemplate.BLUETOOTH_AUDIO).let {
            assertThat(it.content).isEqualTo(CardContent.Media)
            assertThat(it.conditions.bluetoothConnectedOnly).isTrue()
        }
        draft(CardTemplate.CHARGING).let {
            assertThat(it.content).isEqualTo(CardContent.Actions)
            assertThat(it.conditions.chargingOnly).isTrue()
        }
        assertThat(draft(CardTemplate.WEATHER).content).isEqualTo(CardContent.Weather())
        assertThat(draft(CardTemplate.WEATHER).title).isEqualTo("Weather")
    }

    @Test
    fun templatesRespectTheCardLimitAndUseTheGivenIdentity() {
        val full = (0 until CardsSettings.MAX_CARDS).map { AutomationCard("synthetic-$it", "Synthetic $it") }
        for (template in CardTemplate.entries - CardTemplate.BLANK) {
            assertThat(saveCardDraft(full, draft(template))).isNull()
        }
        assertThat(cardTemplateDraft(CardTemplate.MORNING, "fresh-id", "Name").id).isEqualTo("fresh-id")
    }
}
