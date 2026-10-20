package com.example.ighelper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    private fun field(root: LinearLayout, label: String, value: Int): EditText {
        root.addView(TextView(this).apply { text = label; setPadding(0, 24, 0, 0) })
        val e = EditText(this)
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.setText(value.toString())
        root.addView(e)
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = getSharedPreferences(AutoService.PREFS, MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        root.addView(TextView(this).apply { text = "IG Helper"; textSize = 26f })

        val likeLimit = field(root, "Daily like limit", p.getInt("likeLimit", 20))
        val saveLimit = field(root, "Daily save limit", p.getInt("saveLimit", 7))
        val minDelay = field(root, "Min wait per reel (seconds)", p.getInt("minDelay", 4))
        val maxDelay = field(root, "Max wait per reel (seconds)", p.getInt("maxDelay", 9))

        val swipe = Switch(this).apply {
            text = "Swipe to next reel"
            isChecked = p.getBoolean("swipe", true)
            setPadding(0, 32, 0, 32)
        }
        root.addView(swipe)

        val enable = Button(this).apply { text = "1. Enable Accessibility Service" }
        enable.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        root.addView(enable)

        val start = Button(this).apply { text = "2. Start (then open Instagram Reels)" }
        start.setOnClickListener {
            val s = AutoService.instance
            if (s == null) {
                Toast.makeText(this, "Enable IG Helper in Accessibility settings first", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                p.edit()
                    .putInt("likeLimit", likeLimit.text.toString().toIntOrNull() ?: 20)
                    .putInt("saveLimit", saveLimit.text.toString().toIntOrNull() ?: 7)
                    .putInt("minDelay", (minDelay.text.toString().toIntOrNull() ?: 4).coerceAtLeast(2))
                    .putInt("maxDelay", (maxDelay.text.toString().toIntOrNull() ?: 9).coerceAtLeast(3))
                    .putBoolean("swipe", swipe.isChecked)
                    .apply()
                s.start()
                refresh()
                Toast.makeText(this, "Running. Open Instagram Reels.", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(start)

        val stop = Button(this).apply { text = "Stop" }
        stop.setOnClickListener { AutoService.instance?.stop(); refresh() }
        root.addView(stop)

        status = TextView(this).apply { setPadding(0, 40, 0, 0); gravity = Gravity.START }
        root.addView(status)

        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val s = AutoService.instance
        status.text = "Service: " + (if (s == null) "OFF (enable in Accessibility)" else "ON") +
            "\nRunning: " + (s?.running == true) +
            "\nToday: " + AutoService.likesToday(this) + " likes, " + AutoService.savesToday(this) + " saves"
    }
}
