package io.github.aj4abinjacob.chikku.platform

import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic paths only. An explicit expectedPageSize distinguishes real16KB acceptance from4KB. */
@RunWith(AndroidJUnit4::class)
class NativePageSizeDeviceTest {
    @Test fun packagedGraphicsPathLibraryLoadsAndConicConversionRuns() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE)
        InstrumentationRegistry.getArguments().getString("expectedPageSize")?.let {
            assertThat(pageSize).isEqualTo(it.toLong())
        }
        val path = Path().apply {
            addOval(RectF(0f, 0f, 100f, 60f), Path.Direction.CW)
            moveTo(0f, 0f)
            cubicTo(10f, 30f, 60f, 10f, 80f, 40f)
        }
        // graphics-path1.0.1 initializes its JNI library even on API34+; converting conics
        // to quadratics also exercises that native implementation, rather than only launching UI.
        // This existing dependency is runtime-only in the app's compile graph. Reflection here
        // exercises that exact packaged version without adding a test or production dependency.
        val loader = instrumentation.targetContext.classLoader
        val type = Class.forName("androidx.graphics.path.PathIterator", true, loader)
        val evaluation = Class.forName("androidx.graphics.path.PathIterator\$ConicEvaluation", true, loader)
        val quadratics = evaluation.enumConstants!!.single { (it as Enum<*>).name == "AsQuadratics" }
        val iterator = type.getConstructor(Path::class.java, evaluation, Float::class.javaPrimitiveType)
            .newInstance(path, quadratics, 0.25f)
        val hasNext = type.getMethod("hasNext")
        val next = type.getMethod("next", FloatArray::class.java, Int::class.javaPrimitiveType)
        val points = FloatArray(8)
        var segments = 0
        while (hasNext.invoke(iterator) == true) {
            next.invoke(iterator, points, 0)
            assertThat(points.all(Float::isFinite)).isTrue()
            check(++segments < 100) { "Unexpected path iterator output" }
        }
        assertThat(segments).isGreaterThan(4)
        instrumentation.addResults(Bundle().apply {
            putLong("chikkuActualPageSize", pageSize)
            putBoolean("chikkuGraphicsPathConicConversionCompleted", true)
        })
    }
}
