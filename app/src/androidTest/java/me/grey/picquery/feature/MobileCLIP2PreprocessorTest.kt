package me.grey.picquery.feature

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import me.grey.picquery.feature.mobileclip2.PreprocessorMobileCLIPv2
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileCLIP2PreprocessorTest {
    @Test
    fun preservesRgbNchwAndTiesToEvenCrop() = runBlocking {
        val preprocessor = PreprocessorMobileCLIPv2()
        // 257 -> offset 0; 259 -> offset 2. These cover both half-even directions.
        for ((width, height, left, top) in listOf(
            listOf(257, 256, 0, 0),
            listOf(259, 256, 2, 0),
            listOf(256, 259, 0, 2)
        )) {
            val source = patternBitmap(width, height)
            try {
                val actual = preprocessor.preprocess(source)
                assertTrue(actual.isDirect)
                assertEquals(ByteOrder.nativeOrder(), actual.order())
                assertEquals(0, actual.position())
                assertEquals(3 * 256 * 256, actual.remaining())
                val expected = FloatArray(actual.remaining())
                for (y in 0 until 256) {
                    for (x in 0 until 256) {
                        val index = y * 256 + x
                        expected[index] = ((x + left) % 256) / 255f
                        expected[index + 65536] = ((y + top) % 256) / 255f
                        expected[index + 131072] = ((x + left + y + top) % 256) / 255f
                    }
                }
                assertArrayEquals(expected, actual.copyValues(), 0f)
                assertFalse(source.isRecycled)
            } finally {
                source.recycle()
            }
        }
    }

    @Test
    fun retainsIndependentOutputsAndBatchOrder() = runBlocking {
        val preprocessor = PreprocessorMobileCLIPv2()
        val red = Bitmap.createBitmap(320, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val blue = Bitmap.createBitmap(512, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try {
            val first = preprocessor.preprocess(red)
            val firstSnapshot = first.copyValues()
            val second = preprocessor.preprocess(blue).copyValues()
            val batch = preprocessor.preprocessBatch(listOf(red, blue, red))
            assertEquals(0, batch.position())
            assertEquals(firstSnapshot.size * 3, batch.remaining())
            assertArrayEquals(firstSnapshot + second + firstSnapshot, batch.copyValues(), 0f)
            assertArrayEquals(firstSnapshot, first.copyValues(), 0f)
            assertEquals(1f, firstSnapshot[0], 0f)
            assertEquals(0f, second[0], 0f)
            assertEquals(1f, second[131072], 0f)
            assertEquals(0, preprocessor.preprocessBatch(emptyList()).remaining())
            assertFalse(red.isRecycled)
            assertFalse(blue.isRecycled)
        } finally {
            red.recycle()
            blue.recycle()
        }
    }

    @Test
    fun sharedPreprocessorIsSafeForConcurrentCallers() = runBlocking {
        val preprocessor = PreprocessorMobileCLIPv2()
        val inputs = (0..7).map { value ->
            Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(value * 30, 255 - value * 30, value * 20))
            }
        }
        try {
            val expected = inputs.map { preprocessor.preprocess(it).copyValues() }
            val actual = inputs.map { bitmap -> async(Dispatchers.Default) { preprocessor.preprocess(bitmap) } }.awaitAll()
            actual.forEachIndexed { index, buffer -> assertArrayEquals(expected[index], buffer.copyValues(), 0f) }
        } finally {
            inputs.forEach { it.recycle() }
        }
    }

    @Test
    fun recordsStandalonePreprocessingCost() = runBlocking {
        val preprocessor = PreprocessorMobileCLIPv2()
        val measurements = JSONObject()
        for ((width, height) in listOf(256 to 256, 1024 to 640)) {
            val bitmap = patternBitmap(width, height)
            try {
                repeat(20) { preprocessor.preprocess(bitmap) }
                val samples = JSONArray()
                repeat(100) {
                    val start = SystemClock.elapsedRealtimeNanos()
                    val output = preprocessor.preprocess(bitmap)
                    samples.put((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0)
                    assertEquals(196608, output.remaining())
                    assertTrue(output.get(12345).isFinite())
                }
                measurements.put("${width}x$height", samples)
            } finally {
                bitmap.recycle()
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "mobileclip2_preprocessing_report.json").writeText(
            JSONObject().put("device", Build.MODEL).put("warmup_count", 20).put("sample_count", 100)
                .put("note", "Standalone preprocess API, warm Debug instrumentation, no Activity or model inference. Bitmap creation excluded. Separate from App UI latency.")
                .put("samples_ms", measurements).toString(2)
        )
    }

    private fun FloatBuffer.copyValues() = FloatArray(remaining()).also { duplicate().get(it) }

    private fun patternBitmap(width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            Color.rgb(x % 256, y % 256, (x + y) % 256)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
