package io.pedalcheck.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import java.lang.ref.WeakReference

/** One process-wide speaker: the overlay takes priority over the visible Activity. */
object WarningAudio {
    private val owners = linkedMapOf<Any, Attention>()
    private var context: WeakReference<Context>? = null
    private var cadence = WarningCadence()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    private fun speaker() = owners.keys.lastOrNull { it is PedalOverlayService } ?: owners.keys.lastOrNull()
    fun attach(owner: Any, context: Context) { this.context = WeakReference(context.applicationContext); owners[owner] = Attention.NONE }
    fun detach(owner: Any) {
        val wasSpeaker = speaker() === owner
        owners.remove(owner)
        if (wasSpeaker) { stop(); cadence = WarningCadence(); refresh() }
        if (owners.isEmpty()) context = null
    }
    fun update(owner: Any, attention: Attention) {
        if (!owners.containsKey(owner)) return
        owners[owner] = attention
        if (speaker() === owner) refresh()
    }
    fun refresh() {
        val ctx = context?.get() ?: return
        val prefs = ctx.getSharedPreferences("display", Context.MODE_PRIVATE)
        val attention = if (prefs.getBoolean("voice", true) && prefs.getBoolean("warnings", true)) owners[speaker()] ?: Attention.NONE else Attention.NONE
        val announce = cadence.update(attention, SystemClock.elapsedRealtime())
        if (attention == Attention.NONE) { stop(); return }
        if (!announce) return
        stop()
        val resource = when (attention) {
            Attention.SIMULTANEOUS -> R.raw.warning_simultaneous
            Attention.FULL_ACCELERATOR -> R.raw.warning_full
            Attention.HIGH_SPEED_FULL -> R.raw.warning_speed
            Attention.NONE -> return
        }
        val manager = ctx.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
            }.build()
        focus = request
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { stop(); return }
        try {
            player = MediaPlayer.create(ctx, resource, attributes, 0)?.apply {
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ -> stop(); true }
                start()
                Log.i("PedalWarningAudio", "playing $attention (bundled Korean audio)")
            }
            if (player == null) stop()
        } catch (error: RuntimeException) { Log.e("PedalWarningAudio", "playback failed", error); stop() }
    }
    private fun stop() {
        player?.release(); player = null
        focus?.let { context?.get()?.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }; focus = null
    }
}
