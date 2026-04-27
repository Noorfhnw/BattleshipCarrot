package ch.fhnw.vinnai.battleshipclient

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.util.Log
import com.example.battleshipcarrot.R

/**
 * Manages background music (Garden.mp3) and one-shot game sound effects.
 * Call [resume]/[pause] from Activity lifecycle and [release] in onDestroy.
 */
class SoundManager(context: Context) {

    companion object {
        private const val TAG = "SoundManager"
        private const val ATTRIBUTION_TAG = "AudioPlayback"
        private const val BACKGROUND_VOLUME = 0.1f
        private const val EFFECT_VOLUME = 1f
    }

    // Use an attributed context so AppOps can match the tag declared in the manifest
    private val attrContext: Context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.createAttributionContext(ATTRIBUTION_TAG)
    } else {
        context
    }

    private val audioAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    // Background music player (looping)
    private val musicPlayer: MediaPlayer? = try {
        MediaPlayer.create(attrContext, R.raw.garden)?.apply {
            isLooping = true
            setVolume(BACKGROUND_VOLUME, BACKGROUND_VOLUME)
            Log.d(TAG, "Background music loaded successfully")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create MediaPlayer for background music", e)
        null
    }

    // High-quality one-shot players for end-game results (avoid SoundPool resampling)
    private val winPlayer: MediaPlayer? = try {
        MediaPlayer.create(attrContext, R.raw.win_sound)?.apply {
            setVolume(EFFECT_VOLUME, EFFECT_VOLUME)
            Log.d(TAG, "Win sound loaded successfully")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create MediaPlayer for win sound", e)
        null
    }

    private val losePlayer: MediaPlayer? = try {
        MediaPlayer.create(attrContext, R.raw.lose_sound)?.apply {
            setVolume(EFFECT_VOLUME, EFFECT_VOLUME)
            Log.d(TAG, "Lose sound loaded successfully")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create MediaPlayer for lose sound", e)
        null
    }

    // SoundPool handles short SFX (dig, carrot-eat) where low latency matters
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(audioAttrs)
        .build()

    private var digSoundId: Int = 0
    private var carrotEatSoundId: Int = 0
    private var digLoaded = false
    private var carrotEatLoaded = false
    private var keepBackgroundMusicStopped = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                when (sampleId) {
                    digSoundId -> {
                        digLoaded = true
                        Log.d(TAG, "Dig sound loaded (id=$sampleId)")
                    }
                    carrotEatSoundId -> {
                        carrotEatLoaded = true
                        Log.d(TAG, "Carrot-eat sound loaded (id=$sampleId)")
                    }
                }
            } else {
                Log.e(TAG, "SoundPool load failed (id=$sampleId, status=$status)")
            }
        }
        digSoundId = soundPool.load(attrContext, R.raw.dig, 1)
        carrotEatSoundId = soundPool.load(attrContext, R.raw.carrot_eat, 1)
        Log.d(TAG, "Loading SFX... (dig=$digSoundId, carrotEat=$carrotEatSoundId)")

        // Ensure media volume is audible
        val audioManager = attrContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
        Log.d(TAG, "Current STREAM_MUSIC volume: $currentVol")
    }

    /** Play the dig sound once. */
    fun playDig() {
        if (digLoaded) {
            soundPool.play(digSoundId, EFFECT_VOLUME, EFFECT_VOLUME, 1, 0, 1f)
        } else {
            Log.w(TAG, "Dig sound not yet loaded, skipping playback")
        }
    }

    fun playCarrotEat() {
        if (carrotEatLoaded) {
            soundPool.play(carrotEatSoundId, EFFECT_VOLUME, EFFECT_VOLUME, 1, 0, 1f)
        } else {
            Log.w(TAG, "Carrot-eat sound not yet loaded, skipping playback")
        }
    }

    fun playWin() {
        keepBackgroundMusicStopped = true
        stopBackgroundMusic()
        try {
            winPlayer?.seekTo(0)
            winPlayer?.start()
            Log.d(TAG, "Playing win sound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play win sound", e)
        }
    }

    fun playLose() {
        keepBackgroundMusicStopped = true
        stopBackgroundMusic()
        try {
            losePlayer?.seekTo(0)
            losePlayer?.start()
            Log.d(TAG, "Playing lose sound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play lose sound", e)
        }
    }

    /** Resume background music. Call from onStart/onResume. */
    fun resume() {
        try {
            if (!keepBackgroundMusicStopped) {
                musicPlayer?.start()
                Log.d(TAG, "Background music resumed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume background music", e)
        }
    }

    /** Pause background music. Call from onStop/onPause. */
    fun pause() {
        stopBackgroundMusic()
    }

    private fun stopBackgroundMusic() {
        try {
            if (musicPlayer?.isPlaying == true) {
                musicPlayer.pause()
                Log.d(TAG, "Background music paused")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause background music", e)
        }
    }

    /** Release all resources. Call from onDestroy. */
    fun release() {
        for (player in listOf(musicPlayer, winPlayer, losePlayer)) {
            try { player?.stop(); player?.release() } catch (_: Exception) { }
        }
        try {
            soundPool.release()
            Log.d(TAG, "SoundPool released")
        } catch (_: Exception) { }
    }
}


