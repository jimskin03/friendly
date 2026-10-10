package me.rerere.ai.provider

/** Supplies a short-lived OpenAI OAuth access token without exposing it to UI or settings. */
fun interface OpenAIAccessTokenResolver {
    suspend fun accessToken(accountId: String): String
}
