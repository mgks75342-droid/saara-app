package com.saara

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

// Continuous offline listening with Vosk. No system beep. Runs until stop() is called.
class VoiceEngine(private val modelDir: File, private val onText: (String) -> Unit, private val onError: (String) -> Unit) {

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        if (!File(modelDir, "am").exists() && !File(modelDir, "conf").exists()) {
            onError("ভয়েস মডেল পাওয়া যায়নি: ${modelDir.absolutePath}")
            return
        }
        running = true
        thread = Thread { loop() }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(2000)
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        try {
            val model = Model(modelDir.absolutePath)
            val recognizer = Recognizer(model, 16000f)
            val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
            val buf = ByteArray(minBuf)
            rec.startRecording()
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (recognizer.acceptWaveForm(buf, n)) {
                    val text = JSONObject(recognizer.result).optString("text").trim()
                    if (text.isNotEmpty()) onText(text)
                }
            }
            rec.stop(); rec.release(); recognizer.close(); model.close()
        } catch (e: Exception) {
            onError("মাইক ত্রুটি: ${e.message}")
        } finally {
            running = false
        }
    }
}
