package com.vrgames.deepsea.render

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/**
 * v0.5.2 (device 2026-11-19: no sense of moving in the cabin): the sub's motor heard in the cabin, a low looping hum
 * (assets/audio/motor.wav, the 4-second hum loop made for 引力弹弓 by its tools/sfx.py, no new download) that grows
 * louder ([QUIET]..[LOUD]) and higher ([SLOW]..[FAST] times its pitch) with the sub's speed.
 */
class Motor(context: Context) {
    private val pool: SoundPool = SoundPool.Builder().setMaxStreams(1)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private var sound = 0
    private var stream = 0
    private var loaded = false
    private var running = false
    private var volume = QUIET
    private var rate = SLOW
    private var shownVolume = -1f
    private var shownRate = -1f

    init {
        pool.setOnLoadCompleteListener { _, _, status ->
            loaded = status == 0
            Log.i(TAG, "motor loop ${if (loaded) "loaded" else "failed ($status)"}")
            apply()
        }
        runCatching { context.assets.openFd(ASSET).use { sound = pool.load(it, 1) } }.onFailure { Log.i(TAG, "no motor sound: ${it.message}") }
    }

    /** The sub's speed as a share of its top speed (0..1). */
    fun speed(share: Double) {
        val f = share.coerceIn(0.0, 1.0).toFloat()
        volume = QUIET + (LOUD - QUIET) * f
        rate = SLOW + (FAST - SLOW) * f
        if (stream == 0) return
        if (kotlin.math.abs(volume - shownVolume) > 0.01f) { shownVolume = volume; pool.setVolume(stream, volume, volume) }
        if (kotlin.math.abs(rate - shownRate) > 0.01f) { shownRate = rate; pool.setRate(stream, rate) }
    }

    fun resume() { running = true; apply() }

    fun pause() { running = false; apply() }

    fun release() {
        running = false
        runCatching { pool.release() }
        stream = 0
    }

    private fun apply() {
        if (!loaded) return
        if (running) {
            if (stream == 0) {
                stream = pool.play(sound, volume, volume, 1, -1, rate)
                shownVolume = volume; shownRate = rate
            } else pool.resume(stream)
        } else if (stream != 0) pool.pause(stream)
    }

    companion object {
        private const val TAG = "DeepMotor"
        private const val ASSET = "audio/motor.wav"
        const val QUIET = 0.1f
        const val LOUD = 0.6f
        const val SLOW = 0.85f
        const val FAST = 1.25f
    }
}
