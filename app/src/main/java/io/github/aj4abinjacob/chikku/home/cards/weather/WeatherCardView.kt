package io.github.aj4abinjacob.chikku.home.cards.weather

import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.WeatherUnit
import io.github.aj4abinjacob.chikku.platform.cards.weather.CardWeatherSource
import io.github.aj4abinjacob.chikku.platform.cards.weather.WeatherState
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Visible-card requests only. Detachment and lifecycle stop cancel both collection and HTTP.
 * A card without a city or online access never requests anything and offers only [onSetUp].
 */
class WeatherCardView(
    private val activity: ComponentActivity,
    private val config: CardContent.Weather,
    private val source: CardWeatherSource,
    colors: SurfaceColors,
    /** Opens this card's settings; the only control an unconfigured card shows. */
    onSetUp: () -> Unit,
) : LinearLayout(activity) {
    private val chrome = weatherCardChrome(config)
    private val temperature = text(colors.onSurface, 30f).apply { setTypeface(typeface, Typeface.BOLD) }
    private val conditions = text(colors.onSurface, 16f)
    private val range = text(colors.onSurfaceVariant, 14f)
    private val status = text(colors.onSurfaceVariant, 14f).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val refresh = Button(context, null, 0, R.style.Widget_Chikku_Button_Borderless).apply {
        text = context.getString(R.string.card_weather_refresh)
        contentDescription = text
        setTextColor(colors.onSurface)
        isAllCaps = false
        minHeight = dp(48)
        minimumHeight = dp(48)
        setOnClickListener { refresh() }
    }
    private val setUp = Button(context, null, 0, R.style.Widget_Chikku_Button_Borderless).apply {
        text = context.getString(R.string.card_weather_setup)
        contentDescription = context.getString(R.string.card_weather_setup_description)
        setTextColor(colors.onSurface)
        isAllCaps = false
        minHeight = dp(48)
        minimumHeight = dp(48)
        setOnClickListener { onSetUp() }
    }
    private val refreshes = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private var observing: Job? = null

    init {
        orientation = VERTICAL
        config.location?.let { location ->
            addView(text(colors.onSurfaceVariant, 16f).apply {
                text = location.label
                ViewCompat.setAccessibilityHeading(this, true)
            })
        }
        addView(temperature)
        addView(conditions)
        addView(range)
        addView(status)
        val actionParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        if (chrome.showRefresh) addView(refresh, actionParams) else addView(setUp, actionParams)
        if (chrome.showAttribution) addView(text(colors.onSurfaceVariant, 12f).apply {
            text = weatherAttribution(context)
            movementMethod = LinkMovementMethod.getInstance()
            setLinkTextColor(colors.onSurface)
            minimumHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
        })
        val setup = chrome.setup
        if (setup != null) renderSetup(setup) else render(WeatherState.Loading)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observing?.cancel()
        observing = null
        if (chrome.setup != null) return
        observing = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                refreshes.onStart { emit(false) }.collectLatest { force ->
                    render(WeatherState.Loading)
                    render(source.forecast(config, force))
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        observing?.cancel()
        observing = null
        super.onDetachedFromWindow()
    }

    fun refresh() {
        if (isAttachedToWindow && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            chrome.setup == null) refreshes.tryEmit(true)
    }

    private fun renderSetup(need: WeatherSetupNeed) {
        temperature.visibility = GONE
        conditions.visibility = GONE
        range.visibility = GONE
        status.text = context.getString(need.message)
    }

    private fun render(state: WeatherState) {
        val ready = state as? WeatherState.Ready
        temperature.visibility = if (ready == null) GONE else VISIBLE
        conditions.visibility = temperature.visibility
        range.visibility = temperature.visibility
        if (ready != null) {
            val reading = ready.reading
            val unit = context.getString(if (reading.unit == WeatherUnit.CELSIUS) R.string.card_weather_celsius_short else R.string.card_weather_fahrenheit_short)
            temperature.text = context.getString(R.string.card_weather_temperature, reading.temperature.roundToInt(), unit)
            conditions.text = context.getString(weatherConditionLabel(reading.conditionCode))
            range.text = context.getString(R.string.card_weather_range, reading.high.roundToInt(), reading.low.roundToInt(), unit)
        }
        status.text = context.getString(when (state) {
            WeatherState.Disabled -> R.string.card_weather_disabled
            WeatherState.MissingLocation -> R.string.card_weather_choose_city
            WeatherState.Loading -> R.string.card_weather_loading
            WeatherState.Unavailable -> R.string.card_weather_unavailable
            is WeatherState.Ready -> if (state.reading.providerWarning) R.string.card_weather_provider_warning else R.string.card_weather_estimate
        })
        refresh.isEnabled = chrome.setup == null && state != WeatherState.Loading
    }

    private fun text(color: Int, size: Float) = TextView(context).apply {
        setTextColor(color)
        textSize = size
        setPadding(0, dp(4), 0, dp(4))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

/** Chikku condition groups; MET symbols map to these internal presentation values. */
internal fun weatherConditionLabel(code: Int): Int = when (code) {
    0, 1 -> R.string.card_weather_clear
    2 -> R.string.card_weather_partly_cloudy
    3 -> R.string.card_weather_cloudy
    45, 48 -> R.string.card_weather_fog
    51, 53, 55 -> R.string.card_weather_drizzle
    56, 57, 66, 67 -> R.string.card_weather_freezing_rain
    61, 63, 65, 80, 81, 82 -> R.string.card_weather_rain
    68 -> R.string.card_weather_sleet
    71, 73, 75, 77, 85, 86 -> R.string.card_weather_snow
    95, 96, 97, 99 -> R.string.card_weather_thunderstorm
    else -> R.string.card_weather_conditions_unknown
}
