package io.github.aj4abinjacob.chikku.data.cards

import android.util.AtomicFile
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

enum class CardWidgetBindingPhase { CONSENT, CONFIGURATION, BOUND }

/**
 * Device-local IDs; portable card settings contain only the provider and logical instance ID. A
 * restore gives every embedded widget a new instance ID (`ChikkuBackup.withoutWidgetIds`), so a
 * binding made before the restore no longer matches its card and is released by reconciliation.
 */
data class CardWidgetBinding(
    val cardId: String,
    val instanceId: String,
    val provider: ComponentKey,
    val appWidgetId: Int,
    val token: String,
    val phase: CardWidgetBindingPhase,
    val previousId: Int? = null,
)

sealed interface CardWidgetBindingState {
    data class Ready(
        val bindings: Map<String, CardWidgetBinding> = emptyMap(),
        val revision: Long = 0,
    ) : CardWidgetBindingState
    /** The damaged file stays preserved. No orphan sweep is allowed until explicit recovery. */
    data object Corrupt : CardWidgetBindingState
}

interface CardWidgetBindingStore {
    suspend fun load(): CardWidgetBindingState
    /** Compare-and-save by revision; concurrent setup changes cannot overwrite each other. */
    suspend fun save(state: CardWidgetBindingState.Ready): Boolean
    /** Explicit local setup reset; portable provider/card settings are untouched. */
    suspend fun recover(): Boolean
}

class MemoryCardWidgetBindingStore(initial: CardWidgetBindingState.Ready = CardWidgetBindingState.Ready()) : CardWidgetBindingStore {
    private val mutex = Mutex()
    private var state = initial
    override suspend fun load(): CardWidgetBindingState = mutex.withLock { state }
    override suspend fun save(state: CardWidgetBindingState.Ready): Boolean = mutex.withLock {
        if (state.revision != this.state.revision) false else { this.state = state.copy(revision = state.revision + 1); true }
    }
    override suspend fun recover(): Boolean = mutex.withLock {
        state = CardWidgetBindingState.Ready(revision = state.revision + 1); true
    }
}

/** Stored under noBackupFilesDir, independent of settings backup and workspace widget IDs. */
class FileCardWidgetBindingStore(private val directory: File) : CardWidgetBindingStore {
    private val mutex = Mutex()
    private val file = AtomicFile(File(directory, FILE_NAME))

    override suspend fun load(): CardWidgetBindingState = mutex.withLock { read() }
    override suspend fun save(state: CardWidgetBindingState.Ready): Boolean = mutex.withLock {
        val current = read() as? CardWidgetBindingState.Ready ?: return@withLock false
        if (current.revision != state.revision) false else write(state.copy(revision = state.revision + 1))
    }
    override suspend fun recover(): Boolean = mutex.withLock {
        val previous = read()
        write(CardWidgetBindingState.Ready(revision = (previous as? CardWidgetBindingState.Ready)?.revision?.plus(1) ?: 0))
    }

    private fun read(): CardWidgetBindingState {
        if (!file.baseFile.exists() && !File(directory, "$FILE_NAME.bak").exists()) return CardWidgetBindingState.Ready()
        return try {
            val json = JSONObject(file.readFully().toString(Charsets.UTF_8))
            require(json.getInt("version") == 1)
            val values = json.getJSONArray("bindings")
            require(values.length() <= 24)
            val bindings = LinkedHashMap<String, CardWidgetBinding>()
            val ids = mutableSetOf<Int>()
            for (index in 0 until values.length()) {
                val value = values.getJSONObject(index)
                val provider = ComponentKey(value.getString("package"), value.getString("class"), value.getLong("serial"))
                val binding = CardWidgetBinding(value.getString("card"), value.getString("instance"), provider,
                    value.getInt("id"), value.getString("token"), CardWidgetBindingPhase.valueOf(value.getString("phase")),
                    if (value.has("previous")) value.getInt("previous") else null)
                require(binding.cardId.isNotBlank() && binding.cardId.length <= 80 && binding.instanceId.isNotBlank())
                require(binding.token.isNotBlank() && provider.className.isNotBlank() && provider.userSerial >= 0)
                require(binding.appWidgetId > 0 && ids.add(binding.appWidgetId))
                require(binding.previousId == null || (binding.previousId > 0 && ids.add(binding.previousId)))
                require(bindings.put(binding.cardId, binding) == null)
            }
            CardWidgetBindingState.Ready(bindings, json.optLong("revision", 0))
        } catch (_: Exception) {
            // Keep both the original and a stable damaged copy. Reading never silently resets it.
            runCatching {
                val preserved = File(directory, "$FILE_NAME.damaged")
                if (!preserved.exists() && file.baseFile.exists()) file.baseFile.copyTo(preserved)
            }
            CardWidgetBindingState.Corrupt
        }
    }

    private fun write(state: CardWidgetBindingState.Ready): Boolean {
        var stream: java.io.FileOutputStream? = null
        return try {
            directory.mkdirs()
            val values = JSONArray()
            state.bindings.values.forEach { binding -> values.put(JSONObject().apply {
                put("card", binding.cardId); put("instance", binding.instanceId)
                put("package", binding.provider.packageName); put("class", binding.provider.className)
                put("serial", binding.provider.userSerial); put("id", binding.appWidgetId)
                put("token", binding.token); put("phase", binding.phase.name)
                binding.previousId?.let { put("previous", it) }
            }) }
            val json = JSONObject().put("version", 1).put("revision", state.revision).put("bindings", values)
            stream = file.startWrite()
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            true
        } catch (_: Exception) {
            stream?.let(file::failWrite)
            false
        }
    }

    companion object { internal const val FILE_NAME = "card-widget-bindings.json" }
}
