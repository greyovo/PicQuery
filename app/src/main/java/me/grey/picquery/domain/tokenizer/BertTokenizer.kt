package token

class BertTokenizer(
    val vocabProvider: () -> Map<String, Int>,
) {
    companion object {
        const val contextLength = 52
    }

    private val vocab = vocabProvider()
    private val tokenizer = FullTokenizer(vocab)

    fun tokenize(text: String): List<Int> {
        val token = tokenizer.tokenize(text)

        val finalToken = mutableListOf<Int>()
        finalToken.add(tokenizer.vocab["[CLS]"]!!)
        val content = tokenizer.convertTokensToIds(token)
        if (content.size > contextLength - 2) {
            // 截断过长的token
            finalToken.addAll(content.subList(0, contextLength - 2))
        } else {
            finalToken.addAll(content)
        }
        finalToken.add(tokenizer.vocab["[SEP]"]!!)
        // fill empty
        if (finalToken.size < contextLength) {
            val zeros = MutableList(contextLength - finalToken.size) { 0 }
            finalToken.addAll(zeros)
        }
        return finalToken
    }
}