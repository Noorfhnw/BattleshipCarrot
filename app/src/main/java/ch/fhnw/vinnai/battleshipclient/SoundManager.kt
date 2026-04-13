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
 * Manages background music (Garden.mp3) and the dig sound effect (dig.ogg).
 * Call [resume]/[pause] from Activity lifecycle and [release] in onDestroy.
 */
class SoundManager(context: Context) {

    companion object {
        private const val TAG = "SoundManager"
        private const val ATTRIBUTION_TAG = "AudioPlayback"
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

    private val mediaPlayer: MediaPlayer? = try {
        MediaPlayer.create(attrContext, R.raw.garden)?.apply {
            isLooping = true
            setVolume(0.45f, 0.45f)
            Log.d(TAG, "Background music loaded successfully")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create MediaPlayer for background music", e)
        null
    }

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(audioAttrs)
        .build()

    private var digSoundId: Int = 0
    private var digLoaded = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                Log.d(TAG, "Dig sound loaded successfully (id=$sampleId)")
                digLoaded = true
            } else {
                Log.e(TAG, "Dig sound failed to load (status=$status)")
            }
        }
        digSoundId = soundPool.load(attrContext, R.raw.dig, 1)
        Log.d(TAG, "Loading dig sound... (id=$digSoundId)")

        // Ensure media volume is audible
        val audioManager = attrContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
        Log.d(TAG, "Current STREAM_MUSIC volume: $currentVol")
    }

    /** Play the dig sound once. */
    fun playDig() {
        if (digLoaded) {
            val streamId = soundPool.play(digSoundId, 1f, 1f, 1, 0, 1f)
            Log.d(TAG, "Playing dig sound (streamId=$streamId)")
        } else {
            Log.w(TAG, "Dig sound not yet loaded, skipping playback")
        }
    }

    /** Resume background music. Call from onStart/onResume. */
    fun resume() {
        try {
            mediaPlayer?.start()
            Log.d(TAG, "Background music resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume background music", e)
        }
    }

    /** Pause background music. Call from onStop/onPause. */
    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer.pause()
                Log.d(TAG, "Background music paused")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause background music", e)
        }
    }

    /** Release all resources. Call from onDestroy. */
    fun release() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            Log.d(TAG, "MediaPlayer released")
        } catch (_: Exception) { }
        try {
            soundPool.release()
            Log.d(TAG, "SoundPool released")
        } catch (_: Exception) { }
    }
}


