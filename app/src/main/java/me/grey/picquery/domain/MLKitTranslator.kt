package me.grey.picquery.domain

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.common.AssetUtil
import java.io.File

class MLKitTranslator() {

    private fun copyModelsFromAssets() {
        Log.d(TAG, "copyModelsFromAssets...")
        val context = PicQueryApplication.context
//        val inputStream = context.assets.open("mlkit/com.google.mlkit.translate.models")
//        val inputStream = context.assets.("mlkit/com.google.mlkit.translate.models")
        AssetUtil.copyAssetsFolder(
            context,
            "mlkit/com.google.mlkit.translate.models",
            File(context.dataDir, "no_backup/com.google.mlkit.translate.models"),
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

    private val conditions = DownloadConditions.Builder()
        .requireWifi()
        .build()

    init {

    }

    private fun downloadModel() {
        englishChineseTranslator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                // Model downloaded successfully. Okay to start translating.
                // (Set a flag, unhide the translation UI, etc.)
                Log.d(TAG, "downloadModel: OK")
            }
            .addOnFailureListener { exception ->
                // Model couldn’t be downloaded or other internal error.
                // ...
            }
    }

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


    fun translate(
        text: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit,
    ): Task<String> {
        // start translate:
        copyModelsFromAssets()
//        englishChineseTranslator.downloadModelIfNeeded()
        getDownloadedModels()
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