package io.github.aj4abinjacob.chikku.platform

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import org.junit.Test

/** Public-API policy only. This is not a managed/private-profile device transition test. */
class ProfilePrivacyBoundaryTest {
    private val work = SourceProfile(null, 71, ProfileType.WORK)

    @Test fun accessibleUnpausedSnapshotRetainsItsIdentity() {
        assertThat(profileSnapshotIsCurrent(work, 71, ProfileType.WORK, false, true)).isTrue()
    }

    @Test fun removedOrReusedUserHandleCannotKeepItsOldSerial() {
        assertThat(profileSnapshotIsCurrent(work, -1, ProfileType.WORK, false, true)).isFalse()
        assertThat(profileSnapshotIsCurrent(work, 72, ProfileType.WORK, false, true)).isFalse()
    }

    @Test fun quietLockedAndInaccessibleSnapshotsAreRejected() {
        assertThat(profileSnapshotIsCurrent(work, 71, ProfileType.WORK, true, true)).isFalse()
        assertThat(profileSnapshotIsCurrent(work, 71, ProfileType.WORK, false, false)).isFalse()
        assertThat(profileSnapshotIsCurrent(work, 71, null, false, true)).isFalse()
    }

    @Test fun changedProfileClassificationCannotPublishUnderTheOldType() {
        assertThat(profileSnapshotIsCurrent(work, 71, ProfileType.OTHER, false, true)).isFalse()
        assertThat(profileSnapshotIsCurrent(work, 71, ProfileType.PERSONAL, false, true)).isFalse()
    }

    @Test fun privateProfileIsExcludedIncludingWhenItIsTheCallingUser() {
        for (calling in listOf(false, true)) {
            val type = classifyProfile(calling, UserTypeInfo.Known(USER_TYPE_PRIVATE))
            assertThat(type).isNull()
            assertThat(profileSnapshotIsCurrent(work, 71, type, false, true)).isFalse()
        }
    }

    @Test fun inaccessibleCallingUserFailsClosedLikeAnyOtherUser() {
        assertThat(classifyProfile(true, UserTypeInfo.Inaccessible)).isNull()
        assertThat(classifyProfile(false, UserTypeInfo.Inaccessible)).isNull()
    }

    @Test fun api29UnknownSecondaryTypeRemainsOtherWithoutInventingPrivateSupport() {
        assertThat(classifyProfile(true, UserTypeInfo.NotAvailable)).isEqualTo(ProfileType.PERSONAL)
        assertThat(classifyProfile(false, UserTypeInfo.NotAvailable)).isEqualTo(ProfileType.OTHER)
    }
}
