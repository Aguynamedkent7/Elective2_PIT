package app.numlab.domain.model

import app.numlab.domain.interp.InterpInput
import app.numlab.domain.roots.RootInput
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The inputs of one run. History stores inputs only and re-solves on open (PLAN §5.1). */
@Serializable
sealed interface RunInput {
    @Serializable
    @SerialName("root")
    data class Root(val input: RootInput) : RunInput

    @Serializable
    @SerialName("interp")
    data class Interp(val input: InterpInput) : RunInput
}

/** Versioned JSON for history rows and saved state. Bump [SCHEMA] on breaking changes. */
object RunCodec {
    const val SCHEMA = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(run: RunInput): String = json.encodeToString(RunInput.serializer(), run)
    fun decode(text: String): RunInput = json.decodeFromString(RunInput.serializer(), text)
}

/** One notebook entry. */
data class HistoryItem(
    val id: Long,
    val run: RunInput,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val sample: Boolean,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Reduce motion follows the system unless overridden. */
enum class MotionPref { SYSTEM, REDUCED, FULL }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val decimals: Int = 6,
    val defaultTolerance: String = "1e-5",
    val defaultMaxIter: String = "100",
    val motion: MotionPref = MotionPref.SYSTEM,
    val haptics: Boolean = true,
    val restoreSession: Boolean = false,
)
