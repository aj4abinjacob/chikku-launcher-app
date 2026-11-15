package io.github.aj4abinjacob.chikku.prompt

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chikku is used to style the phone, so its own prompts and menus must look like Chikku, not like
 * the device skin (One UI, MIUI). Platform dialogs, popup menus and toasts take that skin; use
 * [ChikkuPrompts], ChikkuMessages (or a Compose sheet/dialog in the settings theme) instead.
 */
class NoPlatformDialogsTest {
    @Test
    fun appCodeUsesChikkuPromptsInsteadOfPlatformDialogs() {
        val sources = File("src/main/java").takeIf { it.isDirectory } ?: File("app/src/main/java")
        assertTrue("source root not found from ${File(".").absolutePath}", sources.isDirectory)
        val offenders = sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in ALLOWED }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//")
                    if (FORBIDDEN.any { it in code }) "${file.relativeTo(sources)}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        assertTrue(
            "Platform dialogs/menus take the device skin; use ChikkuPrompts, ChikkuMessages or a Compose sheet:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private companion object {
        val FORBIDDEN = listOf("android.app.AlertDialog", "AlertDialog.Builder", "android.widget.PopupMenu", "android.widget.Toast", "Toast.makeText")

        /**
         * ShortcutPinActivity: a translucent pin request that finishes at once, so no Chikku surface
         * outlives it to show a message.
         */
        val ALLOWED = setOf("ShortcutPinActivity.kt")
    }
}
