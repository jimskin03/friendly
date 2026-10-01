package me.rerere.ai.provider.stream

import me.rerere.ai.ui.StreamChunk


interface StreamChunkDecoder {

    fun accept(event: SseEvent): DecodeResult


    fun onClosed(): List<StreamChunk>
}

data class DecodeResult(
    val chunks: List<StreamChunk> = emptyList(),

    val completed: Boolean = false,
)
