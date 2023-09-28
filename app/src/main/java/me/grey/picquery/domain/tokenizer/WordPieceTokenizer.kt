package token

class WordPieceTokenizer(
    private val vocab: Map<String, Int>,
    private val unkToken: String = "[UNK]",
    private val maxInputCharsPerWord: Int = 200
) {
    fun tokenize(text: String): List<String> {
        val outputTokens = mutableListOf<String>()

        text.split("\\s+".toRegex()).forEach { token ->
            if (token.length > maxInputCharsPerWord) {
                outputTokens.add(unkToken)
                return@forEach
            }

            var isBad = false
            var start = 0
            val subTokens = mutableListOf<String>()
            while (start < token.length) {
                var end = token.length
                var curSubstr: String? = null
                while (start < end) {
                    val substr = token.substring(start, end)
                    val prefixedSubstr = if (start > 0) "##$substr" else substr
                    if (vocab.containsKey(prefixedSubstr)) {
                        curSubstr = prefixedSubstr
                        break
                    }
                    end -= 1
                }
                if (curSubstr == null) {
                    isBad = true
                    break
                }
                subTokens.add(curSubstr)
                start = end
            }

            if (isBad) {
                outputTokens.add(unkToken)
            } else {
                outputTokens.addAll(subTokens)
            }
        }

        return outputTokens
    }
}