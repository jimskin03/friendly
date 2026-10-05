package me.rerere.rikkahub.ui.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceStreamSplitterTest {

    @Test
    fun testStreamingEnglishSentences() {
        val splitter = SentenceStreamSplitter()

        // Chunk 1: partial sentence
        val res1 = splitter.consume("Hello there")
        assertEquals(emptyList<String>(), res1)

        // Chunk 2: completes first sentence, starts second
        val res2 = splitter.consume("Hello there! How are you doing today?")
        assertEquals(listOf("Hello there!"), res2)

        // Chunk 3: completes second sentence
        val res3 = splitter.consume("Hello there! How are you doing today? I am Friendly.")
        assertEquals(listOf("How are you doing today?"), res3)

        // Finish: flushes final sentence
        val res4 = splitter.finish("Hello there! How are you doing today? I am Friendly.")
        assertEquals(listOf("I am Friendly."), res4)
    }

    @Test
    fun testStreamingCjkSentences() {
        val splitter = SentenceStreamSplitter()

        // Chunk 1: completes first sentence, trailing text uncompleted
        val res1 = splitter.consume("你好！我是你的语音助手")
        assertEquals(listOf("你好！"), res1)

        // Chunk 2: completes second sentence, trailing text uncompleted
        val res2 = splitter.consume("你好！我是你的语音助手。请问有什么可以帮助")
        assertEquals(listOf("我是你的语音助手。"), res2)

        // Chunk 3: completes third sentence
        val res3 = splitter.consume("你好！我是你的语音助手。请问有什么可以帮助您的？")
        assertEquals(listOf("请问有什么可以帮助您的？"), res3)

        val res4 = splitter.finish("你好！我是你的语音助手。请问有什么可以帮助您的？")
        assertEquals(emptyList<String>(), res4)
    }

    @Test
    fun testMarkdownStrippingInStreaming() {
        val splitter = SentenceStreamSplitter()

        val chunk1 = "**Sure thing!** Here is "
        val res1 = splitter.consume(chunk1)
        assertEquals(listOf("Sure thing!"), res1)

        val fullText = "**Sure thing!** Here is the result for you. Let's see."
        val res2 = splitter.finish(fullText)
        assertEquals(listOf("Here is the result for you.", "Let's see."), res2)
    }
}
