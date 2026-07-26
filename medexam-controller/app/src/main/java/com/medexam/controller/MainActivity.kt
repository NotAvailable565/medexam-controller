package com.medexam.controller

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var packageEdit: EditText
    private lateinit var debugToggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("medexam", MODE_PRIVATE)

        statusText = findViewById(R.id.status_text)
        packageEdit = findViewById(R.id.package_edit)
        debugToggle = findViewById(R.id.debug_toggle)

        packageEdit.setText(prefs.getString("target_package", ""))

        findViewById<Button>(R.id.btn_open_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val pkg = packageEdit.text.toString().trim()
            prefs.edit().putString("target_package", pkg).apply()
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        }

        updateDebugButton()
        debugToggle.setOnClickListener {
            val cur = prefs.getBoolean("debug_mode", false)
            prefs.edit().putBoolean("debug_mode", !cur).apply()
            updateDebugButton()
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = isAccessibilityEnabled()
        statusText.text = if (enabled) "\u25CF 无障碍服务已启用" else "\u25CB 无障碍服务未启用"
        statusText.setTextColor(if (enabled) Color.parseColor("#4CAF50") else Color.parseColor("#FF5722"))
    }

    private fun updateDebugButton() {
        val prefs = getSharedPreferences("medexam", MODE_PRIVATE)
        val on = prefs.getBoolean("debug_mode", false)
        debugToggle.text = if (on) "调试模式：开" else "调试模式：关"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "${packageName}/${MedExamService::class.java.simpleName}"
        return flat.contains(target) || flat.contains("com.medexam.controller")
    }
}
