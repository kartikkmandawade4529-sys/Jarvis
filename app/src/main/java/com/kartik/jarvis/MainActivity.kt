package com.kartik.jarvis

import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeech
    private lateinit var output: TextView
    private val prefs by lazy { getSharedPreferences("jarvis", MODE_PRIVATE) }

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val heard = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (heard != null) handle(heard.lowercase(Locale.getDefault()).trim())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts.language = Locale("en", "IN") }

        output = TextView(this).apply { textSize = 18f; text = "Tap the button and speak" }
        val talk = Button(this).apply {
            text = "🎤 Talk to Jarvis"
            setOnClickListener { listen() }
        }
        val enable = Button(this).apply {
            text = "⚙️ Enable phone control"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(talk)
            addView(enable)
            addView(output)
        })
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
        }
        try { speechLauncher.launch(intent) } catch (e: Exception) { say("Speech input not available") }
    }

    private fun handle(cmd: String) {
        output.text = "You: $cmd"
        when {
            cmd.startsWith("open ") -> openApp(cmd.removePrefix("open ").trim())

            cmd.startsWith("call ") -> {
                val number = cmd.removePrefix("call ").filter { it.isDigit() || it == '+' }
                if (number.isEmpty()) say("Please say a phone number")
                else { say("Opening dialer"); startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
            }

            cmd.contains("alarm") -> setAlarm(cmd)

            cmd.contains("timer") -> {
                val mins = Regex("(\\d+)").find(cmd)?.value?.toIntOrNull()
                if (mins == null) say("For how many minutes?")
                else {
                    say("Timer for $mins minutes")
                    startActivity(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, mins * 60))
                }
            }

            cmd.contains("flashlight") || cmd.contains("torch") -> torch(!cmd.contains("off"))

            cmd.startsWith("search ") -> {
                val q = cmd.removePrefix("search ")
                say("Searching $q")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
            }

            cmd.startsWith("navigate to ") -> {
                val place = cmd.removePrefix("navigate to ")
                say("Navigating to $place")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place))))
            }

            cmd.startsWith("add task ") -> addTask(cmd.removePrefix("add task ").trim())
            cmd.contains("my tasks") || cmd.contains("show tasks") -> readTasks()
            cmd.contains("clear tasks") -> { prefs.edit().remove("tasks").apply(); say("Tasks cleared") }

            cmd.startsWith("click ") || cmd.startsWith("tap ") -> control {
                val t = cmd.substringAfter(" ").trim()
                say(if (it.clickText(t)) "Clicked $t" else "I can't see $t on screen")
            }
            cmd.startsWith("type ") -> control {
                say(if (it.typeText(cmd.removePrefix("type "))) "Typed" else "Tap a text box first")
            }
            cmd.contains("scroll down") -> control { it.scroll(true) }
            cmd.contains("scroll up") -> control { it.scroll(false) }
            cmd.contains("go back") -> control { it.back() }
            cmd.contains("go home") -> control { it.home() }
            cmd.contains("recent apps") -> control { it.recents() }
            cmd.contains("notifications") -> control { it.notifications() }
            cmd.contains("read screen") -> control { say(it.readScreen().ifEmpty { "Nothing to read" }) }

            else -> say("Sorry, I don't know that command yet")
        }
    }

    private fun control(block: (JarvisService) -> Unit) {
        val service = JarvisService.instance
        if (service == null) say("Please enable phone control first") else block(service)
    }

    private fun openApp(name: String) {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(launcher, 0)
            .firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
        val intent = match?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (intent != null) { say("Opening $name"); startActivity(intent) } else say("I can't find $name")
    }

    private fun setAlarm(cmd: String) {
        val m = Regex("(\\d{1,2})(?:[: ](\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?").find(cmd)
        if (m == null) { say("Which time?"); return }
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ampm = m.groupValues[3]
        if (ampm.startsWith("p") && hour < 12) hour += 12
        if (ampm.startsWith("a") && hour == 12) hour = 0
        say("Alarm set for %02d:%02d".format(hour, minute))
        startActivity(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
        )
    }

    private fun torch(on: Boolean) {
        try {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList.first(), on)
            say(if (on) "Flashlight on" else "Flashlight off")
        } catch (e: Exception) { say("Can't control the flashlight") }
    }

    private fun addTask(task: String) {
        val list = prefs.getString("tasks", "").orEmpty()
        prefs.edit().putString("tasks", if (list.isEmpty()) task else "$list\n$task").apply()
        say("Added: $task")
    }

    private fun readTasks() {
        val list = prefs.getString("tasks", "").orEmpty()
        if (list.isEmpty()) say("You have no tasks")
        else say("Your tasks: " + list.split("\n").joinToString(". "))
    }

    private fun say(text: String) {
        output.append("\nJarvis: $text")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    override fun onDestroy() { tts.shutdown(); super.onDestroy() }
}
