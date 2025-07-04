package me.grey.picquery.domain

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.File
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.common.AssetUtil

class MLKitTranslator {

    private companion object {
        const val TAG = "MLKitTranslator"
        const val ASSET_MODEL_PATH = "mlkit/com.google.mlkit.translate.models"
        const val SAVED_MODEL_PATH = "no_backup/com.google.mlkit.translate.models"
    }

    private val context: Context
        get() = PicQueryApplication.context

    private val shouldCopyModel: Boolean
        get() = !targetModelDirectory.exists()

    private val targetModelDirectory =
        File(context.dataDir, SAVED_MODEL_PATH)

    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.CHINESE)
        .setTargetLanguage(TranslateLanguage.ENGLISH)
        .build()
    private val englishChineseTranslator = Translation.getClient(options)

    private suspend fun copyModelsFromAssets() {
        Log.d(TAG, "copyModelsFromAssets...")
        AssetUtil.copyAssetsFolder(
            context,
            ASSET_MODEL_PATH,
            targetModelDirectory
        )
    }

    suspend fun translate(text: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit): Task<String> {
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
