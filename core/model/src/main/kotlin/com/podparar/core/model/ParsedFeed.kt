package com.podparar.core.model

/** A feed as downloaded: the show and its episodes in feed order. */
data class ParsedFeed(val podcast: Podcast, val episodes: List<Episode>)
