package com.sologo.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.sologo.app.R
import com.sologo.core.model.StoneColor

/**
 * 低延迟落子音效。SoundPool 适合短促、可重复的音效，
 * 并遵循系统的媒体音量与静音设置。
 */
class MoveSoundPlayer(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundId: Int
    private var loaded = false

    init {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            loaded = status == 0
        }
        soundId = soundPool.load(context.applicationContext, R.raw.stone_place, 1)
    }

    fun play(color: StoneColor) {
        if (!loaded) return
        // 黑白棋使用轻微音高差异，连续落子时更自然。
        val rate = if (color == StoneColor.BLACK) 0.98f else 1.02f
        soundPool.play(soundId, 0.62f, 0.62f, 1, 0, rate)
    }

    fun release() {
        loaded = false
        soundPool.release()
    }
}
