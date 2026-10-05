package io.github.iamandelib.cyberspace

import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Loads the live site, switches to the C64 theme, and checks that the screen actually
 * turns C64 blue — and stays blue after the app is relaunched.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ThemeSwitchTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val shots = File(instrumentation.targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }

    @Test
    fun c64ThemeRecolorsThePageAndPersists() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor(scenario, "!!findButton('C64')")
            Thread.sleep(2_000)
            val before = screenshot("01-default")
            assertTrue("default theme should not already be C64 blue", !isC64Blue(dominantColor(before)))

            js(scenario, "findButton('C64').click(); 'clicked'")
            waitFor(scenario, "document.documentElement.dataset.theme === 'c64'")
            Thread.sleep(2_000)
            val after = dominantColor(screenshot("02-c64"))
            assertTrue("page should turn C64 blue, was ${hex(after)}", isC64Blue(after))
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor(scenario, "!!findButton('C64')")
            Thread.sleep(2_000)
            assertEquals("\"c64\"", js(scenario, "document.documentElement.dataset.theme"))
            val relaunched = dominantColor(screenshot("03-c64-relaunched"))
            assertTrue("C64 theme should persist, was ${hex(relaunched)}", isC64Blue(relaunched))
        }
    }

    private fun js(scenario: ActivityScenario<MainActivity>, expression: String): String {
        val result = AtomicReference<String>()
        val done = CountDownLatch(1)
        val script = "(function(){ $HELPERS; return ($expression); })()"
        scenario.onActivity { activity ->
            activity.findViewById<WebView>(R.id.web).evaluateJavascript(script) {
                result.set(it)
                done.countDown()
            }
        }
        assertTrue("JavaScript did not return", done.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun waitFor(scenario: ActivityScenario<MainActivity>, condition: String, timeoutMs: Long = 120_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (runCatching { js(scenario, condition) }.getOrNull() == "true") return
            Thread.sleep(1_000)
        }
        screenshot("timeout")
        throw AssertionError("Timed out waiting for: $condition")
    }

    private fun screenshot(name: String): Bitmap {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(shots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    /** Most common colour on screen, sampled on a grid and bucketed to 8 levels per channel. */
    private fun dominantColor(bitmap: Bitmap): Int {
        val counts = HashMap<Int, Int>()
        val step = 8
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val c = bitmap.getPixel(x, y)
                val key = Color.rgb(Color.red(c) / 32 * 32, Color.green(c) / 32 * 32, Color.blue(c) / 32 * 32)
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return counts.maxByOrNull { it.value }!!.key
    }

    // C64 background is #2A2AB8; buckets land on (32, 32, 160).
    private fun isC64Blue(c: Int) =
        abs(Color.red(c) - 32) <= 32 && abs(Color.green(c) - 32) <= 32 && Color.blue(c) >= 128

    private fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

    companion object {
        private const val HELPERS =
            "var findButton = function (t) { return Array.from(document.querySelectorAll('button'))" +
                ".find(function (b) { return b.innerText.trim() === t; }); }"
    }
}
