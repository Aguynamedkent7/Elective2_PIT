package app.numlab.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.numlab.domain.model.MotionPref
import app.numlab.domain.model.RunCodec
import app.numlab.domain.model.RunInput
import app.numlab.domain.model.Settings
import app.numlab.domain.model.ThemeMode
import app.numlab.domain.repo.Draft
import app.numlab.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.numlabPrefs: DataStore<Preferences> by preferencesDataStore("settings")

private object K {
    val theme = stringPreferencesKey("themeMode")
    val dynamic = booleanPreferencesKey("dynamicColor")
    val decimals = intPreferencesKey("decimals")
    val tol = stringPreferencesKey("defaultTolerance")
    val maxIter = stringPreferencesKey("defaultMaxIter")
    val motion = stringPreferencesKey("reduceMotionOverride")
    val haptics = booleanPreferencesKey("hapticsEnabled")
    val restore = booleanPreferencesKey("restoreSession")
    val lastModule = stringPreferencesKey("lastModule")
    val lastRoot = stringPreferencesKey("lastRootInputs")
    val lastInterp = stringPreferencesKey("lastInterpInputs")
}

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private val d = Settings()

    private fun read(p: Preferences) = Settings(
        themeMode = p[K.theme]?.let(ThemeMode::valueOf) ?: d.themeMode,
        dynamicColor = p[K.dynamic] ?: d.dynamicColor,
        decimals = p[K.decimals] ?: d.decimals,
        defaultTolerance = p[K.tol] ?: d.defaultTolerance,
        defaultMaxIter = p[K.maxIter] ?: d.defaultMaxIter,
        motion = p[K.motion]?.let(MotionPref::valueOf) ?: d.motion,
        haptics = p[K.haptics] ?: d.haptics,
        restoreSession = p[K.restore] ?: d.restoreSession,
    )

    override val settings: Flow<Settings> = store.data.map(::read)

    override suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val s = transform(read(p))
            p[K.theme] = s.themeMode.name
            p[K.dynamic] = s.dynamicColor
            p[K.decimals] = s.decimals
            p[K.tol] = s.defaultTolerance
            p[K.maxIter] = s.defaultMaxIter
            p[K.motion] = s.motion.name
            p[K.haptics] = s.haptics
            p[K.restore] = s.restoreSession
        }
    }

    override val draft: Flow<Draft> = store.data.map { p ->
        Draft(
            roots = p[K.lastRoot]?.let { RunCodec.decode(it) as? RunInput.Root },
            interp = p[K.lastInterp]?.let { RunCodec.decode(it) as? RunInput.Interp },
            lastModuleIsInterp = p[K.lastModule] == "interp",
        )
    }

    override suspend fun saveDraft(draft: Draft) {
        store.edit { p ->
            draft.roots?.let { p[K.lastRoot] = RunCodec.encode(it) }
            draft.interp?.let { p[K.lastInterp] = RunCodec.encode(it) }
            p[K.lastModule] = if (draft.lastModuleIsInterp) "interp" else "roots"
        }
    }
}
