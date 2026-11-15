package io.github.aj4abinjacob.chikku.platform

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.platform.LaunchResult.Reason
import java.text.Collator
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pure mapping rules extracted from the platform adapters. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlatformMappingTest {

    @Test
    fun classifyProfileExcludesPrivateAndInaccessibleProfiles() {
        assertThat(classifyProfile(true, UserTypeInfo.NotAvailable)).isEqualTo(ProfileType.PERSONAL)
        assertThat(classifyProfile(true, UserTypeInfo.Known("android.os.usertype.full.SYSTEM"))).isEqualTo(ProfileType.PERSONAL)
        assertThat(classifyProfile(false, UserTypeInfo.Known(USER_TYPE_MANAGED))).isEqualTo(ProfileType.WORK)
        assertThat(classifyProfile(false, UserTypeInfo.Known("android.os.usertype.profile.CLONE"))).isEqualTo(ProfileType.OTHER)
        assertThat(classifyProfile(false, UserTypeInfo.NotAvailable)).isEqualTo(ProfileType.OTHER)
        assertThat(classifyProfile(false, UserTypeInfo.Known(USER_TYPE_PRIVATE))).isNull()
        assertThat(classifyProfile(true, UserTypeInfo.Known(USER_TYPE_PRIVATE))).isNull()
        assertThat(classifyProfile(false, UserTypeInfo.Inaccessible)).isNull()
    }

    @Test fun launchPolicyRejectsPrivateQuietLockedAndInaccessibleSecondaryProfiles() {
        assertThat(profileMayLaunch(true, false, true, UserTypeInfo.NotAvailable)).isTrue()
        assertThat(profileMayLaunch(false, false, true, UserTypeInfo.Known(USER_TYPE_MANAGED))).isTrue()
        assertThat(profileMayLaunch(false, true, true, UserTypeInfo.Known(USER_TYPE_MANAGED))).isFalse()
        assertThat(profileMayLaunch(false, false, false, UserTypeInfo.Known(USER_TYPE_MANAGED))).isFalse()
        assertThat(profileMayLaunch(false, false, true, UserTypeInfo.Inaccessible)).isFalse()
        assertThat(profileMayLaunch(true, false, true, UserTypeInfo.Known(USER_TYPE_PRIVATE))).isFalse()
    }

    @Test
    fun userTypeConstantsMatchPlatform() {
        assertThat(USER_TYPE_MANAGED).isEqualTo(android.os.UserManager.USER_TYPE_PROFILE_MANAGED)
        assertThat(USER_TYPE_PRIVATE).isEqualTo(android.os.UserManager.USER_TYPE_PROFILE_PRIVATE)
    }

    @Test
    fun profileActionsDependOnApiLevel() {
        val q = profileChangeActions(Build.VERSION_CODES.Q)
        assertThat(q).contains(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
        assertThat(q).contains(Intent.ACTION_LOCALE_CHANGED)
        assertThat(q).doesNotContain(Intent.ACTION_PROFILE_ACCESSIBLE)
        assertThat(profileChangeActions(Build.VERSION_CODES.S)).contains(Intent.ACTION_PROFILE_ACCESSIBLE)
        assertThat(profileChangeActions(Build.VERSION_CODES.S)).doesNotContain(Intent.ACTION_PROFILE_ADDED)
        assertThat(profileChangeActions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)).contains(Intent.ACTION_PROFILE_ADDED)
        assertThat(profileChangeActions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)).doesNotContain(Intent.ACTION_PROFILE_AVAILABLE)
        assertThat(profileChangeActions(Build.VERSION_CODES.VANILLA_ICE_CREAM)).contains(Intent.ACTION_PROFILE_AVAILABLE)
    }

    @Test
    fun displayLabelTrimsAndFallsBack() {
        assertThat(displayLabel("  Maps ", "com.maps")).isEqualTo("Maps")
        assertThat(displayLabel("", "com.maps")).isEqualTo("com.maps")
        assertThat(displayLabel(null, "com.maps")).isEqualTo("com.maps")
    }

    @Test
    fun sortIsDeterministicForEqualLabels() {
        val collator = Collator.getInstance(Locale.US).apply { strength = Collator.PRIMARY }
        val personal = SourceProfile(null, 0, ProfileType.PERSONAL)
        val other = SourceProfile(null, 11, ProfileType.OTHER)
        val apps = listOfNotNull(
            RawActivity("b", "b.Main", "Mail").toLauncherApp(other),
            RawActivity("b", "b.Main", "mail").toLauncherApp(personal),
            RawActivity("a", "a.Main", "MAIL").toLauncherApp(personal),
        )
        val expected = sortForDisplay(apps, collator).map { it.key }
        assertThat(sortForDisplay(apps.reversed(), collator).map { it.key }).isEqualTo(expected)
        assertThat(expected.map { it.packageName to it.userSerial })
            .containsExactly("a" to 0L, "b" to 0L, "b" to 11L).inOrder()
    }

    @Test
    fun launchExceptionsMapToReasons() {
        assertThat(launchFailureReason(ActivityNotFoundException())).isEqualTo(Reason.NOT_FOUND)
        assertThat(launchFailureReason(SecurityException())).isEqualTo(Reason.SECURITY)
        assertThat(launchFailureReason(IllegalStateException())).isEqualTo(Reason.PROFILE_UNAVAILABLE)
        assertThat(launchFailureReason(NullPointerException())).isEqualTo(Reason.UNKNOWN)
    }
}
