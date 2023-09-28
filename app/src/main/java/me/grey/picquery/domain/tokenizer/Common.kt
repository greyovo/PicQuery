package token

import java.io.File
import java.lang.Character.*
import java.lang.Character.UnicodeBlock.GENERAL_PUNCTUATION
import java.text.Normalizer

fun loadVocab(fileName: String): MutableMap<String, Int> {
    val vocab = mutableMapOf<String, Int>()
    val file = File(fileName)
    var index = 0
    try {
        val lines = file.readLines() // 直接读取全部文件内容

        for (line in lines) {
            var token = convertToUnicode(line)
            if (token.isEmpty())
                break
            token = token.trim()
            vocab.putIfAbsent(token, index)
            index++
        }
    } catch (e: Exception) {
        println("读取文件时发生错误：${e.message}")
    }
    return vocab
}

fun convertToUnicode(text: String): String {
    return text
}

fun whitespaceTokenize(text: String): List<String> {
    val cleanedText = text.trim()
    if (cleanedText.isEmpty()) {
        return emptyList()
    }
    return cleanedText.split(Regex("\\s+"))
}

fun convertByVocab(vocab: Map<String, Int>, items: List<String>): List<Int> {
    val output = mutableListOf<Int>()
    for (item in items) {
        if (vocab[item] != null) {
            output.add(vocab[item]!!)
        }
    }
    return output
}

fun convertTokensToIds(vocab: Map<String, Int>, tokens: List<String>): List<Int> {
    return convertByVocab(vocab, tokens)
}

fun isWhitespace(char: Char): Boolean {
    // '\t', '\n', 和 '\r' 在技术上是控制字符，但由于它们通常被视为空白字符，我们将它们视为空白字符。
    if (char == ' ' || char == '\t' || char == '\n' || char == '\r') {
        return true
    }
    val type = getType(char).toByte()
    return type == SPACE_SEPARATOR
}

fun isControl(char: Char): Boolean {
    // 这些在技术上是控制字符，但我们将它们视为空白字符。
    if (char == '\t' || char == '\n' || char == '\r') {
        return false
    }
    val block = getType(char).toByte()
    return block == CONTROL || block == FORMAT
}

private val punctuationFlags = arrayOf(
    CONNECTOR_PUNCTUATION,
    DASH_PUNCTUATION,
    END_PUNCTUATION,
    FINAL_QUOTE_PUNCTUATION,
    INITIAL_QUOTE_PUNCTUATION,
    OTHER_PUNCTUATION,
    START_PUNCTUATION
)

fun isPunctuation(char: Char): Boolean {
    val cp = char.toInt()
    // 我们将所有非字母/数字的ASCII字符视为标点符号。
    // "^", "$", 和 "`" 等字符不属于Unicode标点符号类别，但为了一致性，我们将它们视为标点符号。
    if ((cp in 33..47) || (cp in 58..64) ||
        (cp in 91..96) || (cp in 123..126)
    ) {
        return true
    }
    // FIXME 对于中文逗号无法识别
    return punctuationFlags.contains(getType(char).toByte())
}