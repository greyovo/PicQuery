package me.grey.picquery.feature

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.TensorFlowLite

/** External fixtures are prepared by the host; this test never navigates or changes app UI. */
@RunWith(AndroidJUnit4::class)
class MobileCLIPVersionsBenchmarkTest {
    @Test
    fun benchmarkModel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Pass modelId to opt into the external-fixture benchmark.", arguments.getString("modelId") != null)
        val modelId = requireNotNull(arguments.getString("modelId")) { "Pass instrumentation modelId." }
        val roundId = arguments.getString("roundId") ?: "1"
        require(modelId.matches(Regex("[A-Za-z0-9_-]+")) && roundId.matches(Regex("[A-Za-z0-9_-]+")))
        val sampleCount = (arguments.getString("sampleCount") ?: "100").toInt()
        val warmupCount = (arguments.getString("warmupCount") ?: "10").toInt()
        val sustainSeconds = (arguments.getString("sustainSeconds") ?: "45").toInt()
        val cpuThreads = (arguments.getString("cpuThreads") ?: "4").toInt()
        val xnnpack = arguments.getString("xnnpack") ?: "default"
        require(sampleCount > 0 && warmupCount >= 0 && sustainSeconds >= 0)
        require(cpuThreads in 1..8) { "cpuThreads must be between 1 and 8." }
        require(xnnpack in listOf("default", "true", "false", "native")) {
            "xnnpack must be default, true, false, or native."
        }
        val reportFile = File(context.filesDir, "mobileclip-benchmark-$modelId-$roundId.json")
        val health = JSONArray()
        val report = JSONObject()
            .put("model_id", modelId).put("round_id", roundId)
            .put("sample_count", sampleCount).put("warmup_count", warmupCount)
            .put("sustain_seconds", sustainSeconds).put("cpu_threads", cpuThreads)
            .put("requested_xnnpack", xnnpack)
            .put("device", Build.MODEL).put("manufacturer", Build.MANUFACTURER)
            .put("sdk", Build.VERSION.SDK_INT).put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("fingerprint", Build.FINGERPRINT).put("hardware", Build.HARDWARE)
            .put("soc", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "unavailable")
            .put("timing_scope", "Preloaded input copy + runtime invocation + output copy; excludes preprocessing, tokenization, SHA256 and output validation.")
            .put("input_preparation_note", "Identical preloaded fixtures cycle in order. INT32-to-INT64 token conversion, if required by model metadata, happens before timing.")
            .put("load_note", "Session load follows SHA256 verification; filesystem cache may be warm. First invocation is separate from warm samples.")
            .put("memory_note", "PSS is sampled at phase boundaries and about every 10 seconds during sustained inference; it is not a measured peak.")
            .put("health_samples", health)
        try {
            health.put(healthSample(context, "before"))
            val root = File(context.filesDir, "mobileclip-benchmark").canonicalFile
            val manifest = JSONObject(File(root, "manifest.json").readText())
            val models = manifest.getJSONArray("models")
            val matchingModels = (0 until models.length()).map { models.getJSONObject(it) }
                .filter { it.getString("id") == modelId }
            require(matchingModels.size == 1) { "Manifest must contain exactly one model named $modelId." }
            val model = matchingModels.single()
            val backend = model.getString("backend")
            require(backend == "onnx" || backend == "tflite")
            require(xnnpack != "native" || backend == "tflite") { "The native XNNPACK probe requires TFLite." }
            val probeLibrary = File(root, "libpicquery_xnnpack_probe.so")
            if (xnnpack == "native") report.put("native_xnnpack_probe_library", probeLibrary.absolutePath)
            val size = model.getInt("input_size")
            require(size in 1..1024)
            report.put("backend", backend).put("precision", model.optString("precision", "unspecified"))
                .put("input_size", size)
            val imageFile = externalFile(root, model.getString("image_file"))
            val textFile = externalFile(root, model.getString("text_file"))
            val imageHash = sha256(imageFile)
            val textHash = sha256(textFile)
            report.put("actual_image_sha256", imageHash).put("actual_text_sha256", textHash)
            report.put("image_file_bytes", imageFile.length()).put("text_file_bytes", textFile.length())
            require(imageHash.equals(model.getString("image_sha256"), ignoreCase = true)) { "Image model SHA256 mismatch." }
            require(textHash.equals(model.getString("text_sha256"), ignoreCase = true)) { "Text model SHA256 mismatch." }
            report.put("model_hashes_verified", true)

            val imageInputFile = externalFile(root, model.getString("image_input_file"))
            val tokensFile = externalFile(root, manifest.getString("tokens_file"))
            val images = preload(imageInputFile, model.getInt("image_count"), 3 * size * size * 4)
            val tokens = preload(tokensFile, manifest.getInt("token_count"), 77 * 4)
            val imageInputHash = sha256(imageInputFile)
            val tokensHash = sha256(tokensFile)
            report.put("image_input_sha256", imageInputHash).put("tokens_sha256", tokensHash)
            require(imageInputHash.equals(model.getString("image_input_sha256"), ignoreCase = true)) {
                "Preprocessed image input SHA256 mismatch."
            }
            require(tokensHash.equals(manifest.getString("tokens_sha256"), ignoreCase = true)) {
                "Token input SHA256 mismatch."
            }
            report.put("input_hashes_verified", true)
            report.put("preloaded_image_count", images.size).put("preloaded_text_count", tokens.size)

            // Each tower closes before the next opens; no image/text model pair remains resident.
            benchmarkTower(context, imageFile, backend, true, size, images, sampleCount, warmupCount, cpuThreads, xnnpack, probeLibrary, report, health, "image")
            health.put(healthSample(context, "after_image_samples"))
            benchmarkTower(context, textFile, backend, false, size, tokens, sampleCount, warmupCount, cpuThreads, xnnpack, probeLibrary, report, health, "text")
            health.put(healthSample(context, "after_text_samples"))

            if (sustainSeconds > 0) {
                val loadStart = SystemClock.elapsedRealtimeNanos()
                BenchmarkSession(imageFile, backend, true, size, cpuThreads, xnnpack, probeLibrary).use { session ->
                    val sustained = JSONObject().put("reload_ms", elapsedMs(loadStart))
                        .put("native_xnnpack_has_thread_pool", session.nativeXnnpackHasThreadPool ?: JSONObject.NULL)
                    report.put("sustained_image", sustained)
                    val prepared = session.prepareInputs(images)
                    val firstStart = SystemClock.elapsedRealtimeNanos()
                    session.invoke(prepared.first())
                    sustained.put("first_invoke_after_reload_ms", elapsedMs(firstStart))
                    session.validateOutput()
                    repeat(warmupCount) {
                        session.invoke(prepared[it % prepared.size])
                        session.validateOutput()
                    }
                    health.put(healthSample(context, "before_sustained_image"))
                    sustain(context, session, prepared, sustainSeconds, sustained, health)
                }
            } else {
                report.put("sustained_image", JSONObject().put("skipped", true))
            }
            report.put("status", "passed")
        } catch (failure: Throwable) {
            report.put("status", "failed").put("failure", failure.toString())
            throw failure
        } finally {
            health.put(healthSample(context, "after"))
            reportFile.writeText(report.toString(2))
        }
    }

    @Test
    fun exportAccuracyFeatures() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Pass modelId to opt into the external-fixture accuracy export.", arguments.getString("modelId") != null)
        val modelId = requireNotNull(arguments.getString("modelId")) { "Pass instrumentation modelId." }
        require(modelId.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid modelId." }
        val filesRoot = context.filesDir.canonicalFile
        val inputsRoot = File(filesRoot, "mobileclip-accuracy").canonicalFile
        val modelsRoot = File(filesRoot, "mobileclip-benchmark").canonicalFile
        val outputRoot = File(filesRoot, "mobileclip-accuracy-output").canonicalFile
        require(listOf(inputsRoot, modelsRoot, outputRoot).all { it.parentFile == filesRoot }) {
            "Accuracy directories must stay directly inside app filesDir."
        }
        check(outputRoot.isDirectory || outputRoot.mkdirs()) { "Cannot create accuracy output directory." }
        val reportFile = File(outputRoot, "report.json")
        val report = JSONObject()
            .put("status", "running").put("model_id", modelId)
            .put("cpu_threads", 4).put("inter_op_threads", 1)
            .put("device", Build.MODEL).put("manufacturer", Build.MANUFACTURER)
            .put("sdk", Build.VERSION.SDK_INT).put("fingerprint", Build.FINGERPRINT)
            .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("output_format", "Raw little-endian float32 [count,512], manifest order; no additional normalization.")
            .put("timing_scope", "Dataset loops include streaming reads, input preparation, inference, validation and output writes; exclude model loading and SHA256. These are accuracy exports, not latency benchmarks.")
            .put(
                "session_configuration",
                JSONObject()
                    .put("execution_provider", "CPUExecutionProvider")
                    .put("intra_op_threads", 4).put("inter_op_threads", 1)
                    .put("execution_mode", "ORT_SEQUENTIAL (runtime default)")
                    .put("graph_optimization", "runtime default")
                    .put("tower_lifetime", "all image datasets, close; all text datasets, close")
            )
        val started = SystemClock.elapsedRealtimeNanos()
        reportFile.writeText(report.toString(2))
        try {
            require(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) { "Accuracy fixtures require a little-endian device." }
            val runtimeVersion = OrtEnvironment.getEnvironment().version
            report.put("runtime_version", runtimeVersion)
            require(runtimeVersion == "1.29.0") { "Accuracy export requires ORT 1.29.0, got $runtimeVersion." }
            val manifestFile = accuracyFile(inputsRoot, "manifest.json")
            val manifest = JSONObject(manifestFile.readText())
            report.put("manifest_sha256", sha256(manifestFile))
            report.put("evaluation_manifest_sha256", manifest.opt("evaluation_manifest_sha256") ?: JSONObject.NULL)
            val models = manifest.getJSONArray("models")
            val matching = (0 until models.length()).map { models.getJSONObject(it) }
                .filter { it.getString("id") == modelId }
            require(matching.size == 1) { "Manifest must contain exactly one model named $modelId." }
            val model = matching.single()
            require(model.optString("backend", "onnx") == "onnx") { "Accuracy export supports ONNX/ORT CPU models only." }
            report.put("model", model)
            val sourceRows = manifest.getJSONArray("datasets")
            val datasets = (0 until sourceRows.length()).map { JSONObject(sourceRows.getJSONObject(it).toString()) }
            val datasetIds = datasets.map { it.getString("id") }
            require(datasets.isNotEmpty() && datasetIds.toSet().size == datasets.size) { "Dataset IDs must be unique and nonempty." }
            require(datasetIds.all { it.matches(Regex("[A-Za-z0-9_-]+")) }) { "Invalid dataset ID." }
            report.put("datasets", JSONArray(datasets))
            val validationStart = SystemClock.elapsedRealtimeNanos()
            val imageModel = verifyAccuracyFile(modelsRoot, model, "image_file", "image_sha256")
            val textModel = verifyAccuracyFile(modelsRoot, model, "text_file", "text_sha256")
            report.put("actual_image_sha256", model.getString("image_sha256"))
                .put("actual_text_sha256", model.getString("text_sha256"))
            datasets.forEach { dataset ->
                val imageCount = dataset.getInt("image_count")
                val tokenCount = dataset.getInt("token_count")
                require(imageCount > 0 && tokenCount > 0) { "Accuracy input counts must be positive." }
                verifyAccuracyFile(inputsRoot, dataset, "image_file", "image_sha256", imageCount.toLong() * 3 * 256 * 256 * 4)
                verifyAccuracyFile(inputsRoot, dataset, "tokens_file", "tokens_sha256", tokenCount.toLong() * 77 * 4)
            }
            report.put("model_hashes_verified", true).put("input_hashes_verified", true)
                .put("input_validation_ms", elapsedMs(validationStart))
            reportFile.writeText(report.toString(2))
            val unusedProbe = File(modelsRoot, "libpicquery_xnnpack_probe.so")
            for ((tower, modelFile) in listOf("image" to imageModel, "text" to textModel)) {
                val image = tower == "image"
                val loadStart = SystemClock.elapsedRealtimeNanos()
                BenchmarkSession(modelFile, "onnx", image, 256, 4, "default", unusedProbe).use { session ->
                    report.put(
                        "${tower}_session",
                        JSONObject().put("load_ms", elapsedMs(loadStart))
                            .put("runtime_version", session.runtimeVersion).put("tensor_contract", session.contract)
                    )
                    datasets.forEach { dataset ->
                        val input = accuracyFile(inputsRoot, dataset.getString(if (image) "image_file" else "tokens_file"))
                        val count = dataset.getInt(if (image) "image_count" else "token_count")
                        exportAccuracyDataset(session, input, outputRoot, modelId, dataset, tower, count)
                        reportFile.writeText(report.toString(2))
                    }
                }
            }
            report.put("status", "passed")
        } catch (failure: Throwable) {
            report.put("status", "failed").put("failure", failure.toString())
            throw failure
        } finally {
            report.put("total_elapsed_ms", elapsedMs(started))
            reportFile.writeText(report.toString(2))
        }
    }

    private fun accuracyFile(root: File, name: String): File {
        require(name.matches(Regex("[A-Za-z0-9_.-]+"))) { "Expected a fixture basename: $name" }
        return externalFile(root, name)
    }

    private fun verifyAccuracyFile(
        root: File,
        row: JSONObject,
        fileKey: String,
        hashKey: String,
        expectedBytes: Long? = null
    ): File {
        val file = accuracyFile(root, row.getString(fileKey))
        if (expectedBytes != null) require(file.length() == expectedBytes) { "Unexpected input length: ${file.name}." }
        val expectedHash = row.getString(hashKey)
        require(expectedHash.matches(Regex("[A-Fa-f0-9]{64}"))) { "Invalid SHA256 for ${file.name}." }
        val actualHash = sha256(file)
        require(actualHash.equals(expectedHash, ignoreCase = true)) { "SHA256 mismatch: ${file.name}." }
        row.put(hashKey, actualHash)
        return file
    }

    private fun exportAccuracyDataset(
        session: BenchmarkSession,
        inputFile: File,
        outputRoot: File,
        modelId: String,
        dataset: JSONObject,
        tower: String,
        count: Int
    ) {
        val datasetId = dataset.getString("id")
        val bytesPerInput = if (tower == "image") 3 * 256 * 256 * 4 else 77 * 4
        val bytes = ByteArray(bytesPerInput)
        val inputBuffer = ByteBuffer.allocateDirect(bytesPerInput).order(ByteOrder.LITTLE_ENDIAN)
        val singleInput = listOf(inputBuffer)
        val outputBytes = ByteArray(512 * 4)
        val outputValues = ByteBuffer.wrap(outputBytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val outputFile = File(outputRoot, "$modelId-$datasetId-$tower.f32").canonicalFile
        val temporaryFile = File(outputRoot, "${outputFile.name}.partial").canonicalFile
        require(outputFile.parentFile == outputRoot && temporaryFile.parentFile == outputRoot) { "Invalid output path." }
        val result = JSONObject().put("status", "running").put("file", outputFile.name)
            .put("count", count).put("shape", JSONArray(listOf(count, 512))).put("completed_count", 0)
        dataset.put(tower, result)
        var published = false
        try {
            val started = SystemClock.elapsedRealtimeNanos()
            BufferedInputStream(inputFile.inputStream(), 64 * 1024).use { input ->
                BufferedOutputStream(temporaryFile.outputStream(), 64 * 1024).use { output ->
                    repeat(count) { index ->
                        var offset = 0
                        while (offset < bytes.size) {
                            val read = input.read(bytes, offset, bytes.size - offset)
                            check(read > 0) { "Truncated input ${inputFile.name} at sample $index." }
                            offset += read
                        }
                        inputBuffer.clear()
                        inputBuffer.put(bytes).rewind()
                        val prepared = session.prepareInputs(singleInput).single()
                        session.invoke(prepared)
                        session.validateOutput()
                        outputValues.clear()
                        session.copyOutputTo(outputValues)
                        output.write(outputBytes)
                        result.put("completed_count", index + 1)
                        if (tower == "image" && (index + 1) % 250 == 0) {
                            Log.i("MobileCLIPAccuracy", "$modelId/$datasetId/$tower: ${index + 1}/$count")
                        }
                    }
                    check(input.read() == -1) { "Unexpected trailing input data: ${inputFile.name}." }
                }
            }
            result.put("elapsed_ms", elapsedMs(started))
            check(temporaryFile.length() == count.toLong() * 512 * 4) { "Incomplete feature output." }
            val outputHash = sha256(temporaryFile)
            Files.move(temporaryFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            published = true
            result.put("status", "passed").put("sha256", outputHash).put("bytes", outputFile.length())
        } catch (failure: Throwable) {
            result.put("status", "failed").put("failure", failure.toString())
            throw failure
        } finally {
            if (!published) temporaryFile.delete()
        }
    }

    private fun benchmarkTower(
        context: Context,
        file: File,
        backend: String,
        image: Boolean,
        size: Int,
        inputs: List<ByteBuffer>,
        sampleCount: Int,
        warmupCount: Int,
        cpuThreads: Int,
        xnnpack: String,
        probeLibrary: File,
        report: JSONObject,
        health: JSONArray,
        name: String
    ) {
        val result = JSONObject()
        report.put(name, result)
        val loadStart = SystemClock.elapsedRealtimeNanos()
        BenchmarkSession(file, backend, image, size, cpuThreads, xnnpack, probeLibrary).use { session ->
            result.put("load_ms", elapsedMs(loadStart)).put("tensor_contract", session.contract)
                .put("native_xnnpack_has_thread_pool", session.nativeXnnpackHasThreadPool ?: JSONObject.NULL)
            report.put("runtime_version", session.runtimeVersion)
            health.put(healthSample(context, "${name}_loaded"))
            val prepared = session.prepareInputs(inputs)
            val firstStart = SystemClock.elapsedRealtimeNanos()
            session.invoke(prepared.first())
            result.put("first_invoke_ms", elapsedMs(firstStart))
            session.validateOutput()
            result.put("first_output", session.outputSnapshot())
            repeat(warmupCount) {
                session.invoke(prepared[it % prepared.size])
                session.validateOutput()
            }
            val samples = ArrayList<Double>(sampleCount)
            val sampleLoopCpuStart = android.os.Process.getElapsedCpuTime()
            val sampleLoopWallStart = SystemClock.elapsedRealtimeNanos()
            repeat(sampleCount) {
                val start = SystemClock.elapsedRealtimeNanos()
                session.invoke(prepared[it % prepared.size])
                samples.add(elapsedMs(start))
                session.validateOutput()
            }
            val sampleLoopWallMs = elapsedMs(sampleLoopWallStart)
            val sampleLoopCpuMs = android.os.Process.getElapsedCpuTime() - sampleLoopCpuStart
            result.put("raw_samples_ms", JSONArray(samples)).put("summary", statistics(samples))
                .put("validated_output_count", 1 + warmupCount + sampleCount)
                .put("process_cpu_ms", sampleLoopCpuMs).put("sample_loop_wall_ms", sampleLoopWallMs)
                .put("cpu_parallelism", sampleLoopCpuMs / sampleLoopWallMs)
            health.put(healthSample(context, "${name}_samples_complete_before_close"))
        }
    }

    private fun sustain(
        context: Context,
        session: BenchmarkSession,
        inputs: List<ByteBuffer>,
        seconds: Int,
        result: JSONObject,
        health: JSONArray
    ) {
        val segments = JSONArray()
        result.put("segments", segments)
        val start = SystemClock.elapsedRealtimeNanos()
        var segmentStart = start
        var nextSegmentSeconds = 15.0
        var nextHealthSeconds = 10.0
        var totalCount = 0
        val samples = ArrayList<Double>()
        while ((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000_000.0 < seconds) {
            val invokeStart = SystemClock.elapsedRealtimeNanos()
            session.invoke(inputs[totalCount % inputs.size])
            samples.add(elapsedMs(invokeStart))
            session.validateOutput()
            totalCount++
            val now = SystemClock.elapsedRealtimeNanos()
            val elapsed = (now - start) / 1_000_000_000.0
            if (elapsed >= nextHealthSeconds) {
                health.put(healthSample(context, "sustained_image").put("sustain_elapsed_s", elapsed))
                nextHealthSeconds += 10.0
            }
            if (elapsed >= nextSegmentSeconds) {
                segments.put(segment(samples, start, segmentStart, now))
                samples.clear()
                segmentStart = now
                nextSegmentSeconds += 15.0
            }
        }
        val end = SystemClock.elapsedRealtimeNanos()
        if (samples.isNotEmpty()) segments.put(segment(samples, start, segmentStart, end))
        val elapsedSeconds = (end - start) / 1_000_000_000.0
        result.put("actual_elapsed_s", elapsedSeconds).put("count", totalCount)
            .put("images_per_second", totalCount / elapsedSeconds)
        health.put(healthSample(context, "after_sustained_image"))
    }

    private fun segment(samples: List<Double>, start: Long, segmentStart: Long, end: Long): JSONObject {
        val seconds = (end - segmentStart) / 1_000_000_000.0
        return statistics(samples).put("from_s", (segmentStart - start) / 1_000_000_000.0)
            .put("to_s", (end - start) / 1_000_000_000.0)
            .put("elapsed_s", seconds).put("images_per_second", samples.size / seconds)
    }

    private fun statistics(samples: List<Double>): JSONObject {
        require(samples.isNotEmpty())
        val sorted = samples.sorted()
        fun percentile(fraction: Double) = sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceAtLeast(0)]
        return JSONObject().put("count", samples.size).put("mean_ms", samples.average())
            .put("min_ms", sorted.first()).put("max_ms", sorted.last())
            .put("p50_ms", percentile(0.5)).put("p95_ms", percentile(0.95)).put("p99_ms", percentile(0.99))
    }

    private fun healthSample(context: Context, phase: String): JSONObject {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        return JSONObject().put("phase", phase).put("elapsed_realtime_ms", SystemClock.elapsedRealtime())
            .put("battery_temperature_c", if (temperature == null || temperature == Int.MIN_VALUE) JSONObject.NULL else temperature / 10.0)
            .put("battery_plugged", battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1))
            .put("thermal_status", context.getSystemService(PowerManager::class.java).currentThermalStatus)
            .put("sampled_total_pss_kb", memory.totalPss)
            .put("sampled_native_pss_kb", memory.nativePss).put("sampled_dalvik_pss_kb", memory.dalvikPss)
    }

    private fun externalFile(root: File, relative: String): File {
        val file = File(root, relative).canonicalFile
        require(file.path.startsWith(root.path + File.separator) && file.isFile) { "Missing or invalid fixture: $relative" }
        return file
    }

    private fun preload(file: File, count: Int, bytesPerInput: Int): List<ByteBuffer> {
        require(count > 0 && file.length() == count.toLong() * bytesPerInput) { "Unexpected input file length: ${file.name}" }
        require(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) { "Benchmark fixtures require little-endian device buffers." }
        file.inputStream().use { input ->
            val bytes = ByteArray(bytesPerInput)
            return List(count) {
                var offset = 0
                while (offset < bytes.size) {
                    val read = input.read(bytes, offset, bytes.size - offset)
                    check(read > 0) { "Truncated input: ${file.name}" }
                    offset += read
                }
                ByteBuffer.allocateDirect(bytesPerInput).order(ByteOrder.nativeOrder()).apply {
                    put(bytes)
                    rewind()
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(bytes)
                if (read < 0) break
                digest.update(bytes, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun elapsedMs(start: Long) = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0

    private class BenchmarkSession(
        file: File,
        backend: String,
        image: Boolean,
        size: Int,
        cpuThreads: Int,
        xnnpack: String,
        probeLibrary: File
    ) : Closeable {
        private val environment = if (backend == "onnx") OrtEnvironment.getEnvironment() else null
        private var onnx: OrtSession? = null
        private var tflite: Interpreter? = null
        private var nativeDelegate: NativeXnnpackDelegate? = null
        private val shape = if (image) longArrayOf(1, 3, size.toLong(), size.toLong()) else longArrayOf(1, 77)
        private val type: OnnxJavaType
        private val inputName: String
        private val staging: ByteBuffer
        private val inputView: Buffer
        private val outputBuffer = ByteBuffer.allocateDirect(512 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        private val output = FloatArray(512)
        val contract: JSONObject
        val runtimeVersion: String
        val nativeXnnpackHasThreadPool: Boolean? get() = nativeDelegate?.hasThreadPool()

        init {
            try {
                val outputShape: LongArray
                if (environment != null) {
                    val session = OrtSession.SessionOptions().use { options ->
                        options.setIntraOpNumThreads(cpuThreads)
                        options.setInterOpNumThreads(1)
                        environment.createSession(file.absolutePath, options)
                    }
                    onnx = session
                    require(session.numInputs == 1L && session.numOutputs == 1L)
                    inputName = session.inputNames.single()
                    val input = session.inputInfo.getValue(inputName).info as TensorInfo
                    require(matches(input.shape, shape)) { "Unexpected ONNX input shape: ${input.shape.contentToString()}" }
                    type = input.type
                    val result = session.outputInfo.values.single().info as TensorInfo
                    require(result.type == OnnxJavaType.FLOAT)
                    outputShape = result.shape
                    runtimeVersion = environment.version
                } else {
                    val options = Interpreter.Options().setNumThreads(cpuThreads).apply {
                        when (xnnpack) {
                            "native" -> {
                                val probe = NativeXnnpackDelegate(probeLibrary, cpuThreads)
                                nativeDelegate = probe
                                setUseXNNPACK(false)
                                addDelegate(probe)
                            }

                            "true", "false" -> setUseXNNPACK(xnnpack == "true")
                        }
                    }
                    val interpreter = Interpreter(file, options)
                    tflite = interpreter
                    require(interpreter.inputTensorCount == 1 && interpreter.outputTensorCount == 1)
                    inputName = interpreter.getInputTensor(0).name()
                    val input = interpreter.getInputTensor(0)
                    require(matches(input.shape().map { it.toLong() }.toLongArray(), shape)) { "Unexpected TFLite input shape." }
                    type = when (input.dataType()) {
                        DataType.FLOAT32 -> OnnxJavaType.FLOAT
                        DataType.INT32 -> OnnxJavaType.INT32
                        DataType.INT64 -> OnnxJavaType.INT64
                        else -> error("Unsupported TFLite input type: ${input.dataType()}")
                    }
                    val result = interpreter.getOutputTensor(0)
                    require(result.dataType() == DataType.FLOAT32)
                    outputShape = result.shape().map { it.toLong() }.toLongArray()
                    runtimeVersion = TensorFlowLite.runtimeVersion()
                }
                require(if (image) type == OnnxJavaType.FLOAT else type == OnnxJavaType.INT32 || type == OnnxJavaType.INT64)
                require(matches(outputShape, longArrayOf(1, 512)) || matches(outputShape, longArrayOf(512))) {
                    "Expected 512 output features, got ${outputShape.contentToString()}"
                }
                val elements = shape.fold(1L) { total, dimension -> total * dimension }.toInt()
                staging = ByteBuffer.allocateDirect(elements * if (type == OnnxJavaType.INT64) 8 else 4)
                    .order(ByteOrder.nativeOrder())
                inputView = when (type) {
                    OnnxJavaType.FLOAT -> staging.asFloatBuffer()
                    OnnxJavaType.INT32 -> staging.asIntBuffer()
                    else -> staging.asLongBuffer()
                }
                contract = JSONObject().put("input_name", inputName).put("input_type", type.name)
                    .put("input_shape", JSONArray(shape.toList())).put("output_shape", JSONArray(outputShape.toList()))
                    .put("output_type", "FLOAT32")
            } catch (failure: Throwable) {
                try {
                    close()
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
                throw failure
            }
        }

        fun prepareInputs(inputs: List<ByteBuffer>): List<ByteBuffer> {
            val prepared = if (type == OnnxJavaType.INT64) {
                inputs.map { source ->
                    val integers = source.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer()
                    ByteBuffer.allocateDirect(integers.remaining() * 8).order(ByteOrder.nativeOrder()).apply {
                        while (integers.hasRemaining()) putLong(integers.get().toLong())
                        rewind()
                    }
                }
            } else {
                inputs
            }
            require(prepared.all { it.capacity() == staging.capacity() }) { "Preloaded input size does not match model." }
            return prepared
        }

        fun invoke(input: ByteBuffer) {
            input.rewind()
            staging.clear()
            staging.put(input)
            staging.rewind()
            inputView.rewind()
            val session = onnx
            if (session != null) {
                val tensor = when (type) {
                    OnnxJavaType.FLOAT -> OnnxTensor.createTensor(environment, inputView as java.nio.FloatBuffer, shape)
                    OnnxJavaType.INT32 -> OnnxTensor.createTensor(environment, inputView as java.nio.IntBuffer, shape)
                    else -> OnnxTensor.createTensor(environment, inputView as java.nio.LongBuffer, shape)
                }
                tensor.use {
                    session.run(mapOf(inputName to tensor)).use { result ->
                        val buffer = (result.get(0) as OnnxTensor).floatBuffer
                        check(buffer.remaining() == output.size) { "Runtime output must contain 512 values." }
                        buffer.get(output)
                    }
                }
            } else {
                outputBuffer.clear()
                checkNotNull(tflite).run(inputView, outputBuffer)
                outputBuffer.rewind()
                outputBuffer.get(output)
            }
        }

        fun validateOutput() {
            check(output.all { it.isFinite() } && output.any { it != 0f }) { "Expected 512 finite, nonzero features." }
        }

        fun outputSnapshot(): JSONArray = JSONArray(output.map { it.toDouble() })

        fun copyOutputTo(destination: FloatBuffer) {
            require(destination.remaining() == output.size) { "Expected space for 512 output features." }
            destination.put(output)
        }

        override fun close() {
            val onnxSession = onnx
            val interpreter = tflite
            val delegate = nativeDelegate
            onnx = null
            tflite = null
            nativeDelegate = null
            try {
                onnxSession?.close()
            } finally {
                try {
                    interpreter?.close()
                } finally {
                    // The interpreter borrows the delegate; destroy it only after interpreter teardown.
                    delegate?.close()
                }
            }
        }

        private fun matches(actual: LongArray, expected: LongArray): Boolean = actual.size == expected.size && actual.indices.all { actual[it] <= 0L || actual[it] == expected[it] }
    }

    /** Diagnostic only: a host-built JNI probe reuses the app's existing LiteRT native library. */
    private class NativeXnnpackDelegate(libraryFile: File, cpuThreads: Int) :
        Delegate,
        Closeable {
        private var handle = 0L

        init {
            loadProbe(libraryFile)
            handle = nativeCreate(cpuThreads)
            check(handle != 0L) { "Native XNNPACK delegate creation returned a null handle." }
        }

        @Synchronized
        override fun getNativeHandle(): Long {
            check(handle != 0L) { "Native XNNPACK delegate is closed." }
            return handle
        }

        @Synchronized
        fun hasThreadPool(): Boolean {
            check(handle != 0L) { "Native XNNPACK delegate is closed." }
            return nativeHasThreadPool(handle)
        }

        @Synchronized
        override fun close() {
            val activeHandle = handle
            handle = 0L
            if (activeHandle != 0L) nativeDelete(activeHandle)
        }

        private external fun nativeCreate(cpuThreads: Int): Long
        private external fun nativeHasThreadPool(delegateHandle: Long): Boolean
        private external fun nativeDelete(delegateHandle: Long)

        companion object {
            private var loadedPath: String? = null

            @Synchronized
            private fun loadProbe(file: File) {
                check(me.grey.picquery.BuildConfig.LITERT_VERSION == "1.4.1") {
                    "The historical XNNPACK probe requires LiteRT 1.4.1. Use MobileCLIP2InstrumentedTest for the current runtime."
                }
                val path = file.canonicalPath
                if (loadedPath != null) {
                    check(loadedPath == path) { "A different native XNNPACK probe is already loaded." }
                    return
                }
                require(file.isFile && file.canRead()) { "Native XNNPACK probe is missing: $path" }
                TensorFlowLite.init()
                System.load(path)
                loadedPath = path
            }
        }
    }
}
