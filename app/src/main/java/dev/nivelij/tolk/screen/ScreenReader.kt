package dev.nivelij.tolk.screen

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenBlock(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

object ScreenReader {
    private const val MAX_CHARS = 8000

    fun read(root: AccessibilityNodeInfo?): List<ScreenBlock> {
        root ?: return emptyList()
        val webViews = mutableListOf<AccessibilityNodeInfo>()
        findWebViews(root, webViews)
        val blocks = if (webViews.isNotEmpty()) webViews.flatMap(::readWeb) else readNative(root)
        return capBlocks(blocks, MAX_CHARS)
    }

    // In a browser only the page counts, not the address bar or tab strip.
    private fun findWebViews(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.className?.toString() == "android.webkit.WebView" && node.isVisibleToUser) {
            out += node
            return
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { findWebViews(it, out) }
    }

    private class Builder {
        val runs = mutableListOf<String>()
        val rect = Rect()

        fun add(text: String, bounds: Rect) {
            runs += text
            rect.union(bounds)
        }

        fun build(): ScreenBlock? {
            val text = joinRuns(runs)
            if (text.isBlank() || rect.isEmpty) return null
            return ScreenBlock(text, rect.left, rect.top, rect.right, rect.bottom)
        }
    }

    // Browsers expose a paragraph as many small text runs (one per link, bold word, footnote), so runs are
    // glued back together per wide container instead of being treated as separate lines.
    private fun readWeb(web: AccessibilityNodeInfo): List<ScreenBlock> {
        val page = Rect().also(web::getBoundsInScreen)
        val builders = mutableListOf<Builder>()

        fun walk(node: AccessibilityNodeInfo, block: Builder) {
            if (!node.isVisibleToUser) return
            val bounds = Rect().also(node::getBoundsInScreen)
            if (!bounds.intersect(page)) return
            val own = if (node.childCount >= 2 && bounds.width() >= page.width() * 0.6) Builder().also { builders += it } else block
            val text = node.text?.toString()
            if (!text.isNullOrBlank() && !node.isPassword) {
                if (node.className?.endsWith("Button") == true) builders += Builder().also { it.add(text, bounds) } else own.add(text, bounds)
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, own) }
        }

        walk(web, Builder().also { builders += it })
        return builders.mapNotNull(Builder::build)
    }

    private fun readNative(root: AccessibilityNodeInfo): List<ScreenBlock> {
        val out = mutableListOf<ScreenBlock>()

        fun walk(node: AccessibilityNodeInfo, parentText: String?) {
            if (!node.isVisibleToUser) return
            val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() && !node.isPassword }
            val repeatsParent = text != null && parentText != null && parentText.contains(text)
            if (text != null && !repeatsParent) {
                val b = Rect().also(node::getBoundsInScreen)
                if (!b.isEmpty) out += ScreenBlock(text, b.left, b.top, b.right, b.bottom)
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, text ?: parentText) }
        }

        walk(root, null)
        return out.sortedWith(compareBy<ScreenBlock>({ it.top / 12 }, { it.left }))
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

fun capBlocks(blocks: List<ScreenBlock>, maxChars: Int): List<ScreenBlock> {
    val out = mutableListOf<ScreenBlock>()
    var total = 0
    for (block in blocks) {
        if (out.lastOrNull()?.text == block.text) continue
        if (total + block.text.length > maxChars) break
        out += block
        total += block.text.length
    }
    return out
}
