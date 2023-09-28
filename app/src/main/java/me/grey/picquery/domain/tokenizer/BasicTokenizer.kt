package token

import java.text.Normalizer

class BasicTokenizer(private val doLowerCase: Boolean = true) {
    fun tokenize(text: String): List<String> {
        var inputText = convertToUnicode(text)
        inputText = cleanText(inputText)
        inputText = tokenizeChineseChars(inputText)

        val origTokens = whitespaceTokenize(inputText)
        val splitTokens = mutableListOf<String>()
        for (token in origTokens) {
            var processedToken = token
            if (doLowerCase) {
                processedToken = processedToken.toLowerCase()
                processedToken = runStripAccents(processedToken)
            }
            splitTokens.addAll(runSplitOnPunc(processedToken))
        }

        return whitespaceTokenize(splitTokens.joinToString(" "))
    }

    private fun runStripAccents(text: String): String {
        val normalizedText = Normalizer.normalize(text, Normalizer.Form.NFD)
        val output = StringBuilder()
        for (char in normalizedText) {
            val category = Character.getType(char)
            if (category.toByte() == Character.NON_SPACING_MARK) {
                continue
            }
            output.append(char)
        }
        return output.toString()
    }

    private fun runSplitOnPunc(text: String): List<String> {
        var i = 0
        var startNewWord = true
        val output = mutableListOf<MutableList<Char>>()
        while (i < text.length) {
            val char = text[i]
            if (isPunctuation(char)) {
                output.add(mutableListOf(char))
                startNewWord = true
            } else {
                if (startNewWord) {
                    output.add(mutableListOf())
                }
                startNewWord = false
                output[output.size - 1].add(char)
            }
            i++
        }
        return output.map { it.joinToString("") }
    }

    private fun tokenizeChineseChars(text: String): String {
        val output = StringBuilder()
        for (char in text) {
            val cp = char.toInt()
            if (isChineseChar(cp)) {
                output.append(" ")
                output.append(char)
                output.append(" ")
            } else {
                output.append(char)
            }
        }
        return output.toString()
    }

    private fun isChineseChar(cp: Int): Boolean {
        return (cp in 0x4E00..0x9FFF ||
                cp in 0x3400..0x4DBF ||
                cp in 0x20000..0x2A6DF ||
                cp in 0x2A700..0x2B73F ||
                cp in 0x2B740..0x2B81F ||
                cp in 0x2B820..0x2CEAF ||
                cp in 0xF900..0xFAFF ||
                cp in 0x2F800..0x2FA1F)
    }

    private fun cleanText(text: String): String {
        val output = StringBuilder()
        for (char in text) {
            val cp = char.toInt()
            if (cp == 0 || cp == 0xfffd || isControl(char)) {
                continue
            }
            if (isWhitespace(char)) {
                output.append(" ")
            } else {
                output.append(char)
            }
        }
        return output.toString()
    }

//    private fun isPunctuation(char: Char): Boolean {
//        return Pattern.matches("\\p{Punct}", char.toString())
//    }
//
//    private fun isControl(char: Char): Boolean {
//        val type = Character.getType(char).toByte()
//        return type == Character.CONTROL || type == Character.FORMAT || type == Character.PRIVATE_USE ||
//                type == Character.SURROGATE || type == Character.UNASSIGNED
//    }
//
//    private fun isWhitespace(char: Char): Boolean {
//        return Character.isWhitespace(char)
//    }
//
//    private fun whitespaceTokenize(text: String): List<String> {
//        return text.trim().split("\\s+".toRegex())
//    }
}