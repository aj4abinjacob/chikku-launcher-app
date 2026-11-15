package io.github.aj4abinjacob.chikku.settings.cards.weather

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherUnit
import io.github.aj4abinjacob.chikku.home.cards.weather.weatherAttribution
import io.github.aj4abinjacob.chikku.platform.cards.weather.CardWeatherSource
import io.github.aj4abinjacob.chikku.platform.cards.weather.WeatherLocationsResult
import io.github.aj4abinjacob.chikku.settings.components.SettingsChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsInfoText
import io.github.aj4abinjacob.chikku.settings.components.SettingsNavigationRow
import io.github.aj4abinjacob.chikku.settings.components.SettingsSwitchRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Local city search works before online consent; only the saved card's opted-in forecast connects. */
@Composable
fun WeatherCardEditorControls(
    config: CardContent.Weather,
    onChange: (CardContent.Weather) -> Unit,
    source: CardWeatherSource,
    enabled: Boolean = true,
) {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<WeatherLocationsResult?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        searching = false
        result = null
    }
    LaunchedEffect(config.onlineEnabled, enabled) { if (!config.onlineEnabled || !enabled) cancelSearch() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) cancelSearch() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); searchJob?.cancel() }
    }
    Column {
        SettingsSwitchRow(stringResource(R.string.card_weather_online), config.onlineEnabled, {
            cancelSearch()
            onChange(config.copy(onlineEnabled = it))
        }, summary = stringResource(R.string.card_weather_online_summary), enabled = enabled)
        config.location?.let { SettingsInfoText(stringResource(R.string.card_weather_selected_city, it.label)) }
        SettingsChoiceRow(
            title = stringResource(R.string.card_weather_unit), options = WeatherUnit.entries, selected = config.unit,
            optionLabel = { stringResource(if (it == WeatherUnit.CELSIUS) R.string.card_weather_celsius else R.string.card_weather_fahrenheit) },
            onSelect = { onChange(config.copy(unit = it)) }, enabled = enabled,
        )
        OutlinedTextField(query, {
            cancelSearch()
            query = it.take(80)
        }, label = { Text(stringResource(R.string.card_weather_city)) },
            supportingText = { Text(stringResource(R.string.card_weather_query_hint)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
            shape = RoundedCornerShape(16.dp))
        TextButton(enabled = enabled && !searching && query.trim().length >= 2 && query.none(Char::isISOControl), onClick = {
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@TextButton
            cancelSearch()
            searching = true
            searchJob = scope.launch {
                val found = source.searchLocations(query)
                ensureActive()
                result = found
                searching = false
            }
        }) { Text(stringResource(if (searching) R.string.card_weather_loading else R.string.card_weather_search)) }
        when (val found = result) {
            WeatherLocationsResult.Unavailable -> SettingsInfoText(stringResource(R.string.card_weather_city_unavailable))
            is WeatherLocationsResult.Found -> {
                if (found.locations.isEmpty()) SettingsInfoText(stringResource(R.string.card_weather_no_cities))
                found.locations.forEach { location ->
                    SettingsNavigationRow(location.label, {
                        cancelSearch()
                        query = ""
                        onChange(config.copy(location = location))
                    }, enabled = enabled)
                }
            }
            null -> Unit
        }
        val color = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
        val links = MaterialTheme.colorScheme.primary.toArgb()
        AndroidView(factory = { context -> TextView(context).apply {
            textSize = 12f
            movementMethod = LinkMovementMethod.getInstance()
        } }, update = {
            it.text = weatherAttribution(it.context)
            it.setTextColor(color)
            it.setLinkTextColor(links)
        }, modifier = Modifier.fillMaxWidth())
    }
}
