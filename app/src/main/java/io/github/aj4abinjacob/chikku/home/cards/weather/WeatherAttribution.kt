package io.github.aj4abinjacob.chikku.home.cards.weather

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.SpannableString
import android.text.style.URLSpan
import android.view.View
import io.github.aj4abinjacob.chikku.R

/** Optional browser attribution links must remain harmless on devices without a browser. */
internal fun weatherAttribution(context: Context): SpannableString {
    val text = SpannableString(Html.fromHtml(context.getString(R.string.card_weather_attribution), Html.FROM_HTML_MODE_LEGACY))
    text.getSpans(0, text.length, URLSpan::class.java).forEach { original ->
        val start = text.getSpanStart(original)
        val end = text.getSpanEnd(original)
        val flags = text.getSpanFlags(original)
        text.removeSpan(original)
        text.setSpan(object : URLSpan(original.url) {
            override fun onClick(widget: View) {
                runCatching { widget.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }, start, end, flags)
    }
    return text
}
