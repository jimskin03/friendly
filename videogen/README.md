# Video Generation Layer

`videogen` unifies asynchronous video generation protocols for supported providers.

## Design Boundaries

All providers follow the same task lifecycle:

1. Submit generation request and receive a task ID;
2. Poll task status;
3. On success, retrieve the temporary video URL to download or persist.

The module is only responsible for provider protocol adaptation, not for persisting tasks, downloading videos, uploading local assets, or managing UI state.

Common models are located in `model/VideoGeneration.kt`:

- `VideoGenerationRequest`: Prompt, multimodal inputs, resolution, aspect ratio, duration, audio, watermark, etc.
- `VideoGenerationInput`: First frame, last frame, reference image/video/audio, files, webpages, and raw provider inputs.
- `VideoGenerationTask`: Unified queued, running, succeeded, failed, canceled, and expired states.
- `extraParameters` / `Raw`: Provider-specific fields to handle fast model iterations without frequent common API changes.

`VideoGenerationProvider` exposes `create` and `query`. `VideoGenerationManager.watch` provides a cancelable polling Flow for UI or repository subscription.

## Usage Example

```kotlin
val manager = VideoGenerationManager(okHttpClient)
val setting = VideoGenerationProviderSetting.MiniMax(apiKey = apiKey)

val submitted = manager.create(
    setting = setting,
    request = VideoGenerationRequest(
        prompt = "In a rainy city night, a vintage sports car slowly drives through neon-lit streets",
        resolution = "2K",
        aspectRatio = "16:9",
        durationSeconds = 5,
    ),
).getOrThrow()

manager.watch(setting, submitted.id).collect { task ->
    // Persist task or update UI; Flow terminates on completion.
}
```
