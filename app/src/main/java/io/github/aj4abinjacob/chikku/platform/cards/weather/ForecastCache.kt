package io.github.aj4abinjacob.chikku.platform.cards.weather

import android.util.AtomicFile
import java.io.File
import org.json.JSONObject

data class CachedForecast(
    val body: String,
    val expiresAtMillis: Long,
    val lastModified: String?,
    val receivedAtMillis: Long,
    val providerWarning: Boolean,
)

/** Caller owns an IO dispatcher and serialization. Re-fetchable cache, never portable user state. */
interface ForecastCache {
    fun read(key: String): CachedForecast?
    fun write(key: String, value: CachedForecast)
    fun readNotBefore(): Long
    fun writeNotBefore(value: Long)
}

class MemoryForecastCache : ForecastCache {
    private val values = LinkedHashMap<String, CachedForecast>()
    private var notBefore = 0L
    override fun read(key: String) = values[key]
    override fun write(key: String, value: CachedForecast) {
        values.remove(key)
        values[key] = value
        while (values.size > 24) values.remove(values.keys.first())
    }
    override fun readNotBefore() = notBefore
    override fun writeNotBefore(value: Long) { notBefore = value }
}

/** Lazy directory access; all methods are invoked only by the weather source on its IO dispatcher. */
class FileForecastCache(private val directory: () -> File) : ForecastCache {
    override fun read(key: String): CachedForecast? = runCatching {
        require(key.matches(Regex("[a-f0-9]{64}")))
        val file = File(directory(), "$key.json")
        if (!file.exists() || file.length() > BoundedWeatherHttpClient.MAX_BYTES * 2L) return null
        val json = JSONObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
        val body = json.getString("body")
        require(body.toByteArray().size <= BoundedWeatherHttpClient.MAX_BYTES)
        CachedForecast(body, json.getLong("expires"), json.optString("modified").takeIf(String::isNotEmpty),
            json.getLong("received"), json.getBoolean("warning"))
    }.getOrNull()

    override fun write(key: String, value: CachedForecast) {
        require(key.matches(Regex("[a-f0-9]{64}")))
        val dir = directory().apply { check(isDirectory || mkdirs()) }
        val file = File(dir, "$key.json")
        val text = JSONObject().put("body", value.body).put("expires", value.expiresAtMillis)
            .put("modified", value.lastModified ?: "").put("received", value.receivedAtMillis)
            .put("warning", value.providerWarning).toString()
        require(text.toByteArray().size <= BoundedWeatherHttpClient.MAX_BYTES * 2)
        atomicWrite(file, text)
        // Bound storage to 24 responses, plus the tiny persisted rate-limit record.
        dir.listFiles { item -> item.name.matches(Regex("[a-f0-9]{64}\\.json")) }
            ?.sortedByDescending(File::lastModified)?.drop(24)?.forEach { AtomicFile(it).delete() }
    }

    override fun readNotBefore(): Long = runCatching {
        val file = File(directory(), "limits.json")
        if (!file.exists() || file.length() > 128) return 0
        JSONObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() }).getLong("notBefore")
    }.getOrDefault(0)

    override fun writeNotBefore(value: Long) {
        val dir = directory().apply { check(isDirectory || mkdirs()) }
        atomicWrite(File(dir, "limits.json"), JSONObject().put("notBefore", value).toString())
    }

    private fun atomicWrite(file: File, text: String) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (failure: Exception) {
            atomic.failWrite(output)
            throw failure
        }
    }
}
