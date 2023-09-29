package me.grey.picquery.domain

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.common.AssetUtil
import java.io.File

class MLKitTranslator {

    private val context: Context
        get() = PicQueryApplication.context

    private val shouldCopyModel: Boolean
        get() = !targetModelDirectory.exists()

    private val targetModelDirectory: File
        get() = File(context.dataDir, "no_backup/com.google.mlkit.translate.models")

    private suspend fun copyModelsFromAssets() {
        Log.d(TAG, "copyModelsFromAssets...")
        AssetUtil.copyAssetsFolder(
            context,
            "mlkit/com.google.mlkit.translate.models",
            targetModelDirectory,
        )
    }

    private val modelManager = RemoteModelManager.getInstance()

    private companion object {
        const val TAG = "MLKitTranslator"
    }

    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.CHINESE)
        .setTargetLanguage(TranslateLanguage.ENGLISH)
        .build()
    private val englishChineseTranslator = Translation.getClient(options)

    private fun getDownloadedModels() {
        // Get translation models stored on the device.
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                Log.d(TAG, "getDownloadedModels: $models")
            }
            .addOnFailureListener {
                // Error.
                Log.w(TAG, "getDownloadedModels: no model!")
            }
    }


    suspend fun translate(
        text: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit,
    ): Task<String> {
        if (shouldCopyModel) {
            copyModelsFromAssets()
        }
        return englishChineseTranslator.translate(text)
            .addOnSuccessListener { translatedText ->
                // Translation successful.
                onSuccess(translatedText)
                Log.i(TAG, "$text 翻译为: $translatedText")
            }
            .addOnFailureListener { exception ->
                // Error.
                onError(exception)
                Log.e(TAG, "翻译错误")
                // ...
            }
    }
}