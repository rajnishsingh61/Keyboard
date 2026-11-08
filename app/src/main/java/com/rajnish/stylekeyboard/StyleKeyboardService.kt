package com.rajnish.stylekeyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*

private val BG = Color.parseColor("#111113")
private val KEY = Color.parseColor("#1C1C20")
private val KEY_ALT = Color.parseColor("#26262C")
private val ACCENT = Color.parseColor("#7C6CFF")
private val TEXT = Color.parseColor("#EDEDF0")
private val MUTED = Color.parseColor("#8E8E98")

class StyleKeyboardService : InputMethodService() {

    private var style = Style.BOLD
    private var shift = 0               // 0 off, 1 once, 2 caps lock
    private var lastShiftTap = 0L
    private var symbols = false
    private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val symbolRows = listOf("1234567890", "@#$%&-+()", "*\"':;!?/")

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var rowsBox: LinearLayout
    private lateinit var spaceKey: TextView
    private var shiftView: TextView? = null
    private val keyViews = mutableListOf<Pair<Char, TextView>>()
    private val chips = mutableMapOf<Style, TextView>()

    private val repeatDelete = object : Runnable {
        override fun run() { backspace(); handler.postDelayed(this, 45) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- drawing helpers ----------
    private fun bg(color: Int, radiusDp: Int): RippleDrawable {
        val r = dp(radiusDp).toFloat()
        val shape = GradientDrawable().apply { setColor(color); cornerRadius = r }
        val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = r }
        return RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, mask)
    }

    private fun cell(label: String, weight: Float, fill: Int, color: Int = TEXT, size: Float = 18f) =
        TextView(this).apply {
            text = label; textSize = size; setTextColor(color)
            gravity = Gravity.CENTER; background = bg(fill, 10); isClickable = true
            layoutParams = LinearLayout.LayoutParams(0, dp(48), weight).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }

    private fun View.onTap(action: () -> Unit) = setOnClickListener {
        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        action()
    }

    private fun newRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
    }

    // ---------- view ----------
    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(5), dp(8), dp(5), dp(10))
        }
        root.addView(buildStyleBar())
        rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rowsBox)
        buildKeys()
        paintChips()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::rowsBox.isInitialized) { shift = 0; symbols = false; buildKeys() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        handler.removeCallbacks(repeatDelete)
        super.onFinishInputView(finishingInput)
    }

    private fun chipLabel(st: Style) =
        if (st == Style.EMOJIFY) "🅰 Emoji" else TextStyler.apply(st.label, st)

    private fun buildStyleBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), 0, dp(2), dp(6))
        }
        Style.values().forEach { st ->
            val chip = TextView(this).apply {
                text = chipLabel(st); textSize = 14f; gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34))
                    .apply { setMargins(dp(3), 0, dp(3), 0) }
                onTap { selectStyle(st) }
            }
            chips[st] = chip; row.addView(chip)
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun paintChips() {
        chips.forEach { (st, tv) ->
            val sel = st == style
            tv.background = bg(if (sel) ACCENT else KEY, 17)
            tv.setTextColor(if (sel) Color.WHITE else MUTED)
        }
    }

    private fun buildKeys() {
        rowsBox.removeAllViews(); keyViews.clear(); shiftView = null
        val rows = if (symbols) symbolRows else letterRows
        rows.forEachIndexed { i, r ->
            val row = newRow()
            if (i == 2 && !symbols) {
                val s = cell("⇧", 1.5f, KEY_ALT, size = 20f)
                s.onTap { toggleShift() }
                shiftView = s; row.addView(s)
            }
            r.forEach { ch ->
                val v = cell(display(ch), 1f, KEY)
                v.onTap { typeChar(ch) }
                keyViews.add(ch to v); row.addView(v)
            }
            if (i == 2) row.addView(backspaceKey())
            rowsBox.addView(row)
        }
        val bottom = newRow()
        bottom.addView(cell(if (symbols) "ABC" else "123", 1.4f, KEY_ALT, MUTED, 14f).also {
            it.onTap { symbols = !symbols; buildKeys() }
        })
        bottom.addView(cell(",", 1f, KEY_ALT).also { it.onTap { commit(",") } })
        spaceKey = cell(style.label, 4f, KEY, MUTED, 12f)
        spaceKey.onTap { commit(" ") }
        bottom.addView(spaceKey)
        bottom.addView(cell(".", 1f, KEY_ALT).also { it.onTap { commit(".") } })
        bottom.addView(cell("Aa→", 1.4f, KEY_ALT, MUTED, 14f).also { it.onTap { convertSelection() } })
        bottom.addView(cell("↵", 1.4f, ACCENT, Color.WHITE, 20f).also { it.onTap { enter() } })
        rowsBox.addView(bottom)
        updateShiftView()
    }

    private fun backspaceKey(): TextView {
        val v = cell("⌫", 1.5f, KEY_ALT, size = 18f)
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    backspace()
                    handler.postDelayed(repeatDelete, 380)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    handler.removeCallbacks(repeatDelete)
                }
            }
            true
        }
        return v
    }

    // ---------- state ----------
    private fun display(ch: Char): String {
        val c = if (shift != 0) ch.uppercaseChar() else ch
        return if (style == Style.UNDERLINE || style == Style.STRIKE) c.toString()
        else TextStyler.apply(c.toString(), style).trim()
    }

    private fun updateLabels() {
        keyViews.forEach { (ch, tv) -> tv.text = display(ch) }
        updateShiftView()
    }

    private fun updateShiftView() {
        shiftView?.apply {
            text = if (shift == 2) "⇪" else "⇧"
            background = bg(if (shift == 0) KEY_ALT else ACCENT, 10)
            setTextColor(if (shift == 0) TEXT else Color.WHITE)
        }
    }

    private fun selectStyle(st: Style) {
        style = st
        paintChips(); updateLabels()
        if (::spaceKey.isInitialized) spaceKey.text = st.label
    }

    private fun toggleShift() {
        val now = System.currentTimeMillis()
        shift = when {
            now - lastShiftTap < 300 -> 2          // double tap = caps lock
            shift == 0 -> 1
            else -> 0
        }
        lastShiftTap = now
        updateLabels()
    }

    // ---------- input ----------
    private fun commit(s: String) { currentInputConnection?.commitText(s, 1) }

    private fun typeChar(ch: Char) {
        val c = if (shift != 0) ch.uppercaseChar() else ch
        commit(TextStyler.apply(c.toString(), style))
        if (shift == 1) { shift = 0; updateLabels() }
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) { ic.commitText("", 1); return }
        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val n = if (before.endsWith("\u0332") || before.endsWith("\u0336")) 2 else 1
        ic.deleteSurroundingTextInCodePoints(n, 0)
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    /** Selected text (ya poora text) ko current style me convert karta hai. */
    private fun convertSelection() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)?.toString()
        if (!sel.isNullOrEmpty()) { ic.commitText(TextStyler.apply(sel, style), 1); return }
        val before = ic.getTextBeforeCursor(1000, 0)?.toString().orEmpty()
        if (before.isNotEmpty()) {
            ic.deleteSurroundingText(before.length, 0)
            ic.commitText(TextStyler.apply(before, style), 1)
        }
    }
}
