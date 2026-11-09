package com.saara

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var log: TextView
    private lateinit var input: EditText
    private val ui = Handler(Looper.getMainLooper())
    private var pendingYT: String? = null
    private var listening = false
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var micBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(Button(this).apply { text = "অ্যাক্সেসিবিলিটি"; setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }})
        top.addView(Button(this).apply { text = "Gemini key"; setOnClickListener { showKeyDialog() } })
        micBtn = Button(this).apply { text = "🎤 মাইক: বন্ধ"; setOnClickListener { toggleMic() } }
        top.addView(micBtn)
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale("bn", "BD"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts?.language = Locale.ENGLISH
        }}
        root.addView(top)

        val scroll = ScrollView(this)
        log = TextView(this).apply { textSize = 15f; setPadding(8, 8, 8, 8)
            movementMethod = ScrollingMovementMethod.getInstance() }
        scroll.addView(log)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply { hint = "Saara-কে লিখে বলুন..." }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply { text = "পাঠাও"; setOnClickListener { send() } })
        root.addView(row)

        setContentView(root)
        say("হ্যাঁ, বলো। কী করতে হবে লিখুন।")
    }

    private fun say(s: String) {
        log.append("Saara: $s\n\n")
        if (listening) tts?.speak(s, TextToSpeech.QUEUE_ADD, null, "s${System.nanoTime()}")
    }

    private fun me(s: String) {
        log.append("আপনি: $s\n")
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        me(text)
        Thread { handle(text) }.start()
    }

    private fun post(s: String) = ui.post { say(s) }

    private fun handle(cmd: String) {
        val key = getSharedPreferences("saara", MODE_PRIVATE).getString("gemini_key", "") ?: ""

        // Yes / no for pending YouTube result
        if (pendingYT != null) {
            if (cmd.contains("হ্যাঁ") || cmd.equals("yes", true)) {
                pendingYT = null
                val ok = SaraAccessibility.instance?.clickFirstLongTitle() == true
                post(if (ok) "ঠিক আছে, ভিডিওটা খুলছি" else "স্ক্রিনে ভিডিও পেলাম না। অ্যাক্সেসিবিলিটি চালু আছে কিনা দেখুন।")
                return
            } else if (cmd.contains("না")) {
                pendingYT = null
                post("ঠিক আছে, বাতিল করলাম")
                return
            }
        }

        when {
            cmd.contains("ইউটিউবে") && (cmd.contains("সার্চ") || cmd.contains("খোঁজ") || cmd.contains("খুঁজ")) -> {
                val q = cmd.replace("ইউটিউবে", "").replace("সার্চ করো", "").replace("সার্চ", "")
                    .replace("খুঁজে দাও", "").replace("খোঁজো", "").replace("খুঁজো", "").trim()
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                post("ইউটিউবে \"$q\" সার্চ করেছি। একটু অপেক্ষা করুন।")
                Thread.sleep(5000)
                val screen = SaraAccessibility.screenText()
                val firstWord = q.split(" ").firstOrNull { it.length > 1 } ?: q
                if (screen.contains(firstWord, ignoreCase = true)) {
                    pendingYT = q
                    post("পেয়েছি। ভিডিওটা দেখতে চান? হ্যাঁ বা না বলুন।")
                } else {
                    post("স্ক্রিন থেকে নিশ্চিত হতে পারলাম না। অ্যাক্সেসিবিলিটি চালু আছে কিনা দেখুন।")
                }
            }
            cmd.contains("স্ক্রিনে") || cmd.contains("এখানে কী") || cmd.contains("কী হয়েছে") -> {
                val screen = SaraAccessibility.screenText()
                if (screen.isBlank()) { post("স্ক্রিন পড়তে পারছি না। অ্যাক্সেসিবিলিটি চালু করুন।"); return }
                post(Gemini.ask(key, "এই স্ক্রিনের লেখা থেকে সংক্ষেপে বলো এখানে কী হয়েছে বা কী দেখা যাচ্ছে:\n$screen"))
            }
            cmd.contains("কোড") && (cmd.contains("দেখ") || cmd.contains("চেক") || cmd.contains("সমস্যা")) -> {
                val screen = SaraAccessibility.screenText()
                if (screen.isBlank()) { post("কোড পড়তে পারছি না। কোডটা এখানে পেস্ট করুন।"); return }
                post(Gemini.ask(key, "এই কোডে কোনো সমস্যা আছে কিনা বাংলায় বলো, ভুল থাকলে ঠিক করে দাও:\n$screen"))
            }
            cmd.startsWith("খোলো") || cmd.startsWith("ওপেন") -> openApp(cmd.replace("খোলো", "").replace("ওপেন", "").trim())
            cmd.contains("সময়") -> post("এখন " + java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(java.util.Date()))
            cmd.contains("তারিখ") -> post("আজ " + java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date()))
            cmd.contains("ডিলিট") || cmd.contains("মুছে") -> {
                val name = cmd.replace("ডিলিট করো", "").replace("ডিলিট", "").replace("মুছে ফেলো", "").trim()
                val f = File("/storage/emulated/0/Download", name)
                if (name.isNotBlank() && f.exists() && f.delete()) post("${f.name} মুছে দিয়েছি") else post("ডাউনলোডে $name নামে ফাইল পাইনি বা মুছতে পারিনি")
            }
            else -> post(Gemini.ask(key, cmd))
        }
    }

    private fun openApp(name: String) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        val m = apps.firstOrNull { pm.getApplicationLabel(it).toString().equals(name, true) }
            ?: apps.firstOrNull { pm.getApplicationLabel(it).toString().contains(name, true) && name.isNotBlank() }
        val launch = m?.let { pm.getLaunchIntentForPackage(it.packageName) }
        if (launch == null) { post("$name অ্যাপটা পাইনি"); return }
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        post("${pm.getApplicationLabel(m)} খুলছি")
    }

    private fun toggleMic() {
        if (listening) { stopMic(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 42)
            return
        }
        listening = true
        micBtn.text = "🎤 মাইক: চালু (বন্ধ করতে চাপুন)"
        post("মাইক চালু। এখন থেকে শুনছি, বন্ধ করতে বোতামে চাপুন।")
        listenOnce()
    }

    private fun stopMic() {
        listening = false
        micBtn.text = "🎤 মাইক: বন্ধ"
        ui.post { recognizer?.cancel(); recognizer?.destroy(); recognizer = null }
        post("মাইক বন্ধ করলাম।")
    }

    private fun listenOnce() {
        ui.post {
            if (!listening) return@post
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    if (text.isNotBlank()) { me(text); Thread { handle(text); ui.post { restartIfOn() } }.start() }
                    else restartIfOn()
                }
                override fun onError(error: Int) { restartIfOn(800) }
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            r.startListening(intent)
        }
    }

    private fun restartIfOn(delay: Long = 300) {
        if (!listening) return
        ui.postDelayed({ listenOnce() }, delay)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleMic()
        else post("মাইকের অনুমতি দিন, তারপর আবার চাপুন।")
    }

    override fun onDestroy() {
        stopMic()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun showKeyDialog() {
        val e = EditText(this).apply {
            hint = "Gemini API key"
            setText(getSharedPreferences("saara", MODE_PRIVATE).getString("gemini_key", ""))
        }
        android.app.AlertDialog.Builder(this).setTitle("Gemini API key").setView(e)
            .setPositiveButton("সেভ") { _, _ ->
                getSharedPreferences("saara", MODE_PRIVATE).edit().putString("gemini_key", e.text.toString().trim()).apply()
            }
            .setNegativeButton("বাতিল", null).show()
    }
}
