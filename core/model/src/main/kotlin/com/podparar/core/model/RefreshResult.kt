package com.podparar.core.model

/** What a refresh found: how many feeds failed and the episodes that are new since the last refresh. */
data class RefreshResult(val failedFeeds: Int, val newEpisodes: List<Episode>)
