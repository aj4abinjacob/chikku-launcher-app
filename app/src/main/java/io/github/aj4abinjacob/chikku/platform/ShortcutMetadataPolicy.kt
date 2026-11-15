package io.github.aj4abinjacob.chikku.platform

/** Non-consuming permission to read private shortcut labels/artwork. Never uses launch grants. */
fun interface ShortcutMetadataPolicy {
    fun mayRead(packageName: String, userSerial: Long): Boolean

    companion object {
        /** An unwired reader must not expose metadata. Tests may explicitly supply an allow policy. */
        val DENY = ShortcutMetadataPolicy { _, _ -> false }
    }
}
