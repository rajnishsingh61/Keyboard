package com.rajnish.stylekeyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.Locale
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

// ---------- palette (system dark/light follow karta hai) ----------
private fun c(s: String) = Color.parseColor(s)

private class Pal(
    val bg: Int, val key: Int, val fn: Int, val accent: Int,
    val text: Int, val muted: Int, val pop: Int, val ripple: Int
)

private val DARK = Pal(c("#131316"), c("#2A2A30"), c("#1D1D22"), c("#7C6CFF"),
    c("#EDEDF0"), c("#9A9AA5"), c("#34343B"), 0x33FFFFFF)
private val LIGHT = Pal(c("#E3E5E8"), c("#FFFFFF"), c("#CDD0D5"), c("#6C5CE7"),
    c("#202124"), c("#5F6368"), c("#FFFFFF"), 0x22000000)

// ---------- long-press alternates (Gboard jaisa) ----------
private fun l(s: String) = s.split(" ")

private val ALTS: Map<String, List<String>> = mapOf(
    "a" to l("à á â ä æ ã å ā"), "c" to l("ç ć č"), "e" to l("è é ê ë ē ė ę"),
    "i" to l("î ï í ī į ì"), "l" to l("ł"), "n" to l("ñ ń"), "o" to l("ô ö ò ó œ ø ō õ"),
    "s" to l("ß ś š"), "u" to l("û ü ù ú ū"), "y" to l("ÿ"), "z" to l("ž ź ż"),
    "1" to l("¹ ½ ⅓ ¼ ⅛"), "2" to l("² ⅔"), "3" to l("³ ¾ ⅜"), "4" to l("⁴"),
    "5" to l("⅝"), "7" to l("⅞"), "0" to l("ⁿ ∅"),
    "-" to l("_ – — •"), "." to l("… ! ? , : ; ' \""),
    "?" to l("¿"), "!" to l("¡"), "'" to l("‘ ’ ‚"), "\"" to l("“ ” „ « »"),
    "$" to l("₹ £ ¢ € ¥ ₩"), "/" to l("\\"), "%" to l("‰"), "=" to l("≠ ≈ ∞"),
    "+" to l("± ×"), "(" to l("[ { <"), ")" to l("] } >"), "&" to l("§"), "*" to l("† ‡ ★")
)

// ---------- emoji: device ke supported saare emoji khud generate hote hain ----------
private object EmojiData {
    class Cat(val icon: String, val items: List<String>)

    @Volatile private var cache: List<Cat>? = null

    private fun bucket(cp: Int): Int = when {
        cp in 0x1F600..0x1F644 || cp in 0x1F910..0x1F925 || cp in 0x1F927..0x1F92F ||
            cp in 0x1F970..0x1F97A -> 0
        cp in 0x1F440..0x1F450 || cp in 0x1F466..0x1F487 || cp in 0x1F645..0x1F64F || cp == 0x1F926 ||
            cp in 0x1F930..0x1F93E || cp in 0x1F9B0..0x1F9BF || cp in 0x1F9D0..0x1F9DF || cp == 0x261D ||
            cp in 0x270A..0x270D || cp in 0x1F90C..0x1F90F || cp == 0x1F4AA -> 1
        cp in 0x1F400..0x1F43F || cp in 0x1F980..0x1F9AF || cp in 0x1F330..0x1F344 ||
            cp == 0x1F490 || cp == 0x1F940 -> 2
        cp in 0x1F345..0x1F37F || cp in 0x1F950..0x1F96F || cp in 0x1F9C0..0x1F9CB || cp == 0x2615 -> 3
        cp in 0x1F380..0x1F3D3 || cp in 0x1F3F8..0x1F3FA || cp in 0x1F94A..0x1F94F ||
            cp == 0x26BD || cp == 0x26BE -> 4
        cp in 0x1F300..0x1F32C || cp in 0x1F3D4..0x1F3F7 || cp in 0x1F680..0x1F6C5 ||
            cp in 0x1F6D0..0x1F6FF || cp in 0x26F0..0x26FA || cp == 0x2708 -> 5
        cp in 0x1F4A1..0x1F4FF || cp in 0x1F5A4..0x1F5FF || cp in 0x1F9E0..0x1F9FF ||
            cp in 0x231A..0x23F3 -> 6
        else -> 7
    }

    fun get(): List<Cat> {
        cache?.let { return it }
        val paint = Paint()
        val b = List(8) { mutableListOf<String>() }
        fun add(cp: Int) {
            if (cp in 0x1F1E6..0x1F1FF || cp in 0x1F3FB..0x1F3FF) return
            if (!UCharacter.hasBinaryProperty(cp, UProperty.EMOJI)) return
            val pres = UCharacter.hasBinaryProperty(cp, UProperty.EMOJI_PRESENTATION)
            val e = String(Character.toChars(cp)) + (if (pres) "" else "\uFE0F")
            if (paint.hasGlyph(e)) b[bucket(cp)].add(e)
        }
        for (cp in 0x203C..0x3299) add(cp)
        for (cp in 0x1F004..0x1FAFF) add(cp)

        val flags = mutableListOf("\uD83C\uDDEE\uD83C\uDDF3")   // India pehle
        for (cc in Locale.getISOCountries()) {
            if (cc == "IN") continue
            val f = String(Character.toChars(0x1F1E6 + (cc[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (cc[1] - 'A')))
            if (paint.hasGlyph(f)) flags.add(f)
        }
        val out = listOf(
            Cat("😀", b[0]), Cat("👋", b[1]), Cat("🐶", b[2]), Cat("🍔", b[3]), Cat("⚽", b[4]),
            Cat("🚗", b[5]), Cat("💡", b[6]), Cat("❤", b[7]), Cat("🏁", flags)
        ).filter { it.items.isNotEmpty() }
        cache = out
        return out
    }
}

private class Clip(val text: String, var pinned: Boolean)

class StyleKeyboardService : InputMethodService() {

    private enum class Page { LETTERS, SYM1, SYM2, EMOJI, CLIP }

    private companion object {
        const val ROW_DP = 52          // <- Gboard jaisa key-row height. Chhota/bada karna ho to yahi badlo
        const val ROW_DP_LAND = 40
    }

    private var style = Style.BOLD
    private var shift = 0               // 0 off, 1 ek baar, 2 caps lock
    private var lastShiftTap = 0L
    private var page = Page.LETTERS
    private var plainMode = false       // password/email/url/number fields me styling band
    private var lastSpace = 0L
    private var emojiCat = 0

    private lateinit var p: Pal
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var topArea: FrameLayout
    private lateinit var barScroll: HorizontalScrollView
    private lateinit var altStrip: LinearLayout
    private lateinit var rowsBox: LinearLayout
    private var shiftView: TextView? = null
    private var spaceView: TextView? = null
    private val keyViews = mutableListOf<Pair<String, TextView>>()
    private val chips = mutableMapOf<Style, TextView>()
    private var altData: List<String> = emptyList()
    private var prefs: SharedPreferences? = null
    private var clipboard: ClipboardManager? = null
    private val clips = mutableListOf<Clip>()
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private val eff: Style get() = if (plainMode) Style.NORMAL else style

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rowDp() =
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ROW_DP_LAND else ROW_DP

    // ---------- drawing helpers ----------
    private fun shape(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    private fun bg(color: Int, r: Int): RippleDrawable {
        val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(r).toFloat() }
        return RippleDrawable(ColorStateList.valueOf(p.ripple), shape(color, r), mask)
    }

    private fun keyCell(label: String, weight: Float, fill: Int, color: Int = p.text, size: Float = 20f) =
        TextView(this).apply {
            text = label; textSize = size; setTextColor(color)
            gravity = Gravity.CENTER; maxLines = 1; includeFontPadding = false
            background = bg(fill, 8); isClickable = true
            layoutParams = LinearLayout.LayoutParams(0, dp(rowDp()), weight)
                .apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
        }

    private fun spacer(weight: Float) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(rowDp()), weight)
    }

    private fun newRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
    }

    private fun toks(s: String) = s.map { it.toString() }

    // ---------- lifecycle ----------
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("stylekb", Context.MODE_PRIVATE)
        loadClips()
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard?.addPrimaryClipChangedListener(clipListener)
        Thread { try { EmojiData.get() } catch (t: Throwable) { } }.start()   // emoji list pehle se ready
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setInputView(onCreateInputView())
    }

    override fun onCreateInputView(): View {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        p = if (night) DARK else LIGHT
        try { window?.window?.navigationBarColor = p.bg } catch (t: Throwable) { }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.bg)
            setPadding(dp(3), dp(2), dp(3), dp(4))
        }
        topArea = FrameLayout(this)
        barScroll = buildStyleBar()
        altStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
            background = shape(p.pop, 12); elevation = dp(6).toFloat()
            visibility = View.GONE
        }
        topArea.addView(barScroll, FrameLayout.LayoutParams(MP, MP))
        topArea.addView(altStrip, FrameLayout.LayoutParams(WC, dp(44)))
        root.addView(topArea, LinearLayout.LayoutParams(MP, dp(44)))
        rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rowsBox)
        rebuild(); paintChips()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::rowsBox.isInitialized) return
        val t = info?.inputType ?: 0
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        plainMode = cls != InputType.TYPE_CLASS_TEXT ||
            v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            v == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            v == InputType.TYPE_TEXT_VARIATION_URI
        page = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME) Page.SYM1 else Page.LETTERS
        shift = 0
        rebuild(); paintChips(); updateAutoCap()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        handler.removeCallbacksAndMessages(null)
        hideAlts()
        super.onFinishInputView(finishingInput)
    }

    // ---------- top style bar ----------
    private fun chipLabel(st: Style) =
        if (st == Style.EMOJIFY) "🅰 Emoji" else TextStyler.apply(st.label, st)

    private fun buildStyleBar(): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(5), dp(2), dp(5))
        }
        fun chipParams() = LinearLayout.LayoutParams(WC, dp(34)).apply { setMargins(dp(3), 0, dp(3), 0) }

        // 📋 : clipboard history
        row.addView(TextView(this).apply {
            text = "📋"; textSize = 15f; gravity = Gravity.CENTER; isClickable = true
            setPadding(dp(14), 0, dp(14), 0); background = bg(p.fn, 17)
            layoutParams = chipParams()
            setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); page = Page.CLIP; rebuild() }
        })

        // Aa→ : selected text (ya poora text) ko current style me badalta hai
        row.addView(TextView(this).apply {
            text = "Aa→"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(p.accent)
            setPadding(dp(14), 0, dp(14), 0); background = bg(p.fn, 17); isClickable = true
            layoutParams = chipParams()
            setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); convertSelection() }
        })
        Style.values().forEach { st ->
            val chip = TextView(this).apply {
                text = chipLabel(st); textSize = 14f; gravity = Gravity.CENTER; isClickable = true
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = chipParams()
                setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); selectStyle(st) }
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
            tv.background = bg(if (sel) p.accent else p.fn, 17)
            tv.setTextColor(if (sel) Color.WHITE else p.muted)
            tv.alpha = if (plainMode) 0.4f else 1f
        }
    }

    private fun selectStyle(st: Style) {
        style = st
        paintChips(); updateLabels()
        spaceView?.text = spaceLabel()
    }

    private fun spaceLabel() = if (plainMode) "Plain" else style.label

    // ---------- keyboard pages ----------
    private fun rebuild() {
        rowsBox.removeAllViews(); keyViews.clear(); shiftView = null; spaceView = null
        when (page) {
            Page.LETTERS -> {
                rowsBox.addView(newRow().also { r -> toks("1234567890").forEach { r.addView(charKey(it, 17f)) } })
                rowsBox.addView(newRow().also { r -> toks("qwertyuiop").forEach { r.addView(charKey(it)) } })
                rowsBox.addView(newRow().also { r ->
                    r.addView(spacer(0.5f)); toks("asdfghjkl").forEach { r.addView(charKey(it)) }; r.addView(spacer(0.5f))
                })
                rowsBox.addView(newRow().also { r ->
                    r.addView(shiftKey()); toks("zxcvbnm").forEach { r.addView(charKey(it)) }; r.addView(backspaceKey())
                })
                rowsBox.addView(bottomRow())
            }
            Page.SYM1, Page.SYM2 -> {
                val sym1 = page == Page.SYM1
                rowsBox.addView(newRow().also { r -> toks("1234567890").forEach { r.addView(charKey(it, 17f)) } })
                rowsBox.addView(newRow().also { r ->
                    toks(if (sym1) "@#$" + "_&-+()/" else "£¢€¥^°={}\\").forEach { r.addView(charKey(it)) }
                })
                rowsBox.addView(newRow().also { r ->
                    val sw = keyCell(if (sym1) "=\\<" else "?123", 1.5f, p.fn, p.text, 15f)
                    bindKey(sw, tap = { page = if (sym1) Page.SYM2 else Page.SYM1; rebuild() })
                    r.addView(sw)
                    toks(if (sym1) "*\"':;!?" else "%©®™✓[]").forEach { r.addView(charKey(it)) }
                    r.addView(backspaceKey())
                })
                rowsBox.addView(bottomRow())
            }
            Page.EMOJI -> buildEmoji()
            Page.CLIP -> buildClip()
        }
        updateShiftView()
    }

    private fun charKey(tok: String, size: Float = 20f): TextView {
        val v = keyCell(display(tok), 1f, p.key, size = size)
        bindKey(v, tap = { typeToken(tok) }, alts = ALTS[tok].orEmpty())
        if (page == Page.LETTERS) keyViews.add(tok to v)
        return v
    }

    private fun shiftKey(): TextView {
        val s = keyCell("⇧", 1.5f, p.fn, p.text, 20f)
        bindKey(s, tap = { toggleShift() })
        shiftView = s
        return s
    }

    private fun backspaceKey(): TextView {
        val v = keyCell("⌫", 1.5f, p.fn, p.text, 18f)
        bindKey(v, tap = { backspace() }, repeat = true)
        return v
    }

    private fun bottomRow(): LinearLayout {
        val r = newRow()
        val letters = page == Page.LETTERS
        val sw = keyCell(if (letters) "?123" else "ABC", 1.5f, p.fn, p.text, 15f)
        bindKey(sw, tap = {
            page = if (letters) Page.SYM1 else Page.LETTERS
            rebuild(); if (page == Page.LETTERS) updateAutoCap()
        })
        r.addView(sw)

        val comma = keyCell(",", 1f, p.fn, p.text, 20f)
        bindKey(comma, tap = { commit(",") })
        r.addView(comma)

        val emo = keyCell("😊", 1f, p.fn, p.text, 18f)
        bindKey(emo, tap = { page = Page.EMOJI; rebuild() }, longPress = { imePicker() })
        r.addView(emo)

        val space = keyCell(spaceLabel(), 4f, p.key, p.muted, 12f)
        bindKey(space, tap = { space() }, longPress = { imePicker() }, swipe = true)
        spaceView = space
        r.addView(space)

        val dot = keyCell(".", 1f, p.fn, p.text, 20f)
        bindKey(dot, tap = { commit(".") }, alts = ALTS["."].orEmpty())
        r.addView(dot)

        val enter = keyCell(enterLabel(), 1.5f, p.accent, Color.WHITE, 20f)
        bindKey(enter, tap = { enter() })
        r.addView(enter)
        return r
    }

    private fun panelBottom(): LinearLayout {
        val bottom = newRow()
        val abc = keyCell("ABC", 1.5f, p.fn, p.text, 15f)
        bindKey(abc, tap = { page = Page.LETTERS; rebuild(); updateAutoCap() })
        bottom.addView(abc)
        val sp = keyCell("", 7f, p.key, p.muted, 12f)
        bindKey(sp, tap = { space() })
        bottom.addView(sp)
        bottom.addView(backspaceKey())
        return bottom
    }

    private fun buildEmoji() {
        val rowPx = dp(rowDp())
        val tabsData = mutableListOf<Pair<String, List<String>>>()
        val recents = loadRecents()
        if (recents.isNotEmpty()) tabsData.add("🕘" to recents)
        EmojiData.get().forEach { tabsData.add(it.icon to it.items) }
        if (emojiCat >= tabsData.size) emojiCat = 0

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabsData.forEachIndexed { i, (icon, _) ->
            tabs.addView(TextView(this).apply {
                text = icon; textSize = 18f; gravity = Gravity.CENTER; isClickable = true
                background = if (i == emojiCat) shape(p.key, 8) else null
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
                setOnClickListener { emojiCat = i; rebuild() }
            })
        }
        rowsBox.addView(tabs, LinearLayout.LayoutParams(MP, dp(40)))

        val items = tabsData[emojiCat].second
        val ctx = this
        val grid = GridView(this).apply {
            numColumns = 8
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = ColorDrawable(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            adapter = object : BaseAdapter() {
                override fun getCount() = items.size
                override fun getItem(position: Int): Any = items[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val tv = (convertView as? TextView) ?: TextView(ctx).apply {
                        textSize = 24f; gravity = Gravity.CENTER
                        layoutParams = AbsListView.LayoutParams(MP, dp(46))
                    }
                    tv.text = items[position]
                    return tv
                }
            }
            onItemClickListener = AdapterView.OnItemClickListener { _, v, pos, _ ->
                v?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                commitEmoji(items[pos])
            }
        }
        rowsBox.addView(grid, LinearLayout.LayoutParams(MP, rowPx * 4 - dp(40)))
        rowsBox.addView(panelBottom())
    }

    private fun commitEmoji(e: String) {
        commit(e)
        val l = loadRecents().toMutableList()
        l.remove(e); l.add(0, e)
        prefs?.edit()?.putString("recent_emoji", l.take(32).joinToString(" "))?.apply()
    }

    private fun loadRecents(): List<String> =
        (prefs?.getString("recent_emoji", "") ?: "").split(" ").filter { it.isNotEmpty() }

    // ---------- clipboard ----------
    private fun buildClip() {
        captureClip()
        val rowPx = dp(rowDp())
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
        }
        header.addView(TextView(this).apply {
            text = "Clipboard"; textSize = 16f; setTextColor(p.text)
            layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
        })
        header.addView(TextView(this).apply {
            text = "Clear"; textSize = 14f; setTextColor(p.accent); isClickable = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { clips.removeAll { c -> !c.pinned }; saveClips(); rebuild() }
        })
        rowsBox.addView(header, LinearLayout.LayoutParams(MP, dp(40)))

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), 0, dp(4), 0) }
        val sorted = clips.sortedByDescending { it.pinned }
        if (sorted.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Kuch copy karo, yahan dikhega"; textSize = 14f; setTextColor(p.muted)
                gravity = Gravity.CENTER; setPadding(0, dp(40), 0, 0)
            })
        }
        sorted.forEach { list.addView(clipRow(it)) }
        rowsBox.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) },
            LinearLayout.LayoutParams(MP, rowPx * 4 - dp(40)))
        rowsBox.addView(panelBottom())
    }

    private fun clipRow(c: Clip): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { setMargins(0, dp(3), 0, dp(3)) }
        }
        row.addView(TextView(this).apply {
            text = c.text; textSize = 14f; setTextColor(p.text)
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END; isClickable = true
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = bg(p.key, 10)
            layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                currentInputConnection?.commitText(c.text, 1)
                lastSpace = 0L; afterCommit()
            }
        })
        row.addView(TextView(this).apply {
            text = "📌"; textSize = 16f; gravity = Gravity.CENTER; isClickable = true
            alpha = if (c.pinned) 1f else 0.35f
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener { c.pinned = !c.pinned; saveClips(); rebuild() }
        })
        row.addView(TextView(this).apply {
            text = "✕"; textSize = 16f; gravity = Gravity.CENTER; isClickable = true
            setTextColor(p.muted)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener { clips.remove(c); saveClips(); rebuild() }
        })
        return row
    }

    /** Current copy kiya hua text history me daalta hai (sensitive-flag wale copies skip). */
    private fun captureClip() {
        try {
            val clip = clipboard?.primaryClip ?: return
            if (clip.itemCount == 0) return
            val sensitive = clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) ?: false
            if (sensitive) return
            val t = clip.getItemAt(0).coerceToText(this)?.toString()?.trim().orEmpty()
            if (t.isEmpty() || t.length > 5000) return
            val i = clips.indexOfFirst { it.text == t }
            if (i == 0) return
            if (i > 0) { val c = clips.removeAt(i); clips.add(0, c) } else clips.add(0, Clip(t, false))
            var unpinned = 0
            val iter = clips.iterator()
            while (iter.hasNext()) { val c = iter.next(); if (!c.pinned && ++unpinned > 30) iter.remove() }
            saveClips()
        } catch (e: Throwable) { }
    }

    private fun saveClips() {
        val arr = JSONArray()
        clips.forEach { arr.put(JSONObject().put("t", it.text).put("p", it.pinned)) }
        prefs?.edit()?.putString("clips", arr.toString())?.apply()
    }

    private fun loadClips() {
        clips.clear()
        try {
            val arr = JSONArray(prefs?.getString("clips", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                clips.add(Clip(o.getString("t"), o.optBoolean("p", false)))
            }
        } catch (e: Throwable) { }
    }

    // ---------- touch handling (tap / repeat / long-press popup / space swipe) ----------
    private fun bindKey(
        v: View, tap: () -> Unit, alts: List<String> = emptyList(),
        longPress: (() -> Unit)? = null, repeat: Boolean = false, swipe: Boolean = false
    ) {
        val rep = object : Runnable { override fun run() { tap(); handler.postDelayed(this, 45) } }
        var longDone = false; var swiped = false; var altShown = false
        var sel = 0; var lastX = 0f; var downX = 0f; var acc = 0f
        val lp = Runnable {
            if (alts.isNotEmpty()) {
                altShown = true; longDone = true; sel = 0
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showAlts(v, alts)
            } else if (longPress != null) {
                longDone = true
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                longPress()
            }
        }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    longDone = false; swiped = false; altShown = false; acc = 0f
                    downX = e.rawX; lastX = e.rawX
                    if (repeat) { tap(); handler.postDelayed(rep, 380) }
                    else if (alts.isNotEmpty() || longPress != null) handler.postDelayed(lp, 320)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (altShown) {
                        sel = altIndexAt(e.rawX); highlightAlt(sel)
                    } else if (swipe) {
                        acc += e.rawX - lastX; lastX = e.rawX
                        if (!swiped && abs(e.rawX - downX) > dp(12)) { swiped = true; handler.removeCallbacks(lp) }
                        if (swiped) {
                            val step = dp(14).toFloat()
                            while (acc > step) { moveCursor(1); acc -= step }
                            while (acc < -step) { moveCursor(-1); acc += step }
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    handler.removeCallbacks(lp); handler.removeCallbacks(rep)
                    if (altShown) { val s = altData[sel]; hideAlts(); commitStyled(s) }
                    else if (!repeat && !longDone && !swiped) tap()
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    handler.removeCallbacks(lp); handler.removeCallbacks(rep)
                    if (altShown) hideAlts()
                }
            }
            true
        }
    }

    // ---------- long-press alternates strip (top bar ki jagah) ----------
    private fun showAlts(anchor: View, alts: List<String>) {
        val upper = shift != 0 && page == Page.LETTERS
        altData = alts.take(8).map { if (upper) it.uppercase() else it }
        altStrip.removeAllViews()
        val w = dp(44)
        altData.forEach { a ->
            altStrip.addView(TextView(this).apply {
                text = a; textSize = 22f; gravity = Gravity.CENTER; setTextColor(p.text)
                layoutParams = LinearLayout.LayoutParams(w, dp(40))
            })
        }
        val a = IntArray(2); anchor.getLocationOnScreen(a)
        val t = IntArray(2); topArea.getLocationOnScreen(t)
        val stripW = w * altData.size + dp(8)
        val center = a[0] - t[0] + anchor.width / 2
        val maxLeft = (topArea.width - stripW - dp(4)).coerceAtLeast(dp(4))
        altStrip.translationX = (center - stripW / 2).coerceIn(dp(4), maxLeft).toFloat()
        barScroll.visibility = View.INVISIBLE
        altStrip.visibility = View.VISIBLE
        highlightAlt(0)
    }

    private fun highlightAlt(sel: Int) {
        for (i in 0 until altStrip.childCount) {
            val tv = altStrip.getChildAt(i) as TextView
            tv.background = if (i == sel) shape(p.accent, 8) else null
            tv.setTextColor(if (i == sel) Color.WHITE else p.text)
        }
    }

    private fun altIndexAt(rawX: Float): Int {
        if (altData.isEmpty()) return 0
        val loc = IntArray(2); altStrip.getLocationOnScreen(loc)
        return ((rawX - loc[0] - dp(4)) / dp(44)).toInt().coerceIn(0, altData.size - 1)
    }

    private fun hideAlts() {
        if (!::altStrip.isInitialized) return
        altStrip.visibility = View.GONE
        barScroll.visibility = View.VISIBLE
    }

    // ---------- state ----------
    private fun display(tok: String): String {
        if (page != Page.LETTERS) return tok
        val s = if (shift != 0) tok.uppercase() else tok
        val e = eff
        return if (e == Style.UNDERLINE || e == Style.STRIKE) s else TextStyler.apply(s, e).trim()
    }

    private fun updateLabels() {
        keyViews.forEach { (tok, tv) -> tv.text = display(tok) }
        updateShiftView()
    }

    private fun updateShiftView() {
        shiftView?.apply {
            text = if (shift == 2) "⇪" else "⇧"
            background = bg(if (shift == 0) p.fn else p.accent, 8)
            setTextColor(if (shift == 0) p.text else Color.WHITE)
        }
    }

    private fun toggleShift() {
        val now = System.currentTimeMillis()
        shift = when {
            now - lastShiftTap < 300 -> 2           // double tap = caps lock
            shift == 0 -> 1
            else -> 0
        }
        lastShiftTap = now
        updateLabels()
    }

    private fun updateAutoCap() {
        val ei = currentInputEditorInfo
        if (ei != null && (ei.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT && shift == 0) {
            val caps = currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0
            if (caps != 0) shift = 1
        }
        updateLabels()
    }

    private fun enterLabel(): String {
        val ei = currentInputEditorInfo ?: return "↵"
        if ((ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "↵"
        return when (ei.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> "→"
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_SEND -> "➤"
            EditorInfo.IME_ACTION_DONE -> "✓"
            EditorInfo.IME_ACTION_PREVIOUS -> "←"
            else -> "↵"
        }
    }

    // ---------- input ----------
    private fun afterCommit() {
        if (shift == 1) shift = 0
        if (page == Page.LETTERS) updateAutoCap() else updateLabels()
    }

    private fun commit(s: String) {
        currentInputConnection?.commitText(s, 1)
        lastSpace = 0L
        afterCommit()
    }

    private fun commitStyled(s: String) = commit(TextStyler.apply(s, eff))

    private fun typeToken(tok: String) {
        commitStyled(if (shift != 0 && page == Page.LETTERS) tok.uppercase() else tok)
    }

    private fun space() {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        if (!plainMode && now - lastSpace < 400) {          // double-space = ". "
            val b = ic.getTextBeforeCursor(3, 0)?.toString().orEmpty()
            if (b.endsWith(" ")) {
                val t = b.dropLast(1)
                if (t.isNotEmpty() && Character.isLetterOrDigit(t.codePointBefore(t.length))) {
                    ic.deleteSurroundingText(1, 0); ic.commitText(". ", 1)
                    lastSpace = 0L; afterCommit(); return
                }
            }
        }
        ic.commitText(" ", 1)
        lastSpace = now
        afterCommit()
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) { ic.commitText("", 1) }
        else {
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            val n = if (before.endsWith("\u0332") || before.endsWith("\u0336")) 2 else 1
            ic.deleteSurroundingTextInCodePoints(n, 0)
        }
        if (page == Page.LETTERS) updateAutoCap()
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = ei != null && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        lastSpace = 0L
        afterCommit()
    }

    private fun moveCursor(dir: Int) {
        val ic = currentInputConnection ?: return
        val code = if (dir > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun imePicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
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
