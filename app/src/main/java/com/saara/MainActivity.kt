package com.saara

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var micBtn: Button
    private val ui = Handler(Looper.getMainLooper())
    private var voice: VoiceEngine? = null
    private var micOn = false
    private var tts: TextToSpeech? = null
    private var pendingYT: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(8, 8, 8, 8) }
        micBtn = Button(this).apply { text = "🎤 মাইক: বন্ধ"; setOnClickListener { toggleMic() } }
        top.addView(micBtn)
        top.addView(Button(this).apply { text = "Gemini key"; setOnClickListener { showKeyDialog() } })
        top.addView(Button(this).apply { text = "অ্যাক্সেসিবিলিটি"; setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
        root.addView(top)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "SaraNative")
        web.loadUrl("file:///android_asset/index.html")
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale("bn", "BD"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts?.language = Locale("hi", "IN")
        }}

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 42)
        }
    }

    // index.html can call SaraNative.toggleMic() from the Speak button
    inner class Bridge {
        @JavascriptInterface fun toggleMic() { ui.post { toggleMic() } }
        @JavascriptInterface fun isMicOn(): Boolean = micOn
    }

    private fun toggleMic() {
        if (micOn) { stopMic(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 42)
            return
        }
        val dir = File(getExternalFilesDir(null), "model")
        voice = VoiceEngine(dir, { text -> ui.post { handleVoice(text) } }, { err -> ui.post { say(err) } })
        voice?.start()
        micOn = true
        micBtn.text = "🎤 মাইক: চালু"
        say("মাইক চালু। এখন থেকে শুনছি।")
    }

    private fun stopMic() {
        voice?.stop(); voice = null
        micOn = false
        micBtn.text = "🎤 মাইক: বন্ধ"
    }

    private fun handleVoice(text: String) {
        Thread { handleCommand(text) }.start()
    }

    private fun say(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
        tts?.speak(s, TextToSpeech.QUEUE_ADD, null, "s${System.nanoTime()}")
    }

    private fun postSay(s: String) = ui.post { say(s) }

    private fun handleCommand(cmd: String) {
        val key = getSharedPreferences("saara", MODE_PRIVATE).getString("gemini_key", "") ?: ""
        pendingYT?.let { q ->
            pendingYT = null
            if (cmd.contains("হ্যাঁ") || cmd.equals("yes", true)) {
                val ok = SaraAccessibility.instance?.clickFirstLongTitle() == true
                postSay(if (ok) "ভিডিওটা খুলছি" else "ভিডিও খুলতে পারলাম না, অ্যাক্সেসিবিলিটি চালু আছে কি দেখুন")
                return
            }
            if (cmd.contains("না")) { postSay("ঠিক আছে"); return }
        }
        when {
            cmd.contains("ইউটিউবে") && (cmd.contains("সার্চ") || cmd.contains("খোঁজ") || cmd.contains("খুঁজ")) -> {
                val q = cmd.replace("ইউটিউবে", "").replace("সার্চ করো", "").replace("সার্চ", "")
                    .replace("খুঁজে দাও", "").replace("খোঁজো", "").replace("খুঁজো", "").trim()
                ui.post { startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                Thread.sleep(5000)
                val screen = SaraAccessibility.screenText()
                val first = q.split(" ").firstOrNull { it.length > 1 } ?: q
                if (screen.contains(first, true)) { pendingYT = q; postSay("পেয়েছি। ভিডিওটা দেখতে চান? হ্যাঁ বা না বলুন") }
                else postSay("স্ক্রিনে নিশ্চিত হতে পারলাম না")
            }
            cmd.contains("স্ক্রিনে") || cmd.contains("কী হয়েছে") -> {
                val screen = SaraAccessibility.screenText()
                if (screen.isBlank()) postSay("স্ক্রিন পড়তে পারছি না, অ্যাক্সেসিবিলিটি চালু করুন")
                else postSay(Gemini.ask(key, "এই স্ক্রিনের লেখা থেকে সংক্ষেপে বলো কী দেখা যাচ্ছে:\n$screen"))
            }
            cmd.startsWith("খোলো") || cmd.startsWith("ওপেন") -> openApp(cmd.replace("খোলো", "").replace("ওপেন", "").trim())
            cmd.contains("সময়") -> postSay("এখন " + java.text.SimpleDateFormat("h টা mm মিনিট", Locale("bn")).format(java.util.Date()))
            cmd.contains("তারিখ") -> postSay("আজ " + java.text.SimpleDateFormat("d MMMM yyyy", Locale("bn")).format(java.util.Date()))
            cmd.contains("ডিলিট") || cmd.contains("মুছে") -> {
                val name = cmd.replace("ডিলিট করো", "").replace("ডিলিট", "").replace("মুছে ফেলো", "").trim()
                val f = File("/storage/emulated/0/Download", name)
                postSay(if (name.isNotBlank() && f.exists() && f.delete()) "${f.name} মুছে দিয়েছি" else "ফাইলটা পাইনি বা মুছতে পারিনি")
            }
            else -> postSay(Gemini.ask(key, cmd))
        }
    }

    private fun openApp(name: String) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        val m = apps.firstOrNull { pm.getApplicationLabel(it).toString().equals(name, true) }
            ?: apps.firstOrNull { name.isNotBlank() && pm.getApplicationLabel(it).toString().contains(name, true) }
        val launch = m?.let { pm.getLaunchIntentForPackage(it.packageName) }
        if (launch == null) { postSay("$name অ্যাপটা পাইনি"); return }
        ui.post { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        postSay("${pm.getApplicationLabel(m)} খুলছি")
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

    override fun onDestroy() {
        stopMic()
        tts?.shutdown()
        super.onDestroy()
    }
}
