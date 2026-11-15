package io.github.aj4abinjacob.chikku.platform.cards.weather

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OfflineCitySearchTest {
    private fun gzip(value: String) = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(value.toByteArray()) } }.toByteArray()
    private val index = """Paris, France	48.85	2.35	paris|parisius	france fr ile de france
Paris, Texas, United States	33.66	-95.55	paris	united states us texas
São Paulo, Brazil	-23.55	-46.63	sao paulo|sampa	brazil br
München, Germany	48.13	11.58	munchen|munich	germany de bavaria
""".replace("\\t", "\t")

    @Test fun exactAliasDiacriticsAndCommaCountryDisambiguationWorkWithoutNetwork() = runTest {
        val source = OfflineCitySearch({ ByteArrayInputStream(gzip(index)) }, StandardTestDispatcher(testScheduler))
        fun WeatherLocationsResult.labels() = (this as WeatherLocationsResult.Found).locations.map { it.label }
        assertThat(source.search("Paris, US").labels()).containsExactly("Paris, Texas, United States")
        assertThat(source.search("São Paulo").labels()).containsExactly("São Paulo, Brazil")
        assertThat(source.search("Sampa").labels()).containsExactly("São Paulo, Brazil")
        assertThat(source.search("Munich, Germany").labels()).containsExactly("München, Germany")
        assertThat(source.search("Paris").labels()).containsExactly("Paris, France", "Paris, Texas, United States").inOrder()
        assertThat(source.search("No such city").labels()).isEmpty()
    }

    @Test fun invalidQueriesDoNotReadIndexAndDamagedIndexReturnsTypedFailure() = runTest {
        var reads = 0
        val source = OfflineCitySearch({ reads++; error("Unexpected read") }, StandardTestDispatcher(testScheduler))
        for (query in listOf("", "a", "bad\nname", "x".repeat(81))) assertThat(source.search(query)).isEqualTo(WeatherLocationsResult.Found(emptyList()))
        assertThat(reads).isEqualTo(0)
        assertThat(OfflineCitySearch({ ByteArrayInputStream(byteArrayOf()) }, StandardTestDispatcher(testScheduler)).search("Paris"))
            .isEqualTo(WeatherLocationsResult.Unavailable)
    }

    @Test fun resultsAreBoundedAndMalformedCoordinatesCannotReachSettings() = runTest {
        val many = (1..20).joinToString("\n") { "Sample $it\t$it\t2\tsample $it\texample ex" } + "\n"
        val source = OfflineCitySearch({ ByteArrayInputStream(gzip(many)) }, StandardTestDispatcher(testScheduler))
        assertThat((source.search("Sample") as WeatherLocationsResult.Found).locations).hasSize(5)
        val bad = OfflineCitySearch({ ByteArrayInputStream(gzip("Bad\t91\t2\tbad\texample\n")) }, StandardTestDispatcher(testScheduler))
        assertThat(bad.search("Bad")).isEqualTo(WeatherLocationsResult.Unavailable)
    }
}
