package com.example.android.data.tokenizer

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Standard WordPiece Tokenizer for BERT / TinyBERT models.
 * Loads vocabulary from assets (models/vocab.txt) and produces input_ids & attention_mask tensors.
 */
class BertWordPieceTokenizer(
    private val vocab: Map<String, Long>,
    private val maxLength: Int = 128
) {

    companion object {
        const val PAD_TOKEN = "[PAD]"
        const val UNK_TOKEN = "[UNK]"
        const val CLS_TOKEN = "[CLS]"
        const val SEP_TOKEN = "[SEP]"

        const val PAD_ID = 0L
        const val UNK_ID = 100L
        const val CLS_ID = 101L
        const val SEP_ID = 102L

        fun fromAssets(context: Context, assetPath: String = "models/vocab.txt", maxLength: Int = 128): BertWordPieceTokenizer {
            val vocabMap = mutableMapOf<String, Long>()
            try {
                context.assets.open(assetPath).use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        var lineIndex = 0L
                        var line = reader.readLine()
                        while (line != null) {
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) {
                                vocabMap[trimmed] = lineIndex
                            }
                            lineIndex++
                            line = reader.readLine()
                        }
                    }
                }
            } catch (e: Exception) {
                // If vocab file cannot be loaded, seed with standard BERT special tokens
                vocabMap[PAD_TOKEN] = PAD_ID
                vocabMap[UNK_TOKEN] = UNK_ID
                vocabMap[CLS_TOKEN] = CLS_ID
                vocabMap[SEP_TOKEN] = SEP_ID
            }
            return BertWordPieceTokenizer(vocabMap, maxLength)
        }
    }

    data class EncodingResult(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokens: List<String>
    )

    fun encode(text: String): EncodingResult {
        val inputIds = LongArray(maxLength) { PAD_ID }
        val attentionMask = LongArray(maxLength) { 0L }
        val tokens = mutableListOf<String>()

        tokens.add(CLS_TOKEN)
        inputIds[0] = CLS_ID
        attentionMask[0] = 1L
        var currentIdx = 1

        val words = basicTokenize(text)
        for (word in words) {
            val subwords = wordpieceTokenize(word)
            for (subword in subwords) {
                if (currentIdx < maxLength - 1) { // Reserve 1 slot for [SEP]
                    tokens.add(subword)
                    inputIds[currentIdx] = vocab[subword] ?: UNK_ID
                    attentionMask[currentIdx] = 1L
                    currentIdx++
                } else {
                    break
                }
            }
            if (currentIdx >= maxLength - 1) break
        }

        tokens.add(SEP_TOKEN)
        inputIds[currentIdx] = SEP_ID
        attentionMask[currentIdx] = 1L

        return EncodingResult(inputIds, attentionMask, tokens)
    }

    private fun basicTokenize(text: String): List<String> {
        val normalized = text.lowercase().trim()
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()

        for (ch in normalized) {
            if (ch.isWhitespace()) {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.clear()
                }
            } else if (isPunctuation(ch)) {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.clear()
                }
                tokens.add(ch.toString())
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }
        return tokens
    }

    private fun wordpieceTokenize(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        val subwords = mutableListOf<String>()
        var start = 0
        val len = word.length

        while (start < len) {
            var end = len
            var curSubword: String? = null
            while (start < end) {
                val candidate = (if (start == 0) "" else "##") + word.substring(start, end)
                if (vocab.containsKey(candidate)) {
                    curSubword = candidate
                    break
                }
                end--
            }
            if (curSubword == null) {
                subwords.add(UNK_TOKEN)
                break
            } else {
                subwords.add(curSubword)
                start = end
            }
        }
        return subwords
    }

    private fun isPunctuation(ch: Char): Boolean {
        val type = Character.getType(ch)
        return type == Character.CONNECTOR_PUNCTUATION.toInt() ||
               type == Character.DASH_PUNCTUATION.toInt() ||
               type == Character.START_PUNCTUATION.toInt() ||
               type == Character.END_PUNCTUATION.toInt() ||
               type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
               type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
               type == Character.OTHER_PUNCTUATION.toInt() ||
               "-@%&~#$!,;:*+[](){}<>./?\\\"'=^`|".contains(ch)
    }
}
