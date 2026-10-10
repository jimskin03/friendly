package app.friendly.assistant.ui.pages.chat

import app.friendly.assistant.utils.extractQuotedContentAsText
import app.friendly.assistant.utils.removeBracketedContent
import app.friendly.assistant.utils.stripMarkdown

/**
 * Splits streaming LLM output text into clean, speech-ready sentences as tokens arrive.
 * Enables zero-dead-air pipelining to TTS so audio playback starts on sentence 1 while
 * sentence 2 and 3 are still generating.
 */
class SentenceStreamSplitter(
    private val ttsOnlyReadQuoted: Boolean = false,
    private val ttsOnlyReadOutsideBrackets: Boolean = false,
) {
    private var processedIndex = 0

    /**
     * Consumes the latest accumulated text from the LLM stream.
     * Returns a list of newly completed, cleaned sentences ready for TTS.
     */
    fun consume(accumulatedText: String): List<String> {
        if (accumulatedText.length <= processedIndex) return emptyList()
        val textToProcess = accumulatedText.substring(processedIndex)
        val sentences = mutableListOf<String>()

        var searchIndex = 0
        while (searchIndex < textToProcess.length) {
            val boundary = findSentenceBoundary(textToProcess, searchIndex)
            if (boundary == -1) break

            val rawSentence = textToProcess.substring(searchIndex, boundary)
            val cleaned = cleanText(rawSentence)
            if (cleaned.isNotBlank()) {
                sentences.add(cleaned)
            }
            searchIndex = boundary
        }

        if (searchIndex > 0) {
            processedIndex += searchIndex
        }
        return sentences
    }

    /**
     * Flushes any remaining text when generation is complete.
     */
    fun finish(accumulatedText: String): List<String> {
        val sentences = mutableListOf<String>()
        // Consume any remaining sentence boundaries first
        sentences.addAll(consume(accumulatedText))

        // Any leftover text in buffer
        if (accumulatedText.length > processedIndex) {
            val remaining = accumulatedText.substring(processedIndex)
            val cleaned = cleanText(remaining)
            if (cleaned.isNotBlank()) {
                sentences.add(cleaned)
            }
            processedIndex = accumulatedText.length
        }
        return sentences
    }

    fun reset() {
        processedIndex = 0
    }

    private fun cleanText(text: String): String {
        var result = text
        if (ttsOnlyReadQuoted) {
            result = result.extractQuotedContentAsText() ?: result
        }
        if (ttsOnlyReadOutsideBrackets) {
            result = result.removeBracketedContent() ?: result
        }
        return result.stripMarkdown().trim()
    }

    companion object {
        // CJK and newline punctuation that mark sentence or clause completion
        private val CJK_DELIMITERS = charArrayOf('。', '！', '？', '；', '\n')
        private val COMMON_ABBREVIATIONS = setOf("mr.", "mrs.", "dr.", "prof.", "e.g.", "i.e.", "vs.", "etc.")

        private fun findSentenceBoundary(text: String, startIndex: Int): Int {
            for (i in startIndex until text.length) {
                val c = text[i]
                if (c in CJK_DELIMITERS) {
                    return i + 1
                }
                if (c == '.' || c == '!' || c == '?') {
                    // Avoid numbers like 3.14
                    val prevChar = if (i > 0) text[i - 1] else null
                    if (prevChar != null && prevChar.isDigit()) continue

                    // Check abbreviation
                    val wordBefore = text.substring(startIndex, i + 1).split(' ').lastOrNull()?.lowercase()
                    if (wordBefore != null && wordBefore in COMMON_ABBREVIATIONS) continue

                    // If followed by optional closing formatting (*, _, ~, quotes) and then whitespace/newline
                    var nextPos = i + 1
                    while (nextPos < text.length && (
                        text[nextPos] == '*' || text[nextPos] == '_' ||
                        text[nextPos] == '~' || text[nextPos] == '`' ||
                        text[nextPos] == '"' || text[nextPos] == '”' ||
                        text[nextPos] == '\'' || text[nextPos] == ')' || text[nextPos] == ']'
                    )) {
                        nextPos++
                    }
                    if (nextPos < text.length) {
                        val next = text[nextPos]
                        if (next.isWhitespace()) {
                            return nextPos
                        }
                    }
                }
                // Break overly long clauses at commas if longer than 120 chars to avoid delayed audio
                if (i - startIndex > 120 && (c == ',' || c == '，' || c == '、')) {
                    if (i + 1 < text.length && (c != ',' || text[i + 1].isWhitespace())) {
                        return i + 1
                    }
                }
            }
            return -1
        }
    }
}
