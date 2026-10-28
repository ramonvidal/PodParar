package com.podparar.core.model

/** Why loading a show or search failed, so screens can say it in plain words. */
enum class LoadError { OFFLINE, NOT_FOUND, NOT_A_FEED, INVALID_LINK, UNKNOWN }

/** A failure the screens can explain: carries the [LoadError] to show. */
class PodcastException(val error: LoadError, cause: Throwable? = null) : Exception(error.name, cause)
