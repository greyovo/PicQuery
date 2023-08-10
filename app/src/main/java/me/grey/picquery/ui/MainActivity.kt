package me.grey.picquery.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.grey.picquery.data.encoder.ImageEncoderONNX
import me.grey.picquery.data.encoder.TextEncoderONNX
import me.grey.picquery.ui.theme.PicQueryTheme
import me.grey.picquery.util.assetFilePath
import me.grey.picquery.util.loadThumbnail
import me.grey.picquery.util.saveBitMap
import java.io.File
import java.util.*
import kotlin.concurrent.thread

private val imageList =
    listOf(
        "image@400px.jpg",
        "image@1000px.jpg",
        "image@4000px.jpg",
        "image@4000px-large.jpg",
        "image-large-17.2MB.jpg"
    )

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PicQueryTheme() {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Column {
                        Greeting(selectedImage.value)
                        OutlinedButton(onClick = { imageListExpanded.value = true }) {
                            Text(text = "selectImage")
                            DropdownMenu(
                                expanded = imageListExpanded.value,
                                onDismissRequest = { imageListExpanded.value = false }
                            ) {
                                for (im in imageList) {
                                    DropdownMenuItem(onClick = {
                                        setImage(im)
                                    }) {
                                        Text(im)
                                    }
                                }
                            }
                        }

                        Text(text = "tokenizerCost: ${tokenizerCost.value} ms")

                        Button(onClick = { testTextEncoder() }) {
                            Text(text = "testTextEncoder")
                        }
                        Text(text = "testTextEncoder: ${encodeTextCost.value} ms")

                        Button(onClick = { testImageEncoder() }) {
                            Text(text = "testImageEncoder")
                        }
                        Text(text = "testImageEncoder: ${encodeImageCost.value} ms")

                        Button(onClick = { testBatchONNX() }) {
                            Text(text = "testBatchONNX")
                        }
                        Button(onClick = { testMultiThreadONNX() }) {
                            Text(text = "testMultiThreadONNX")
                        }

                        Text(text = encodeImageState1.value)
                        Text(text = encodeImageState2.value)
                    }
                }
            }
        }

        imagePath = assetFilePath(this, selectedImage.value)
    }

    var imageListExpanded = mutableStateOf(false)
    private var selectedImage = mutableStateOf(imageList[0])
    private var tokenizerCost = mutableStateOf(0L)
    private var encodeTextCost: MutableState<Long> = mutableStateOf(0L)
    private var encodeImageCost: MutableState<Long> = mutableStateOf(0L)
    private var encodeImageState1: MutableState<String> = mutableStateOf("None")
    private var encodeImageState2: MutableState<String> = mutableStateOf("None")

    private var textEncoderONNX: TextEncoderONNX? = null
    private var imageEncoderONNX: ImageEncoderONNX? = null

    private var imagePath: String = ""

    private fun testTextEncoder() {
        if (textEncoderONNX == null) {
            loadTextEncoderONNX()
        }
        val text = "A bird flying in the sky, cloudy"
        Log.i("testTextEncoder", "start...")
        val time = System.currentTimeMillis()
        textEncoderONNX?.encode(text)
        encodeTextCost.value = System.currentTimeMillis() - time
    }

    private fun testImageEncoder() {
        lifecycleScope.launch {
            if (imageEncoderONNX == null) {
                loadImageEncoderONNX()
            }
            val time = System.currentTimeMillis()
            // 120ms+ for loading 4096px, 4.7MB JPEG
//            val bitmap = BitmapFactory.decodeFile(filesDir.path + "/" + selectedImage.value)
            // 70ms for loading 4096px, 4.7MB JPEG
//            val bitmap = decodeSampledBitmapFromFile(imagePath, 224, 224)
            // 64ms for loading 4096px, 4.7MB JPEG
            val bitmap = loadThumbnail(this@MainActivity, imagePath)
            Log.d("loadImage", "${System.currentTimeMillis() - time} ms")
            saveBitMap(this@MainActivity, bitmap, "decodeSampledBitmapFromFile")
            val output = imageEncoderONNX?.encode(bitmap)
            encodeImageCost.value = System.currentTimeMillis() - time
            Log.d("testImageEncoder", Arrays.toString(output))
        }
    }

    private fun loadImageEncoderONNX() {
        if (imageEncoderONNX == null) {
            encodeImageState1.value = "Loading ImageEncoder ONNX ..."
            encodeImageState2.value = "Loading ImageEncoder ONNX ..."
            imageEncoderONNX = ImageEncoderONNX(context = this@MainActivity)
            encodeImageState1.value = "Loading ImageEncoder ONNX done"
            encodeImageState2.value = "Loading ImageEncoder ONNX done"
        }
    }

    private fun loadTextEncoderONNX() {
        lifecycleScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.Default) {
                if (textEncoderONNX == null) {
                    textEncoderONNX = TextEncoderONNX(context = this@MainActivity)
                }
            }
        }
    }

    private var batchTestLock = false

    /**
     * With SnapDragon 8+ gen 1 SoC, ONNX model:
     * - 500pics @ ~54s, 400px, 21KB, fp32
     * - 500pics @ ~20s, 400px, 21KB, int8
     * - 500pics @ ~27s, 1000px, 779KB, int8
     * - 500pics @ ~60s, 4096px, 1.7MB, int8
     * - 500pics @ ~87s, 4096px, 4MB, int8
     *
     * Decoding stream costs more time than model inference time
     * if the image is large.
     */
    private fun testBatchONNX() {
        if (batchTestLock) {
            Toast.makeText(this, "Already Running batch test!", Toast.LENGTH_SHORT).show()
            return
        }
        batchTestLock = true
        lifecycleScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.Default) {
                if (imageEncoderONNX == null) {
                    loadImageEncoderONNX()
                }
                val total = 500
                val start = System.currentTimeMillis()
                for (i in 0..total) {
                    val _start = System.currentTimeMillis()
                    val bitmap = loadThumbnail(this@MainActivity, imagePath)
                    Log.d("decodeStream", "${System.currentTimeMillis() - _start} ms")
                    saveBitMap(this@MainActivity, bitmap, "temp-224.jpg")
                    imageEncoderONNX?.encode(bitmap)
                    if (i % 10 == 0) {
                        encodeImageState1.value =
                            "Processing `${selectedImage.value}`: $i / $total using ONNX..."
                    }
                }
                encodeImageState1.value =
                    "Processed: $total `${selectedImage.value}` images in ${System.currentTimeMillis() - start} ms using ONNX."
                batchTestLock = false
            }
        }
    }


    var encodeLock1 = false
    var encodeLock2 = false

    private fun testMultiThreadONNX() {
        assetFilePath(this, selectedImage.value)
        if (imageEncoderONNX == null) {
            loadImageEncoderONNX()
            return
        }
        if (encodeLock1) {
            Toast.makeText(this, "Already Running batch test!", Toast.LENGTH_SHORT).show()
            return
        } else {
            encodeLock1 = true
            thread(start = true, isDaemon = false, name = "DThread1", priority = 1) {
                suspend {
                    val total = 500
                    val start = System.currentTimeMillis()
                    for (i in 0..total) {
                        val bitmap =
                            BitmapFactory.decodeStream(File(filesDir.path + "/" + (selectedImage.value)).inputStream())
                        imageEncoderONNX?.encode(bitmap)
                        if (i % 10 == 0) {
                            encodeImageState1.value =
                                "Processing `${selectedImage.value}`: $i / $total"
                        }
                    }
                    encodeImageState1.value =
                        "Processed: $total `${selectedImage.value}` images in ${System.currentTimeMillis() - start} ms"
                    encodeLock1 = false
                }

            }
        }
        if (encodeLock2) {
            Toast.makeText(this, "Already Running batch test!", Toast.LENGTH_SHORT).show()
            return
        } else {
            encodeLock2 = true
            thread(start = true, isDaemon = false, name = "DThread2", priority = 1) {
                suspend {
                    val total = 500
                    val start = System.currentTimeMillis()
                    for (i in 0..total) {
                        val bitmap = loadThumbnail(this, imagePath)
                        imageEncoderONNX?.encode(bitmap)
                        if (i % 10 == 0) {
                            encodeImageState2.value =
                                "Processing `${selectedImage.value}`: $i / $total"
                        }
                    }
                    encodeImageState2.value =
                        "Processed: $total `${selectedImage.value}` images in ${System.currentTimeMillis() - start} ms"
                    encodeLock2 = false
                }
            }

        }
    }

    private fun setImage(im: String) {
        imageListExpanded.value = false
        selectedImage.value = im
        imagePath = assetFilePath(this@MainActivity, selectedImage.value)
        if (imagePath.isEmpty()) {
            Toast.makeText(this, "图片加载失败！", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Testing: $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    PicQueryTheme {
        Column {
            Greeting("Android")
        }
    }
}