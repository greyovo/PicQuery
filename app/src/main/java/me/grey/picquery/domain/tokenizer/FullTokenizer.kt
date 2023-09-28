package me.grey.picquery.domain.tokenizer

class FullTokenizer(
    val vocab: Map<String, Int>,
    private val doLowerCase: Boolean = true
) {
    //    private val vocab: Map<String, Int> = loadVocab(PicQueryApplication.context, vocabFile)
    private val invVocab: Map<Int, String> = vocab.entries.associate { (k, v) -> v to k }
    private val basicTokenizer = BasicTokenizer(doLowerCase)
    private val wordPieceTokenizer = WordPieceTokenizer(vocab)

    fun tokenize(text: String): List<String> {
        val splitTokens = mutableListOf<String>()
        val temp = basicTokenizer.tokenize(text)
        for (token in temp) {
            for (subToken in wordPieceTokenizer.tokenize(token)) {
                splitTokens.add(subToken)
            }
        }
        return splitTokens
    }

    fun convertTokensToIds(tokens: List<String>): List<Int> {
        return convertByVocab(vocab, tokens)
    }

    fun convertIdsToTokens(ids: List<Int>): List<String> {
        return ids.mapNotNull { invVocab[it] }
    }

    fun convertTokensToString(
        tokens: List<String>,
        cleanUpTokenizationSpaces: Boolean = true
    ): String {
        fun cleanUpTokenization(outString: String): String {
            var string = outString
            string = string.replace(" .", ".")
                .replace(" ?", "?")
                .replace(" !", "!")
                .replace(" ,", ",")
                .replace(" ' ", "'")
                .replace(" n't", "n't")
                .replace(" 'm", "'m")
                .replace(" 's", "'s")
                .replace(" 've", "'ve")
                .replace(" 're", "'re")
            return string
        }

        var text = tokens.joinToString(" ").replace(" ##", "").trim()
        if (cleanUpTokenizationSpaces) {
            text = cleanUpTokenization(text)
        }
        return text
    }
}