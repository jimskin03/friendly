package me.rerere.videogen.provider

import me.rerere.videogen.model.VideoGenerationRequest
import me.rerere.videogen.model.VideoGenerationTask


interface VideoGenerationProvider<S : VideoGenerationProviderSetting> {
    val id: String

    suspend fun create(
        setting: S,
        request: VideoGenerationRequest,
    ): Result<VideoGenerationTask>

    suspend fun query(
        setting: S,
        taskId: String,
    ): Result<VideoGenerationTask>
}
