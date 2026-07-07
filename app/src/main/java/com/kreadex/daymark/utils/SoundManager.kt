import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

object SoundManager {

    private const val TAG = "SoundManager"

    var isSoundEnabled: Boolean = true

    fun updateSoundEnabled(enabled: Boolean) {
        isSoundEnabled = enabled
    }

    private var soundPool: SoundPool? = null
    private var appContext: Context? = null

    private val soundMap = HashMap<Int, Int>()
    private val pendingPlay = HashSet<Int>()
    private val readySounds = HashSet<Int>()

    fun init(context: Context) {
        if (appContext != null) return

        appContext = context.applicationContext

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build().apply {

                setOnLoadCompleteListener { _, sampleId, status ->

                    if (status == 0) {
                        readySounds.add(sampleId)

                        // если звук ждал воспроизведения — играем сразу
                        if (pendingPlay.contains(sampleId)) {
                            play(sampleId, 1f, 1f, 1, 0, 1f)
                            pendingPlay.remove(sampleId)
                        }
                    }
                }
            }
    }

    fun play(resId: Int) {
        if (!isSoundEnabled) return

        val pool = soundPool ?: return
        val ctx = appContext ?: return

        var soundId = soundMap[resId]

        // 🔥 ЕСЛИ ЕЩЁ НЕ ЗАГРУЖАЛИ → грузим автоматически
        if (soundId == null) {
            soundId = pool.load(ctx, resId, 1)
            soundMap[resId] = soundId
        }

        if (readySounds.contains(soundId)) {
            val streamId = pool.play(soundId, 1f, 1f, 1, 0, 1f)
        } else {
            pendingPlay.add(soundId)
        }
    }
}