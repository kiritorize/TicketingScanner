package com.ticketing.qr.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.ticketing.qr.R

class SoundManager(context: Context) {
    private var soundPool: SoundPool
    private var soundSuccessId: Int = 0
    private var soundErrorId: Int = 0
    private var loaded = false

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
            
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                loaded = true
            }
        }
        
        try {
            // Load sounds. Make sure these raw files exist.
            val successResId = context.resources.getIdentifier("sound_success", "raw", context.packageName)
            if (successResId != 0) soundSuccessId = soundPool.load(context, successResId, 1)
            
            val errorResId = context.resources.getIdentifier("sound_error", "raw", context.packageName)
            if (errorResId != 0) soundErrorId = soundPool.load(context, errorResId, 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playSuccess() {
        if (loaded && soundSuccessId != 0) {
            soundPool.play(soundSuccessId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun playError() {
        if (loaded && soundErrorId != 0) {
            soundPool.play(soundErrorId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool.release()
    }
}
