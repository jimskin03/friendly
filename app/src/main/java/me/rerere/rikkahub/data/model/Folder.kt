package me.rerere.rikkahub.data.model

import java.time.Instant
import kotlin.uuid.Uuid


data class Folder(
    val id: Uuid = Uuid.random(),
    val assistantId: Uuid,
    val name: String,
    val label: String = "planning",
    val sortIndex: Int = 0,
    val createAt: Instant = Instant.now(),
)
