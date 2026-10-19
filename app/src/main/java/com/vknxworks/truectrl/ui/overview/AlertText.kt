package com.vknxworks.truectrl.ui.overview

import androidx.core.text.HtmlCompat

/**
 * TrueNAS alert texts can contain a little HTML (links, line breaks). Shown as plain text: tags
 * removed, entities decoded, line breaks kept. Links are never made tappable.
 */
fun alertText(raw: String): String {
    if (!raw.contains('<') && !raw.contains('&')) return raw.trim()
    val withBreaks = raw.replace(Regex("(?i)<br\\s*/?>"), "\n")
    return HtmlCompat.fromHtml(withBreaks.replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace(' ', ' ')
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
