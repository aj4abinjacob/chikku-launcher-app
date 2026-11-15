package io.github.aj4abinjacob.chikku.settings.components

import io.github.aj4abinjacob.chikku.core.model.AppKey

/** Stable lazy-list key for a component in a profile: user, package and class together. */
internal fun AppKey.listKey(): String = "$userSerial/$packageName/$className"
