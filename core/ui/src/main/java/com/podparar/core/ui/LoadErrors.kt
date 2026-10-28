package com.podparar.core.ui

import androidx.annotation.StringRes
import com.podparar.core.model.LoadError

/** The plain-words message for why loading a show or a search failed. */
@StringRes
fun LoadError.messageRes(): Int = when (this) {
    LoadError.OFFLINE -> R.string.error_offline
    LoadError.NOT_FOUND -> R.string.error_not_found
    LoadError.NOT_A_FEED -> R.string.error_not_a_feed
    LoadError.INVALID_LINK -> R.string.error_invalid_link
    LoadError.UNKNOWN -> R.string.error_generic
}
