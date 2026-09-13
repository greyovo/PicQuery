package me.grey.picquery.feature.mobileclip2

import android.content.Context
import java.nio.IntBuffer
import me.grey.picquery.feature.BPETokenizer
import me.grey.picquery.feature.base.TextEncoder

class TextEncoderMobileCLIPv2(private val context: Context) : TextEncoder {
    private val sessionLock = Any()
    private val tokenizer by lazy { BPETokenizer(context) }
    private var session: MobileCLIP2Session? = null
    private var closed = false

    override fun encode(input: String): FloatArray = synchronized(sessionLock) {
        check(!closed) { "MobileCLIP2 text encoder is closed." }
        // BPETokenizer has a mutable cache, so tokenization shares the inference lock.
        val tokens = tokenizer.tokenize(input).first
        val activeSession = session ?: MobileCLIP2Session.create(context, MobileCLIP2Tower.TEXT)
            .also { session = it }
        activeSession.run(IntBuffer.wrap(tokens))
    }

    fun closeSession() = synchronized(sessionLock) {
        closed = true
        val activeSession = session
        session = null
        activeSession?.close()
    }
}
