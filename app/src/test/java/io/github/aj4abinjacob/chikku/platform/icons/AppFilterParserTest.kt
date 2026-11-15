package io.github.aj4abinjacob.chikku.platform.icons

import android.util.Xml
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppFilterParserTest {

    private fun parser(resource: String): XmlPullParser {
        val text = requireNotNull(javaClass.classLoader?.getResource(resource)) { "missing $resource" }.readText()
        return Xml.newPullParser().apply { setInput(StringReader(text)) }
    }

    private fun parserOf(xml: String): XmlPullParser = Xml.newPullParser().apply { setInput(StringReader(xml)) }

    @Test
    fun parsesComponentsAndFallbackTheme() {
        val filter = AppFilterParser.parse(parser("icons/appfilter_fixture.xml"))

        assertThat(filter.components).containsExactly(
            "com.example.mail/com.example.mail.MainActivity", "mail",
            "com.example.maps/com.example.maps.ui.Launcher", "maps",
        )
        assertThat(filter.drawableFor("com.example.mail", "com.example.mail.MainActivity")).isEqualTo("mail")
        assertThat(filter.drawableFor("com.example.mail", "com.example.mail.Other")).isNull()
        assertThat(filter.iconBacks).containsExactly("iconback_one", "iconback_two").inOrder()
        assertThat(filter.iconMask).isEqualTo("iconmask")
        assertThat(filter.iconUpon).isEqualTo("iconupon")
        assertThat(filter.scale).isEqualTo(0.8f)
        assertThat(filter.hasFallbackTheme).isTrue()
    }

    @Test
    fun emptyOrUnrelatedXmlYieldsEmptyFilter() {
        val filter = AppFilterParser.parse(parserOf("<resources><something a=\"b\"/></resources>"))
        assertThat(filter.components).isEmpty()
        assertThat(filter.hasFallbackTheme).isFalse()
        assertThat(filter.scale).isEqualTo(1f)
    }

    @Test
    fun invalidScaleIsIgnored() {
        listOf("0", "-1", "NaN", "abc", "50").forEach { factor ->
            val filter = AppFilterParser.parse(parserOf("<resources><scale factor=\"$factor\"/></resources>"))
            assertThat(filter.scale).isEqualTo(1f)
        }
    }

    @Test
    fun drawableListKeepsOrderWithoutDuplicatesOrBlanks() {
        val names = AppFilterParser.parseDrawableList(parser("icons/drawable_fixture.xml"))
        assertThat(names).containsExactly("mail", "maps", "mail_alt").inOrder()
    }

    @Test
    fun normalizesComponentStrings() {
        assertThat(AppFilterParser.normalizeComponent("ComponentInfo{a.b/a.b.C}")).isEqualTo("a.b/a.b.C")
        assertThat(AppFilterParser.normalizeComponent(" ComponentInfo{a.b/.C} ")).isEqualTo("a.b/a.b.C")
        assertThat(AppFilterParser.normalizeComponent("a.b/a.b.C")).isEqualTo("a.b/a.b.C")
        assertThat(AppFilterParser.normalizeComponent("ComponentInfo{a.b}")).isNull()
        assertThat(AppFilterParser.normalizeComponent("ComponentInfo{/a.b.C}")).isNull()
        assertThat(AppFilterParser.normalizeComponent("ComponentInfo{a.b/}")).isNull()
        assertThat(AppFilterParser.normalizeComponent(":CALENDAR")).isNull()
    }
    @Test fun dynamicCalendarNamesUseTheCurrentDayAndKeepAStaticFallback() {
        val filter = AppFilterParser.parse(parserOf("""<resources>
            <calendar component="ComponentInfo{com.calendar/.Main}" prefix="calendar_"/>
            <calendar component="ComponentInfo{com.calendar/.Main}" prefix="ignored_"/>
            <calendar component="broken" prefix="bad/path"/>
            <item component="ComponentInfo{com.calendar/.Main}" drawable="calendar_static"/>
        </resources>"""))
        assertThat(filter.calendarDrawableFor("com.calendar", "com.calendar.Main", 11)).isEqualTo("calendar_11")
        assertThat(filter.calendarDrawableFor("com.calendar", "com.calendar.Main", 31)).isEqualTo("calendar_31")
        assertThat(filter.calendarDrawableFor("com.calendar", "com.calendar.Main", 0)).isNull()
        assertThat(filter.drawableFor("com.calendar", "com.calendar.Main")).isEqualTo("calendar_static")
        assertThat(filter.calendars).hasSize(1)
    }

    @Test fun calendarAliasFallbackRequiresAnUnambiguousPackagePrefix() {
        val filter = AppFilterParser.parse(parserOf("""<resources>
            <calendar component="ComponentInfo{com.calendar/.Old}" prefix="calendar_"/>
            <calendar component="ComponentInfo{com.calendar/.Older}" prefix="calendar_"/>
            <calendar component="ComponentInfo{com.ambiguous/.One}" prefix="first_"/>
            <calendar component="ComponentInfo{com.ambiguous/.Two}" prefix="second_"/>
        </resources>"""))
        assertThat(filter.calendarDrawableFor("com.calendar", "com.calendar.NewAlias", 11)).isEqualTo("calendar_11")
        assertThat(filter.calendarDrawableFor("com.ambiguous", "com.ambiguous.NewAlias", 11)).isNull()
        assertThat(filter.calendarDrawableFor("com.ambiguous", "com.ambiguous.One", 11)).isEqualTo("first_11")
        assertThat(filter.calendarDrawableFor("com.unmapped", "com.unmapped.Main", 11)).isNull()
    }
}
