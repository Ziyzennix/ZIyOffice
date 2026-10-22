package org.ziyoffice.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.htmlToParagraphs
import org.ziyoffice.model.textToParagraphs
import org.ziyoffice.model.toClipboardHtml
import org.ziyoffice.model.toClipboardText

/**
 * The clipboard, as the document editor sees it.
 *
 * A clipboard holds text, and the apps that put things there also put HTML
 * beside it, which is where the formatting travels. Copying puts both, so
 * another app can use whichever it understands, and pasting takes the HTML
 * when there is any and the plain text when there is not.
 */
object EditorClipboard {

    /**
     * Puts the part of [paragraph] between [start] and [end] on the clipboard.
     *
     * Nothing is put there when the part is empty, so a copy with nothing
     * selected does not wipe what was already copied.
     */
    fun copy(context: Context, paragraph: Paragraph?, start: Int, end: Int) {
        if (paragraph == null) return
        val text = paragraph.toClipboardText(start, end)
        if (text.isEmpty()) return
        val html = paragraph.toClipboardHtml(start, end)
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("ZiyOffice text", text)
        if (html.isNotEmpty()) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putString(Intent.EXTRA_HTML_TEXT, html)
            }
        }
        clipboard.setPrimaryClip(clip)
    }

    /**
     * What is on the clipboard, as paragraphs.
     *
     * Null when there is nothing to paste.
     */
    fun read(context: Context): List<Paragraph>? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val item = clip.getItemAt(0)
        val html = item.htmlText
        if (!html.isNullOrBlank()) return htmlToParagraphs(html)
        val text = item.text?.toString()
        if (text.isNullOrBlank()) return null
        return textToParagraphs(text)
    }
}
