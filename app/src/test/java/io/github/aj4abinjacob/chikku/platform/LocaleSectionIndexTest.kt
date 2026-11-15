package io.github.aj4abinjacob.chikku.platform

import android.icu.text.Collator
import android.os.LocaleList
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerModel
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocaleSectionIndexTest {

    private fun index(vararg tags: String) = LocaleSectionIndex { LocaleList.forLanguageTags(tags.joinToString(",")) }

    /**
     * Sections for [labels] in drawer order. On a device java.text.Collator is backed by ICU; under
     * Robolectric it is the desktop JDK's, so sort with ICU directly to match the phone.
     */
    private fun sections(labels: List<String>, vararg tags: String): List<String> {
        val collator = Collator.getInstance(Locale.forLanguageTag(tags.first())).apply { strength = Collator.PRIMARY }
        val apps = labels.mapIndexed { i, label -> LauncherApp(AppKey("p$i", "c$i", 0), label, ProfileType.PERSONAL) }
        val sorted = apps.sortedWith { a, b -> collator.compare(a.label, b.label) }
        val sectionOf = index(*tags)::sectionOf
        // Each bucket is one contiguous run, so no drawer position is unreachable.
        val runs = sorted.map { sectionOf(it.label) }.fold(mutableListOf<String>()) { acc, s -> acc.apply { if (lastOrNull() != s) add(s) } }
        assertThat(runs).containsNoDuplicates()
        return DrawerModel.sections(sorted, sectionOf).map { it.letter }
    }

    @Test
    fun digitsSymbolsEmojiAndInvisibleMarksGoToTheRightPlace() {
        val result = sections(listOf("1Password", "#hashtag", "@Mail", "😀 Fun", "(Beta) Tool", "\u200EZoom", "bravo", "alpha"), "en-US")
        assertThat(result).containsExactly("#", "A", "B", "Z").inOrder()
        assertThat(index("en-US").sectionOf("")).isEqualTo(DrawerModel.OTHER_SECTION)
    }

    @Test
    fun accentsFoldIntoTheirLetterUnlessTheLanguageSeparatesThem() {
        val labels = listOf("Écran", "Edge", "Email", "Ångström", "Apple", "Zebra", "Öl")
        assertThat(sections(labels, "fr-FR")).containsExactly("A", "E", "O", "Z").inOrder()
        // Swedish treats Å and Ö as letters after Z.
        assertThat(sections(labels, "sv-SE")).containsExactly("A", "E", "Z", "Å", "Ö").inOrder()
    }

    @Test
    fun cjkAndKoreanUseLanguageBucketsNotOneEntryPerCharacter() {
        val korean = sections(listOf("가계부", "각도기", "간식", "나무", "다음", "Chrome"), "ko-KR", "en-US")
        assertThat(korean).containsExactly("ㄱ", "ㄴ", "ㄷ", "C").inOrder()
        val japanese = sections(listOf("あさひ", "いろは", "かめら", "きっぷ", "Maps"), "ja-JP", "en-US")
        assertThat(japanese.size).isEqualTo(3)
        assertThat(japanese).contains("M")
    }

    @Test
    fun scriptsOutsideThePhoneLanguagesShareOneBucket() {
        val result = sections(listOf("Alpha", "Ζήτα", "Яндекс", "Мапс", "微信", "支付宝"), "en-US")
        assertThat(result.first()).isEqualTo("A")
        assertThat(result.size).isAtMost(3)
        assertThat(result).doesNotContain("微")
    }

    @Test
    fun turkishDottedCapitalStaysDistinctFromDotlessI() {
        val result = sections(listOf("ılık", "istanbul", "Kahve"), "tr-TR")
        assertThat(result).containsExactly("I", "İ", "K").inOrder()
    }
}
