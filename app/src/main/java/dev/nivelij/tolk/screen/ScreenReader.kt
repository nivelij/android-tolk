package dev.nivelij.tolk.screen

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenLine(val text: String, val top: Int, val left: Int)

object ScreenReader {
    fun read(root: AccessibilityNodeInfo?): String {
        root ?: return ""
        val webViews = mutableListOf<AccessibilityNodeInfo>()
        findWebViews(root, webViews)
        return if (webViews.isNotEmpty()) {
            capLines(webViews.flatMap(::readWeb))
        } else {
            val lines = mutableListOf<ScreenLine>()
            collectNative(root, null, lines)
            joinScreenLines(lines)
        }
    }

    // In a browser only the page counts, not the address bar or tab strip.
    private fun findWebViews(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.className?.toString() == "android.webkit.WebView" && node.isVisibleToUser) {
            out += node
            return
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { findWebViews(it, out) }
    }

    // Browsers expose a paragraph as many small text runs (one per link, bold word, footnote), so runs are
    // glued back together per wide container instead of being treated as separate lines.
    private fun readWeb(web: AccessibilityNodeInfo): List<String> {
        val pageWidth = Rect().also(web::getBoundsInScreen).width()
        val blocks = mutableListOf<MutableList<String>>()

        fun walk(node: AccessibilityNodeInfo, block: MutableList<String>) {
            if (!node.isVisibleToUser) return
            val bounds = Rect().also(node::getBoundsInScreen)
            val own = if (node.childCount >= 2 && bounds.width() >= pageWidth * 0.6) {
                mutableListOf<String>().also { blocks += it }
            } else {
                block
            }
            val text = node.text?.toString()
            if (!text.isNullOrBlank() && !node.isPassword) {
                if (node.className?.endsWith("Button") == true) blocks += mutableListOf(text) else own += text
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, own) }
        }

        walk(web, mutableListOf<String>().also { blocks += it })
        return blocks.map(::joinRuns).filter { it.isNotBlank() }
    }

    private fun collectNative(node: AccessibilityNodeInfo, parentText: String?, out: MutableList<ScreenLine>) {
        if (!node.isVisibleToUser) return
        val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() && !node.isPassword }
        val repeatsParent = text != null && parentText != null && parentText.contains(text)
        if (text != null && !repeatsParent) {
            val bounds = Rect().also(node::getBoundsInScreen)
            out += ScreenLine(text, bounds.top, bounds.left)
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectNative(it, text ?: parentText, out) }
    }
}

private val footnote = Regex("""\[\d{1,3}]""")
private val whitespace = Regex("""\s+""")
private const val NO_SPACE_BEFORE = ".,;:!?)]}%»“”'’"
private const val NO_SPACE_AFTER = "-([{„«\"'‘“/"

fun joinRuns(runs: List<String>): String {
    val out = StringBuilder()
    for (run in runs) {
        if (out.isNotEmpty() && run.isNotEmpty()) {
            val prev = out.last()
            val next = run.first()
            val glued = prev.isWhitespace() || next.isWhitespace() || prev in NO_SPACE_AFTER || next in NO_SPACE_BEFORE
            if (!glued) out.append(' ')
        }
        out.append(run)
    }
    return out.toString().replace(footnote, "").replace(whitespace, " ").replace(" .", ".").replace(" ,", ",").trim()
}

fun joinScreenLines(lines: List<ScreenLine>, maxChars: Int = 8000): String =
    capLines(lines.sortedWith(compareBy<ScreenLine>({ it.top / 12 }, { it.left })).map { it.text }, maxChars)

private fun capLines(lines: List<String>, maxChars: Int = 8000): String {
    val out = StringBuilder()
    var previous: String? = null
    for (line in lines) {
        if (line == previous) continue
        if (out.length + line.length > maxChars) break
        if (out.isNotEmpty()) out.append('\n')
        out.append(line)
        previous = line
    }
    return out.toString()
}
