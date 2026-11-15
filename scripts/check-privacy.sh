#!/usr/bin/env bash
# Privacy gate for Chikku Launcher (docs/PRIVACY_SECURITY.md).
#
# 1. Builds the merged debug manifest and fails if it declares or references a forbidden permission.
# 2. Resolves the app's runtime classpaths and fails if a known ads/analytics/crash-reporting/attribution
#    SDK is present.
#
# Usage: scripts/check-privacy.sh [extra Gradle arguments...]
# Works on macOS (bash 3.2) and Linux. Requires JAVA_HOME / java and local.properties or ANDROID_HOME.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLEW=("./gradlew" "--console=plain" "-q")
EXTRA_ARGS=("$@")

# Final merged manifests produced by AGP for the debug variant (verified with AGP 9.4.1).
MANIFESTS=(
    "app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"
    "app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
)

# Permissions the launcher must not hold. Optional features that later need one of these must
# change this list in the same reviewed PR, with a rationale (see AGENTS.md "Coding conventions").
#
# Exceptions documented in docs/PRIVACY_SECURITY.md:
# - android.permission.INTERNET: optional city weather (ADR-0006) and autocomplete requested by the user on 2026-10-10.
#   Only typed text goes to the selected provider while Search is visible and the switch is on.
#   Local app/contact/shortcut results remain on-device; no tracking SDK is allowed.
# Removed from this list for the G01–G22 feature set (user-approved, docs/PRIVACY_SECURITY.md):
# - android.permission.READ_CONTACTS: opt-in contact results in search. Requested at runtime only
#   when the user enables "Contacts" in Search settings; contacts are queried on-device per search
#   and never stored, logged or transmitted.
# - android.permission.BIND_ACCESSIBILITY_SERVICE: protects Chikku's own optional accessibility
#   service (it is declared on the <service>, not requested), used only for the "Lock screen" and
#   "Recents" gesture actions. The user enables it in system settings after choosing such an action;
#   it observes no window content.
# android.permission.EXPAND_STATUS_BAR (normal permission, for the "Expand notifications/quick
# settings" gesture actions) was never forbidden and needs no entry here.
FORBIDDEN_PERMISSIONS=(
    "android.permission.QUERY_ALL_PACKAGES"
    "android.permission.PACKAGE_USAGE_STATS"
    "android.permission.BIND_DEVICE_ADMIN"
    "com.google.android.gms.permission.AD_ID"
)

# Maven coordinate prefixes ("group" or "group:artifact") of ads, analytics, attribution,
# crash-reporting and push-marketing SDKs. Matched against the start of each resolved coordinate.
FORBIDDEN_DEPENDENCIES=(
    "com.google.firebase"
    "com.google.android.gms:play-services-ads"
    "com.google.android.gms:play-services-measurement"
    "com.google.android.gms:play-services-analytics"
    "com.google.android.gms:play-services-appset"
    "com.google.android.gms:play-services-ads-identifier"
    "com.google.android.gms:play-services-tagmanager"
    "com.google.ads"
    "com.crashlytics"
    "io.fabric"
    "io.sentry"
    "com.appsflyer"
    "com.adjust.sdk"
    "com.mixpanel"
    "com.amplitude"
    "com.segment"
    "com.facebook.android"
    "com.facebook.audience"
    "com.onesignal"
    "io.branch"
    "com.bugsnag"
    "com.flurry"
    "com.newrelic"
    "com.datadoghq"
    "com.instabug"
    "com.microsoft.appcenter"
    "com.heapanalytics"
    "io.embrace"
    "com.braze"
    "com.appboy"
    "com.clevertap"
    "com.kochava"
    "com.yandex.android"
    "io.appmetrica"
    "com.applovin"
    "com.unity3d.ads"
    "com.ironsource"
    "com.mopub"
    "com.chartboost"
    "com.inmobi"
    "com.vungle"
    "com.startapp"
    "com.smartlook"
    "com.posthog"
    "io.countly"
    "ly.count"
    "com.pushwoosh"
    "com.urbanairship"
    "com.airship"
)

CONFIGURATIONS=("debugRuntimeClasspath" "releaseRuntimeClasspath")

failures=0
fail() {
    printf '  FAIL  %s\n' "$1"
    failures=$((failures + 1))
}

echo "== Chikku privacy gate =="
echo
echo "-- Manifest permissions --"
"${GRADLEW[@]}" ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"} :app:processDebugManifest

checked_manifests=0
for manifest in "${MANIFESTS[@]}"; do
    if [[ ! -f "$manifest" ]]; then
        continue
    fi
    checked_manifests=$((checked_manifests + 1))
    echo "  manifest: $manifest"
    for permission in "${FORBIDDEN_PERMISSIONS[@]}"; do
        # Match the quoted attribute value so comments mentioning a permission do not trigger.
        if grep -Fq "\"$permission\"" "$manifest"; then
            fail "forbidden permission $permission in $manifest"
        fi
    done
done
if [[ "$checked_manifests" -eq 0 ]]; then
    fail "no merged debug manifest found; AGP output paths may have changed (update MANIFESTS in this script)"
else
    echo "  declared uses-permission entries (final merged manifest):"
    last_manifest=""
    for manifest in "${MANIFESTS[@]}"; do
        [[ -f "$manifest" ]] && last_manifest="$manifest"
    done
    declared="$(grep -o '<uses-permission[^>]*android:name="[^"]*"' "$last_manifest" \
        | sed 's/.*android:name="\([^"]*\)"/\1/' | sort -u || true)"
    if [[ -z "$declared" ]]; then
        echo "    (none)"
    else
        printf '    %s\n' $declared
    fi
fi

echo
echo "-- Runtime dependencies --"
dependency_report="$(mktemp "${TMPDIR:-/tmp}/chikku-deps.XXXXXX")"
trap 'rm -f "$dependency_report"' EXIT
for configuration in "${CONFIGURATIONS[@]}"; do
    "${GRADLEW[@]}" ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"} :app:dependencies --configuration "$configuration" \
        >>"$dependency_report"
done

# Extract "group:artifact" from tree lines such as "+--- androidx.core:core-ktx:1.19.1 -> 1.19.1 (*)".
coordinates="$(grep -E '^[| +\\-]+' "$dependency_report" \
    | sed -E 's/^[| +\\-]+//' \
    | awk '{print $1}' \
    | grep -E '^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+' \
    | awk -F: '{print $1 ":" $2}' \
    | sort -u || true)"
coordinate_count="$(printf '%s\n' "$coordinates" | grep -c . || true)"
echo "  resolved modules (${CONFIGURATIONS[*]}): $coordinate_count"
if [[ "$coordinate_count" -eq 0 ]]; then
    fail "no dependencies parsed from :app:dependencies output; the parser or Gradle output changed"
fi

for prefix in "${FORBIDDEN_DEPENDENCIES[@]}"; do
    # A group prefix matches "group:" or "group.sub:"; a group:artifact prefix matches that artifact family.
    if [[ "$prefix" == *:* ]]; then
        pattern="^$(printf '%s' "$prefix" | sed 's/\./\\./g')"
    else
        pattern="^$(printf '%s' "$prefix" | sed 's/\./\\./g')[.:]"
    fi
    matches="$(printf '%s\n' "$coordinates" | grep -E "$pattern" || true)"
    if [[ -n "$matches" ]]; then
        for match in $matches; do
            fail "forbidden SDK dependency $match (rule: $prefix)"
        done
    fi
done

echo
if [[ "$failures" -gt 0 ]]; then
    echo "Privacy gate FAILED with $failures problem(s)."
    exit 1
fi
echo "Privacy gate passed: ${#FORBIDDEN_PERMISSIONS[@]} forbidden permissions absent from $checked_manifests manifest(s);" \
    "${#FORBIDDEN_DEPENDENCIES[@]} SDK rules checked against $coordinate_count resolved modules."
