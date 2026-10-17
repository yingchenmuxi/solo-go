package com.sologo.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sologo.app.FeatureFlags
import com.sologo.core.model.StoneColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "solo_go_settings")

data class AppSettings(
    val privacyAccepted: Boolean = false,
    val privacyDecisionMade: Boolean = false,
    val premiumUnlocked: Boolean = false,
    val adUnlockUntilEpochMs: Long = 0L,
    val boardSize: Int = 19,
    val komi: Float = 7.5f,
    val maxVisits: Int = 60,
    val autoAnalyze: Boolean = false,
    val humanColor: StoneColor = StoneColor.BLACK
) {
    /** 一次性买断 + 激励视频临时解锁，都视为高级分析可用。 */
    fun hasAdvancedAccess(now: Long = System.currentTimeMillis()): Boolean =
        FeatureFlags.unlockAllForTesting || premiumUnlocked || adUnlockUntilEpochMs > now
}

/**
 * 所有持久化偏好集中在这里。隐私同意状态是广告 SDK 初始化的唯一开关。
 */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val privacyAccepted = booleanPreferencesKey("privacy_accepted")
        val privacyDecisionMade = booleanPreferencesKey("privacy_decision_made")
        val premiumUnlocked = booleanPreferencesKey("premium_unlocked")
        val adUnlockUntil = longPreferencesKey("ad_unlock_until")
        val boardSize = intPreferencesKey("board_size")
        val komi = floatPreferencesKey("komi")
        val maxVisits = intPreferencesKey("max_visits")
        val autoAnalyze = booleanPreferencesKey("auto_analyze")
        val humanColor = stringPreferencesKey("human_color")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { throwable ->
            if (throwable is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw throwable
        }
        .map { preferences ->
            AppSettings(
                privacyAccepted = preferences[Keys.privacyAccepted] ?: false,
                privacyDecisionMade = preferences[Keys.privacyDecisionMade] ?: false,
                premiumUnlocked = preferences[Keys.premiumUnlocked] ?: false,
                adUnlockUntilEpochMs = preferences[Keys.adUnlockUntil] ?: 0L,
                boardSize = (preferences[Keys.boardSize] ?: 19).coerceIn(9, 19),
                komi = preferences[Keys.komi] ?: 7.5f,
                maxVisits = (preferences[Keys.maxVisits] ?: 60).coerceIn(20, 4000),
                autoAnalyze = preferences[Keys.autoAnalyze] ?: false,
                humanColor = runCatching {
                    StoneColor.valueOf(preferences[Keys.humanColor] ?: StoneColor.BLACK.name)
                }.getOrDefault(StoneColor.BLACK)
            )
        }

    suspend fun setPrivacyChoice(accepted: Boolean) {
        store.edit {
            it[Keys.privacyAccepted] = accepted
            it[Keys.privacyDecisionMade] = true
        }
    }

    suspend fun setPremiumUnlocked(value: Boolean) {
        store.edit { it[Keys.premiumUnlocked] = value }
    }

    suspend fun grantAdUnlock(hours: Int = 24) {
        store.edit {
            it[Keys.adUnlockUntil] = System.currentTimeMillis() + hours * 60L * 60L * 1000L
        }
    }

    suspend fun setBoardSize(value: Int) {
        store.edit { it[Keys.boardSize] = value.coerceIn(9, 19) }
    }

    suspend fun setKomi(value: Float) {
        store.edit { it[Keys.komi] = value }
    }

    suspend fun setMaxVisits(value: Int) {
        store.edit { it[Keys.maxVisits] = value.coerceIn(20, 4000) }
    }

    suspend fun setAutoAnalyze(value: Boolean) {
        store.edit { it[Keys.autoAnalyze] = value }
    }

    suspend fun setHumanColor(value: StoneColor) {
        store.edit { it[Keys.humanColor] = value.name }
    }

    suspend fun resetPrivacyDecision() {
        store.edit {
            it[Keys.privacyAccepted] = false
            it[Keys.privacyDecisionMade] = false
        }
    }
}
