package com.rajnish.stylekeyboard

enum class Style(val label: String) {
    NORMAL("Normal"), BOLD("Bold"), ITALIC("Italic"), BOLD_ITALIC("Bold+Italic"),
    UNDERLINE("Underline"), STRIKE("Strike"), MONO("Mono"), PREMIUM("Premium"),
    GOTHIC("Gothic"), CIRCLE("Circle"), EMOJIFY("Emojify")
}

object TextStyler {
    private fun cp(v: Int) = String(Character.toChars(v))

    private fun alpha(c: Char, up: Int, low: Int, digit: Int? = null): String? = when {
        c in 'A'..'Z' -> cp(up + (c - 'A'))
        c in 'a'..'z' -> cp(low + (c - 'a'))
        c in '0'..'9' && digit != null -> cp(digit + (c - '0'))
        else -> null
    }

    fun apply(text: String, style: Style): String {
        val sb = StringBuilder()
        for (c in text) {
            val s: String? = when (style) {
                Style.NORMAL -> null
                Style.BOLD -> alpha(c, 0x1D400, 0x1D41A, 0x1D7CE)
                Style.ITALIC -> if (c == 'h') "\u210E" else alpha(c, 0x1D434, 0x1D44E)
                Style.BOLD_ITALIC -> alpha(c, 0x1D468, 0x1D482)
                Style.MONO -> alpha(c, 0x1D670, 0x1D68A, 0x1D7F6)
                Style.PREMIUM -> alpha(c, 0x1D4D0, 0x1D4EA)   // bold script
                Style.GOTHIC -> alpha(c, 0x1D56C, 0x1D586)    // bold fraktur
                Style.CIRCLE -> alpha(c, 0x24B6, 0x24D0)
                Style.EMOJIFY -> if (c.isLetter() && c.code < 128) cp(0x1F170 + (c.uppercaseChar() - 'A')) + " " else null
                Style.UNDERLINE -> if (!c.isWhitespace()) "$c\u0332" else null
                Style.STRIKE -> if (!c.isWhitespace()) "$c\u0336" else null
            }
            sb.append(s ?: c.toString())
        }
        return sb.toString()
    }
}
