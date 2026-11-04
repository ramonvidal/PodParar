package com.podparar.core.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * UpdateRepository and the distribution channel: the Play build never updates itself, so with
 * `UpdateChannel(selfUpdate = false)` nothing is published and a manual check finds nothing, without
 * going to the network. Each Robolectric test gets a fresh application, so its preferences start empty.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpdateRepositoryChannelTest {
    private fun repository(selfUpdate: Boolean) =
        UpdateRepository(RuntimeEnvironment.getApplication(), UpdateChannel(selfUpdate = selfUpdate))

    @Test
    fun playChannelHasSelfUpdateDisabled() {
        assertFalse(repository(selfUpdate = false).selfUpdateEnabled)
    }

    @Test
    fun githubChannelHasSelfUpdateEnabled() {
        // Only the flag: refresh/checkNow would go to the network on this channel.
        assertTrue(repository(selfUpdate = true).selfUpdateEnabled)
    }

    @Test
    fun refreshPublishesNothingWhenSelfUpdateIsDisabled() = runTest {
        val repository = repository(selfUpdate = false)
        assertNull(repository.available.value)

        repository.refresh()

        assertNull(repository.available.value)
    }

    @Test
    fun checkNowReturnsNullWhenSelfUpdateIsDisabled() = runTest {
        val repository = repository(selfUpdate = false)

        assertNull(repository.checkNow())
        assertNull(repository.available.value)
    }

    @Test
    fun repeatedChecksStayEmptyWhenSelfUpdateIsDisabled() = runTest {
        val repository = repository(selfUpdate = false)

        repository.refresh()
        assertNull(repository.checkNow())
        repository.refresh()

        assertNull(repository.available.value)
    }
}
