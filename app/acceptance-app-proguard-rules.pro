# Acceptance application only. AndroidJUnitRunner lives in a separate APK and uses the shared
# app tracing dependency during bootstrap. Without this keep, app R8 removes Trace as unused by
# production entry points, while test R8 treats it as an app-provided library class. On API29 this
# crashed instrumentation before any test started. Normal production builds do not apply this rule.
-keep class androidx.tracing.Trace { *; }

# The separate AndroidX test APK and acceptance fixtures call Kotlin/coroutine runtime APIs
# that production entry points alone do not retain (first native failure: kotlin.LazyKt in
# AndroidJUnitRunner's FileTestStorage). App R8 does not use androidTest as reachability roots;
# test R8 treats these shared dependencies as library inputs. Preserve their binary contract
# only for this opt-in build. Other libraries remain optimized by R8.
# These APKs validate release-mode state/UI behavior, not production size or performance.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Native fixtures are a separately compiled consumer of Chikku's exposed API. Closed-world app
# shrinking removed ChikkuDatabaseFactory.INSTANCE even though the test APK still referenced it.
# Preserve the own-package public/protected binary contract, including Kotlin-generated bridges;
# method bodies and private implementation can still be optimized. This is acceptance-only and
# intentionally not equivalent to the normal production closed-world shrink configuration.
-keep,allowoptimization class io.github.aj4abinjacob.chikku.** { public protected *; }

# Fixtures inspect actual schema/column state through SQLite and close their Room handles.
# Native execution showed query(String) removed because Room's production queries use a different
# overload. Retain these storage API contracts for the separate consumer; keep body optimization.
-keep,allowoptimization class androidx.sqlite.db.** { public protected *; }
-keep,allowoptimization class androidx.room.RoomDatabase { public protected *; }

# Production construction can be fully inlined; native SAF fixtures also retrieve the actual
# Activity-scoped producer through this public provider API. Keep that acceptance-only ABI.
-keep,allowoptimization class androidx.lifecycle.ViewModelProvider { public protected *; }

# External-widget fixtures assert RESUMED state before observing a real drawn frame. These
# lifecycle APIs are separate-APK consumers too; closed-world R8 otherwise removes getCurrentState
# or unboxes State while test R8 still references that contract. Normal release builds omit this.
-keep,allowoptimization class androidx.lifecycle.Lifecycle { public protected *; }
-keep class androidx.lifecycle.Lifecycle$State { *; }
