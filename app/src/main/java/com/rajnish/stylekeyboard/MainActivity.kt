package com.rajnish.stylekeyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val bgC = Color.parseColor("#111113")
    private val keyC = Color.parseColor("#1C1C20")
    private val accent = Color.parseColor("#7C6CFF")
    private val textC = Color.parseColor("#EDEDF0")
    private val muted = Color.parseColor("#8E8E98")

    private lateinit var step1: TextView
    private lateinit var step2: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int): RippleDrawable {
        val shape = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
        val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(r).toFloat() }
        return RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, mask)
    }

    private fun button(onClick: () -> Unit) = TextView(this).apply {
        textSize = 16f; gravity = Gravity.CENTER; isClickable = true
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
            .apply { topMargin = dp(12) }
        setOnClickListener { onClick() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = bgC
        window.navigationBarColor = bgC

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgC)
            setPadding(dp(28), dp(72), dp(28), dp(28))
        }
        root.addView(TextView(this).apply {
            text = "Style Keyboard"; textSize = 30f; setTextColor(textC)
        })
        root.addView(TextView(this).apply {
            text = "𝗕𝗼𝗹𝗱  𝘐𝘵𝘢𝘭𝘪𝘤  𝓟𝓻𝓮𝓶𝓲𝓾𝓶  U̲n̲d̲e̲r̲l̲i̲n̲e̲"
            textSize = 15f; setTextColor(muted); setPadding(0, dp(8), 0, dp(28))
        })

        step1 = button { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        step2 = button {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        root.addView(step1); root.addView(step2)

        root.addView(EditText(this).apply {
            hint = "Yahan type karke test karo"
            setHintTextColor(muted); setTextColor(textC); textSize = 16f
            background = rounded(keyC, 14)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(28) }
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val cur = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = cur.startsWith("$packageName/")
        paint(step1, "1   Enable keyboard", enabled, true)
        paint(step2, "2   Select keyboard", selected, enabled)
    }

    private fun paint(v: TextView, label: String, done: Boolean, active: Boolean) {
        v.text = if (done) "✓  $label" else label
        v.background = rounded(if (done || !active) keyC else accent, 14)
        v.setTextColor(if (done) muted else if (active) Color.WHITE else muted)
    }
}
