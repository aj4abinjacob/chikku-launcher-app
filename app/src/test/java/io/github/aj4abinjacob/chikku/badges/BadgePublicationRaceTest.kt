package io.github.aj4abinjacob.chikku.badges

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BadgePublicationRaceTest {
    private val badges = mapOf(BadgeKey("synthetic", 0) to BadgeInfo(3))
    @Test fun clearedProfileAndDisconnectedServiceRejectInflightPublication() {
        val repository = BadgeRepository()
        repository.connect()
        val beforeProfileChange = repository.revision()
        repository.clear()
        repository.publishIfCurrent(beforeProfileChange, badges)
        assertThat(repository.badges.value).isEmpty()
        repository.publishIfCurrent(repository.revision(), badges)
        assertThat(repository.badges.value).isEqualTo(badges)
        val beforeDisconnect = repository.revision()
        repository.disconnect()
        repository.connect()
        repository.publishIfCurrent(beforeDisconnect, badges)
        assertThat(repository.badges.value).isEmpty()
        assertThat(repository.connected.value).isTrue()
        repository.disconnect()
        assertThat(repository.connected.value).isFalse()
    }
    @Test fun revokedAccessClearsEnabledBadgeData() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = BadgeRepository()
        repository.connect()
        repository.publish(badges)
        val access = BadgeAccess(context)
        assertThat(access.isGranted()).isFalse()
        access.apply(BadgeStyle.COUNT, repository, Dispatchers.Unconfined)
        assertThat(repository.badges.value).isEmpty()
        assertThat(repository.connected.value).isFalse()
    }
}
