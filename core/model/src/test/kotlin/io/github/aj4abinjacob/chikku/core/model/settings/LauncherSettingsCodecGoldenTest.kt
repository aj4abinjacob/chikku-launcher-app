package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.security.MessageDigest
import org.junit.Test

/**
 * Pins the wire format of [LauncherSettingsCodec]: the exact encoded JSON (keys and key order) and
 * the decoded value for a broad set of awkward inputs. These goldens guard internal restructuring of
 * the codec; they must only change deliberately, together with a documented format change.
 *
 * To regenerate after an intentional change: run with the environment variable
 * `UPDATE_CODEC_GOLDENS=1`; the files under src/test/resources/codec are rewritten and the test
 * fails once so the change is reviewed.
 *
 * Decode cases are stored as one `case-name sha256(toString)` line each, so the file stays small
 * but any changed decoded value is caught.
 */
class LauncherSettingsCodecGoldenTest {

    @Test
    fun encodedDefaultsMatchGolden() {
        assertGolden("encode-default.json", LauncherSettingsCodec.encode(LauncherSettings()))
    }

    @Test
    fun encodedNonDefaultEverywhereMatchesGolden() {
        val decoded = LauncherSettingsCodec.fromMap(everyKeyChanged())
        // The mutation really moved (almost) every field away from its default.
        assertThat(decoded).isNotEqualTo(LauncherSettings())
        assertGolden("encode-non-default.json", LauncherSettingsCodec.encode(decoded))
        // And that output decodes back to itself.
        assertThat(LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(decoded))).isEqualTo(decoded)
    }

    @Test
    fun decodedAwkwardInputsMatchGolden() {
        val lines = decodeCases().map { (name, input) ->
            val result = try {
                sha256(LauncherSettingsCodec.fromMap(input).toString())
            } catch (e: SettingsFormatException) {
                "rejected:" + e.message
            }
            "$name $result"
        }
        assertGolden("decode-cases.txt", lines.joinToString("\n"))
    }

    private fun decodeCases(): List<Pair<String, Map<String, Any?>>> {
        val base = parsedDefaults()
        val cases = linkedMapOf<String, Map<String, Any?>>()
        cases["empty"] = emptyMap()
        cases["onlyFormatVersion"] = mapOf("formatVersion" to 99L)
        cases["everyKeyChanged"] = everyKeyChanged()
        cases["defaultMap"] = base
        cases["cardsNull"] = mapOf("cards" to null)
        for (section in base.keys.filter { base[it] is Map<*, *> }) {
            cases["missing:$section"] = base - section
            cases["wrongType:$section"] = base + (section to "not an object")
            cases["nullSection:$section"] = base + (section to null)
            cases["emptySection:$section"] = base + (section to emptyMap<String, Any?>())
            cases["unknownKey:$section"] = base + (section to (base[section] as Map<*, *> + ("zzUnknown" to 1L)))
        }
        // One case per key per kind of damage, applied to that key alone.
        for ((section, keys) in sectionKeys(base)) {
            for (key in keys) {
                val original = (base[section] as Map<*, *>)[key]
                val variants = linkedMapOf(
                    "changed" to changed(section, key, original),
                    "string" to "wrong-type",
                    "long" to 7L,
                    "hugeLong" to Long.MAX_VALUE,
                    "negative" to -123456789012L,
                    "double" to 1e12,
                    "fraction" to 0.3,
                    "bool" to true,
                    "list" to listOf("a", 1L),
                    "null" to null,
                )
                for ((kind, value) in variants) {
                    val sectionMap = (base[section] as Map<*, *>) + (key to value)
                    cases["$section.$key:$kind"] = base + (section to sectionMap)
                }
                val dropped = (base[section] as Map<*, *>) - key
                cases["$section.$key:absent"] = base + (section to dropped)
            }
        }
        // Legacy and fallback keys.
        cases["legacy:homeButtonChanged"] = base + ("gestures" to (base["gestures"] as Map<*, *> - "homeGesture" + ("homeButton" to "lock_screen")))
        cases["legacy:homeButtonOldDefault"] = base + ("gestures" to (base["gestures"] as Map<*, *> - "homeGesture" + ("homeButton" to "go_to_default_page")))
        cases["legacy:frequentApps"] = base + ("search" to (base["search"] as Map<*, *> - "historyLearningEnabled" + ("frequentAppsEnabled" to true)))
        cases["legacy:folderSharedColor"] = base + ("folders" to (base["folders"] as Map<*, *> - "iconBackgroundColor" + ("backgroundColor" to 0xFF123456L)))
        cases["legacy:folderSharedColorNull"] = base + ("folders" to (base["folders"] as Map<*, *> + ("iconBackgroundColor" to null) + ("backgroundColor" to 0xFF123456L)))
        return cases.toList()
    }

    /** The default map as the decoder really sees it: after a JSON round trip (Long/Double numbers). */
    @Suppress("UNCHECKED_CAST")
    private fun parsedDefaults(): Map<String, Any?> =
        MiniJson.parse(LauncherSettingsCodec.encode(LauncherSettings())) as Map<String, Any?>

    private fun sectionKeys(base: Map<String, Any?>): List<Pair<String, List<String>>> =
        base.entries.filter { it.key != "cards" && it.value is Map<*, *> }
            .map { (k, v) -> k to (v as Map<*, *>).keys.map { it as String } }

    /** Every non-structured key set to a valid value different from its default. */
    private fun everyKeyChanged(): Map<String, Any?> {
        val base = parsedDefaults()
        val out = linkedMapOf<String, Any?>()
        for ((section, value) in base) {
            out[section] = if (section == "cards" || value !is Map<*, *>) {
                value
            } else {
                value.entries.associate { (k, v) -> (k as String) to changed(section, k, v) }
            }
        }
        return out
    }

    private fun changed(section: String, key: String, original: Any?): Any? = when (original) {
        is Boolean -> !original
        is Long -> original + 1
        is Int -> original + 1L
        is Double -> original + 0.25
        is Float -> original.toDouble() + 0.25
        is List<*> -> listOf("sig:change")
        is Map<*, *> -> if (key == "covers") original else original.entries.associate { (k, v) -> (k as String) to changed(section, k, v) }
        null -> 0xFF123456L
        is String -> changedString(section, key, original)
        else -> original
    }

    private fun changedString(section: String, key: String, original: String): String {
        if (section == "gestures") return "app:10:a.b/a.b.C"
        if (key == "shape") return DockCornerShape.entries.first { it.name != original }.name
        val owner = LauncherSettings::class.java.declaredFields.firstOrNull { it.name == section }?.type
        val enumType = owner?.declaredFields?.firstOrNull { it.name == key }?.type
        if (enumType != null && enumType.isEnum) {
            val names = enumType.enumConstants.map { (it as Enum<*>).name }
            return names[(names.indexOf(original) + 1) % names.size]
        }
        return "pkg.changed"
    }

    private fun assertGolden(name: String, actual: String) {
        val file = File("src/test/resources/codec/$name")
        if (System.getenv("UPDATE_CODEC_GOLDENS") == "1") {
            file.parentFile.mkdirs()
            file.writeText(actual)
            throw AssertionError("Golden $name rewritten; review it, then rerun without UPDATE_CODEC_GOLDENS")
        }
        assertThat(file.exists()).isTrue()
        assertThat(actual).isEqualTo(file.readText())
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
