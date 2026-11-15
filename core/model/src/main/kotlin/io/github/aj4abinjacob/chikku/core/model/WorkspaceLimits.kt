package io.github.aj4abinjacob.chikku.core.model

/** Bounds new layouts and untrusted imports because HOME materializes a model for each page.
 * 256 retains headroom above the former 64-page portable limit without permitting an unbounded
 * allocation from a tiny archive. Device-private recovery preserves older over-limit layouts.
 */
object WorkspaceLimits {
    const val MAX_PAGES = 256
}
