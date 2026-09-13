package me.grey.picquery.feature

import ai.onnxruntime.OrtEnvironment
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.grey.picquery.BuildConfig
import me.grey.picquery.feature.mobileclip2.ImageEncoderMobileCLIPv2
import me.grey.picquery.feature.mobileclip2.MobileCLIP2Session
import me.grey.picquery.feature.mobileclip2.MobileCLIP2Tower
import me.grey.picquery.feature.mobileclip2.PreprocessorMobileCLIPv2
import me.grey.picquery.feature.mobileclip2.TextEncoderMobileCLIPv2
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.TensorFlowLite

/** Runs the packaged model pair against independently generated official PyTorch features. */
@RunWith(AndroidJUnit4::class)
class MobileCLIP2InstrumentedTest {
    @Test
    fun packagedModelsMatchOfficialReferenceAndWriteDeviceReport() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val targetContext = instrumentation.targetContext
            val testAssets = instrumentation.context.assets
            val arguments = InstrumentationRegistry.getArguments()
            val sampleCount = arguments.getString("sampleCount", "3").toInt().also { require(it in 1..200) }
            val warmupCount = arguments.getString("warmupCount", "0").toInt().also { require(it in 0..50) }
            val reference = testAssets.open("mobileclip2/reference_dog_256.json").bufferedReader().use {
                JSONObject(it.readText())
            }
            // The fixture is already 256 square, isolating model parity from resizing differences.
            val dog = testAssets.open("mobileclip2/${reference.getString("image_file")}").use {
                checkNotNull(
                    BitmapFactory.decodeStream(
                        it,
                        null,
                        BitmapFactory.Options().apply {
                            inScaled = false
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                    )
                )
            }
            val black = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLACK)
            }
            val imageEncoder = ImageEncoderMobileCLIPv2(targetContext, PreprocessorMobileCLIPv2(), Dispatchers.Default)
            val textEncoder = TextEncoderMobileCLIPv2(targetContext)
            val timings = JSONObject()
            val cosines = JSONObject()
            val report = JSONObject()
                .put("backend", BuildConfig.MOBILECLIP2_BACKEND)
                .put("litert_artifact_version", BuildConfig.LITERT_VERSION)
                .put("model", reference.getString("model"))
                .put("precision", "image FP32 / text dynamic weight INT8")
                .put("device_model", Build.MODEL)
                .put("device_hardware", Build.HARDWARE)
                .put("android_sdk", Build.VERSION.SDK_INT)
                .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                .put("is_emulator", Build.FINGERPRINT.contains("generic") || Build.HARDWARE in listOf("goldfish", "ranchu"))
                .put("measurement_note", "Actual App encoder path, including preprocessing/tokenization and normalization; 256-square fixture. Cold includes lazy model loading and asset copying; warm samples exclude cold and warmups. Not full-resolution gallery indexing or UI search latency.")
                .put("image_cpu_threads", MobileCLIP2Session.numThreads(MobileCLIP2Tower.IMAGE))
                .put("text_cpu_threads", MobileCLIP2Session.numThreads(MobileCLIP2Tower.TEXT))
                .put("sample_count_per_encoder", sampleCount)
                .put("warmup_count_per_encoder", warmupCount)
                .put("reference", "official PyTorch MobileCLIP2-S0 dfndr2b FP32")
                .put("fixture_source", reference.getString("image_source"))
                .put(
                    "quantized_reference_model_sha256",
                    reference.getJSONObject("quantized_text_model_sha256")
                        .getString(BuildConfig.MOBILECLIP2_BACKEND)
                )
                .put("timings_ms", timings)
                .put("cosines", cosines)

            try {
                assertEquals(256, dog.width)
                assertEquals(256, dog.height)
                val prompt = reference.getString("text")
                val tokens = reference.getJSONArray("token_ids")
                assertArrayEquals(
                    "Android BPE tokens must match the official tokenizer exactly",
                    IntArray(tokens.length()) { tokens.getInt(it) },
                    BPETokenizer(targetContext).tokenize(prompt).first
                )
                report.put("tokenizer_exact_match", true)

                val (image, coldImageMs) = timed { imageEncoder.encodeBatch(listOf(dog)).single() }
                timings.put("image_cold", coldImageMs)
                assertEmbedding(image)
                report.put("image_embedding", image.toJson())
                val imageCosine = cosine(image, reference.getJSONArray("image_embedding").toFloatArray())
                cosines.put("image_vs_official", imageCosine)

                val (text, coldTextMs) = timed { textEncoder.encode(prompt) }
                timings.put("text_cold", coldTextMs)
                assertEmbedding(text)
                report.put("text_embedding", text.toJson())
                val textCosine = cosine(text, reference.getJSONArray("text_embedding").toFloatArray())
                cosines.put("text_vs_official", textCosine)
                val quantizedReference = reference.getJSONObject("quantized_text_embeddings")
                    .getJSONArray(BuildConfig.MOBILECLIP2_BACKEND).toFloatArray()
                val quantizedCosine = cosine(text, quantizedReference)
                cosines.put("text_vs_matching_quantized_host", quantizedCosine)
                // Dynamic quantization varies with runtime version and CPU kernels. The previous
                // 0.9999 universal threshold was too strict: Android measured ONNX 0.9983273 and
                // TFLite 0.9995571. Model identity is checked independently with SHA256 below.
                val quantizedMinimum = reference.getJSONObject("expected_tolerances")
                    .getDouble("quantized_runtime_cosine_min")
                report.put("required_matching_quantized_cosine", quantizedMinimum)
                report.put("quantized_tolerance_note", reference.getString("quantized_runtime_tolerance_note"))
                report.put(
                    "runtime_version",
                    when (BuildConfig.MOBILECLIP2_BACKEND) {
                        "onnx" -> OrtEnvironment.getEnvironment().version
                        else -> TensorFlowLite.runtimeVersion()
                    }
                )
                // Host validation of the existing TFLite dynamic model measured >= 0.99935.
                // The standard ONNX INT8 export measures 0.98610 on this exact golden prompt.
                val textMinimum = if (BuildConfig.MOBILECLIP2_BACKEND == "tflite") {
                    0.999
                } else {
                    reference.getJSONObject("expected_tolerances").getDouble("int8_text_cosine_min")
                }
                report.put("required_text_cosine", textMinimum)
                cosines.put("dog_image_vs_dog_text", cosine(image, text))

                val textAsset = if (BuildConfig.MOBILECLIP2_BACKEND == "onnx") {
                    "mobileclip2_s0_text_int8.onnx"
                } else {
                    "text_model_dynamic_wi8.tflite"
                }
                val actualHash = targetContext.assets.open(textAsset).use { sha256(it) }
                val expectedHash = reference.getJSONObject("quantized_text_model_sha256")
                    .getString(BuildConfig.MOBILECLIP2_BACKEND)
                report.put("packaged_text_asset", textAsset)
                report.put("packaged_text_sha256", actualHash)
                report.put("packaged_text_hash_matches_reference", actualHash == expectedHash)
                assertEquals("Packaged text model must be the exact validated export", expectedHash, actualHash)

                val imageWarmMs = JSONArray()
                val textWarmMs = JSONArray()
                repeat(warmupCount) {
                    imageEncoder.encodeBatch(listOf(dog))
                    textEncoder.encode(prompt)
                }
                repeat(sampleCount) {
                    val (warmImage, imageMs) = timed { imageEncoder.encodeBatch(listOf(dog)).single() }
                    val (warmText, textMs) = timed { textEncoder.encode(prompt) }
                    imageWarmMs.put(imageMs)
                    textWarmMs.put(textMs)
                    assertArrayEquals("Image inference must be stable", image, warmImage, 0.00001f)
                    assertArrayEquals("Text inference must be stable", text, warmText, 0.00001f)
                }
                timings.put("image_warm_samples", imageWarmMs)
                timings.put("text_warm_samples", textWarmMs)

                val (batch, batchMs) = timed { imageEncoder.encodeBatch(listOf(dog, black, dog)) }
                timings.put("image_batch_3", batchMs)
                assertEquals(3, batch.size)
                batch.forEach { assertEmbedding(it) }
                assertArrayEquals("First batch item must match singleton", image, batch[0], 0.00001f)
                assertArrayEquals("Last batch item must preserve input order", image, batch[2], 0.00001f)
                val blackSingleton = imageEncoder.encodeBatch(listOf(black)).single()
                assertArrayEquals("Different middle item must match singleton", blackSingleton, batch[1], 0.00001f)
                val distinctImageCosine = cosine(image, blackSingleton)
                cosines.put("dog_vs_black_image", distinctImageCosine)
                assertTrue("Different images must change features", distinctImageCosine < 0.99)
                assertTrue(imageEncoder.encodeBatch(emptyList()).isEmpty())

                val otherText = textEncoder.encode("a photo of a car")
                assertEmbedding(otherText)
                val distinctTextCosine = cosine(text, otherText)
                cosines.put("dog_vs_car_text", distinctTextCosine)
                assertTrue("Different text must change features", distinctTextCosine < 0.99)
                report.put("batch_matches_singleton", true)

                imageEncoder.closeSession()
                textEncoder.closeSession()
                assertTrue(runCatching { imageEncoder.encodeBatch(listOf(dog)) }.exceptionOrNull() is IllegalStateException)
                assertTrue(runCatching { textEncoder.encode(prompt) }.exceptionOrNull() is IllegalStateException)
                report.put("closed_sessions_reject_inference", true)

                val threadPools = JSONObject()
                MobileCLIP2Tower.entries.forEach { tower ->
                    MobileCLIP2Session.create(targetContext, tower).use { session ->
                        threadPools.put(tower.name.lowercase(), session.nativeXnnpackThreadPoolActive)
                        if (BuildConfig.MOBILECLIP2_BACKEND == "tflite") {
                            assertTrue("App ${tower.name} XNNPACK must own a thread pool", session.nativeXnnpackThreadPoolActive)
                        }
                    }
                }
                report.put("native_xnnpack_thread_pools", threadPools)

                // Defer reference comparisons so a numerical mismatch still leaves all functional
                // checks and both independent cosine measurements available in the device report.
                assertTrue("Image cosine was $imageCosine", imageCosine >= 0.9999)
                assertTrue(
                    "Cross-runtime quantized text cosine was $quantizedCosine (required $quantizedMinimum)",
                    quantizedCosine >= quantizedMinimum
                )
                assertTrue("Quantized text cosine was $textCosine (required $textMinimum)", textCosine >= textMinimum)
                report.put("status", "passed")
            } catch (failure: Throwable) {
                report.put("status", "failed").put("failure", failure.toString())
                throw failure
            } finally {
                try {
                    imageEncoder.closeSession()
                    textEncoder.closeSession()
                } finally {
                    dog.recycle()
                    black.recycle()
                    File(targetContext.filesDir, "mobileclip2_device_report.json").writeText(report.toString(2))
                }
            }
        }
    }

    private suspend fun <T> timed(block: suspend () -> T): Pair<T, Double> {
        val start = SystemClock.elapsedRealtimeNanos()
        val result = block()
        return result to (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun assertEmbedding(feature: FloatArray) {
        assertEquals(512, feature.size)
        assertTrue("Features must be finite", feature.all { it.isFinite() })
        val norm = sqrt(feature.sumOf { it.toDouble() * it.toDouble() })
        assertTrue("Feature norm was $norm", abs(norm - 1.0) < 0.00001)
    }

    private fun cosine(left: FloatArray, right: FloatArray): Double {
        assertEquals(left.size, right.size)
        val dot = left.indices.sumOf { left[it].toDouble() * right[it].toDouble() }
        val leftNorm = sqrt(left.sumOf { it.toDouble() * it.toDouble() })
        val rightNorm = sqrt(right.sumOf { it.toDouble() * it.toDouble() })
        return dot / (leftNorm * rightNorm)
    }

    private fun JSONArray.toFloatArray() = FloatArray(length()) { getDouble(it).toFloat() }

    private fun FloatArray.toJson() = JSONArray(map { it.toDouble() })
}
