package me.grey.picquery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.permissionx.guolindev.PermissionX
import kotlinx.coroutines.*
import me.grey.picquery.R
import me.grey.picquery.core.ImageSearcher
import me.grey.picquery.core.encoder.ImageEncoder
import me.grey.picquery.core.encoder.TextEncoder
import me.grey.picquery.data.PhotoRepository
import me.grey.picquery.theme.PicQueryTheme
import me.grey.picquery.util.assetFilePath
import me.grey.picquery.util.loadThumbnail
import me.grey.picquery.util.saveBitMap


private val imageList = listOf(
    "image@400px.jpg",
    "image@1000px.jpg",
    "image@4000px.jpg",
    "image@4000px-large.jpg",
    "image-large-17.2MB.jpg"
)

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

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
                            DropdownMenu(expanded = imageListExpanded.value,
                                onDismissRequest = { imageListExpanded.value = false }) {
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
                        Button(onClick = { testImageSearcherEncode() }) {
                            Text(text = "testImageSearcherEncode")
                        }
                        Button(onClick = { testImageSearcherSearch() }) {
                            Text(text = "testImageSearcherSearch")
                        }
                        Button(onClick = { doIndexAlbum() }) {
                            Text(text = "doIndexAlbum")
                        }

                        Text(text = encodeImageState1.value)
                        Text(text = encodeImageState2.value)
                    }
                }
            }
        }

        imagePath = assetFilePath(this, selectedImage.value)
        requestPermission()
    }

    private fun testImageSearcherSearch() {
        lifecycleScope.launch(Dispatchers.Default) {
            initEncoder()
            delay(2000)
            imageSearcher?.search()
        }
    }

    private val permissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        else
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun requestPermission() {
        PermissionX.init(this)
            .permissions(permissions)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    resources.getString(R.string.permission_tips),
                    resources.getString(R.string.ok),
                    resources.getString(R.string.cancel),
                )
            }.request { allGranted, _, _ ->
                if (!allGranted) {
                    Toast.makeText(this, "无法获取相应权限", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun doIndexAlbum() {
        for (p in permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                requestPermission()
        }

        val repo = PhotoRepository(contentResolver)
        val batchSize = 500
        val photos = repo.getPhotos(pageSize = batchSize)
        if (photos.isEmpty()) {
            Toast.makeText(this, "没有内容！", Toast.LENGTH_SHORT).show()
        } else {
            var start = System.currentTimeMillis()
            Toast.makeText(this, "开始构建索引！", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch(Dispatchers.Default) {
                initEncoder()
                encodeImageState1.value = "initEncoder！cost ${System.currentTimeMillis() - start}ms"
                start = System.currentTimeMillis()
                imageSearcher?.encodePhotoList(contentResolver, photos,this@MainActivity)
                encodeImageState2.value =
                    "encodePhotoList length=${photos.size} cost ${System.currentTimeMillis() - start}ms"
            }
        }
    }

    var imageListExpanded = mutableStateOf(false)
    private var selectedImage = mutableStateOf(imageList[0])
    private var tokenizerCost = mutableStateOf(0L)
    private var encodeTextCost: MutableState<Long> = mutableStateOf(0L)
    private var encodeImageCost: MutableState<Long> = mutableStateOf(0L)
    private var encodeImageState1: MutableState<String> = mutableStateOf("None")
    private var encodeImageState2: MutableState<String> = mutableStateOf("None")

    private var textEncoder: TextEncoder? = null
    private var imageEncoder: ImageEncoder? = null

    private var imagePath: String = ""

    private fun testTextEncoder() {
        if (textEncoder == null) {
            loadTextEncoderONNX()
        }
        val text = "A bird flying in the sky, cloudy"
        Log.i("testTextEncoder", "start...")
        val time = System.currentTimeMillis()
        textEncoder?.encode(text)
        encodeTextCost.value = System.currentTimeMillis() - time
    }

    private fun testImageEncoder() {
        lifecycleScope.launch(Dispatchers.Default) {
            initEncoder()
            val time = System.currentTimeMillis()
            // 120ms+ for loading 4096px, 4.7MB JPEG
//            val bitmap = BitmapFactory.decodeFile(filesDir.path + "/" + selectedImage.value)
            // 70ms for loading 4096px, 4.7MB JPEG
//            val bitmap = decodeSampledBitmapFromFile(imagePath, 224, 224)
            // 64ms for loading 4096px, 4.7MB JPEG
            val bitmap = loadThumbnail(this@MainActivity, imagePath)
            Log.d("loadImage", "${System.currentTimeMillis() - time} ms")
//            saveBitMap(this@MainActivity, bitmap, "decodeSampledBitmapFromFile")
            val output = imageEncoder?.encode(bitmap)
            encodeImageCost.value = System.currentTimeMillis() - time
            println(output.toString())
            println(output?.array().contentToString())
//            println(output?.size)
//            println(output!![0].size)
//            println(output[0].contentToString())
        }
    }

    private fun loadImageEncoderONNX() {
        if (imageEncoder == null) {
            encodeImageState1.value = "Loading ImageEncoder ONNX ..."
            encodeImageState2.value = "Loading ImageEncoder ONNX ..."
            imageEncoder = ImageEncoder(context = this@MainActivity)
            encodeImageState1.value = "Loading ImageEncoder ONNX done"
            encodeImageState2.value = "Loading ImageEncoder ONNX done"
        }
    }

    private fun loadTextEncoderONNX() {
        if (textEncoder == null) {
            textEncoder = TextEncoder(context = this@MainActivity)
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
                if (imageEncoder == null) {
                    loadImageEncoderONNX()
                }
                val total = 500
                val start = System.currentTimeMillis()
                for (i in 0..total) {
                    val _start = System.currentTimeMillis()
                    val bitmap = loadThumbnail(this@MainActivity, imagePath)
                    Log.d("decodeStream", "${System.currentTimeMillis() - _start} ms")
                    saveBitMap(this@MainActivity, bitmap, "temp-224.jpg")
                    imageEncoder?.encode(bitmap)
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


    var imageSearcher: ImageSearcher? = null

    private fun testImageSearcherEncode() {
        assetFilePath(this, selectedImage.value)
        val batchSize = 100
        lifecycleScope.launch(Dispatchers.Default) {
            // 在后台线程执行耗时操作
            initEncoder()
            val tempBitmaps = mutableListOf<Bitmap>()
            encodeImageState1.value = "load $batchSize thumbnails..."
            for (i in 1..batchSize) {
                tempBitmaps.add(loadThumbnail(this@MainActivity, imagePath))
            }
            encodeImageState1.value = "testImageSearcherEncode batch $batchSize ..."
            imageSearcher!!.encodeBatch(tempBitmaps)
            encodeImageState1.value = "testImageSearcherEncode $batchSize done!"
            Log.d("ImageSearcher", "testImageSearcherEncode $batchSize done!")
        }

    }

    private fun initEncoder() {
        if (imageEncoder == null) {
            loadImageEncoderONNX()
        }
        if (textEncoder == null) {
            loadTextEncoderONNX()
        }
        if (imageSearcher == null) {
            imageSearcher = ImageSearcher(imageEncoder!!, textEncoder!!, this.filesDir)
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