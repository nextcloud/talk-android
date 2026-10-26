/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.message

/**
 * The GFM tables parser bundled with Markwon 4.6.2 (commonmark-ext-gfm-tables 0.13.0) only starts a table when the
 * header row begins its own paragraph, has at most as many cells as the delimiter row and the delimiter row follows
 * it directly. A table written directly under a text line, without a blank line between them, is therefore rendered
 * as plain text. A blank line is inserted before such a header row. Fenced code blocks and indented code are left
 * untouched.
 *
 * Not handled: tables inside block quotes and lazy continuation lines of list items.
 */
object MarkdownTables {
    private const val PIPE = '|'
    private const val ESCAPE = '\\'
    private const val INDENTED_CODE_SPACES = 4
    private const val MAX_FENCE_INDENT = 3
    private val DELIMITER_ROW = Regex("""^ {0,3}\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")

    // optional list markers ("- ", "1. ") before the fence; a backtick fence has no backticks in its info string
    private val FENCE_OPEN = Regex("""^ {0,3}(?:(?:[-+*]|\d{1,9}[.)])[ \t]+)*(?:(`{3,})[^`]*|(~{3,}).*)$""")

    private class Fence(val marker: String, val maxCloseIndent: Int)

    /** Returns true if the text contains a table header row followed by a delimiter row outside of code blocks. */
    fun containsTable(markdown: String): Boolean {
        if (!markdown.contains(PIPE)) return false

        val lines = markdown.split("\n").map { it.trimEnd('\r') }
        val inCode = fencedLines(lines)
        return lines.indices.any { !inCode[it] && isTableHeader(lines[it], lines.getOrNull(it + 1)) }
    }

    fun ensureBlankLineBeforeTables(markdown: String): String {
        if (!markdown.contains(PIPE)) return markdown

        val lines = markdown.split("\n").map { it.trimEnd('\r') to it }
        val inCode = fencedLines(lines.map { (content, _) -> content })
        val result = ArrayList<String>(lines.size + 1)
        var inTable = false

        for ((index, line) in lines.withIndex()) {
            val (content, raw) = line
            val isHeader = !inCode[index] && isTableHeader(content, lines.getOrNull(index + 1)?.first)
            if (isHeader && !inTable && result.isNotEmpty() && result.last().isNotBlank()) {
                result.add("")
            }
            inTable = !inCode[index] && (isHeader || (inTable && content.contains(PIPE)))
            result.add(raw)
        }
        return if (result.size == lines.size) markdown else result.joinToString("\n")
    }

    private fun isTableHeader(line: String, next: String?): Boolean =
        next != null &&
            line.contains(PIPE) &&
            !isIndentedCode(line) &&
            next.contains(PIPE) &&
            DELIMITER_ROW.matches(next) &&
            cellCount(next) >= cellCount(line)

    private fun isIndentedCode(line: String): Boolean =
        line.startsWith("\t") || line.startsWith(" ".repeat(INDENTED_CODE_SPACES))

    /** Number of cells in a table row: unescaped pipes split cells, the outer pipes are not counted. */
    private fun cellCount(line: String): Int {
        val text = line.trim().removePrefix(PIPE.toString())
        val pipes = unescapedPipes(text)
        val endsWithPipe = pipes.isNotEmpty() && pipes.last() == text.length - 1
        return pipes.size + if (endsWithPipe) 0 else 1
    }

    private fun unescapedPipes(text: String): List<Int> {
        val result = ArrayList<Int>()
        var escaped = false
        for ((index, char) in text.withIndex()) {
            if (char == PIPE && !escaped) result.add(index)
            escaped = char == ESCAPE && !escaped
        }
        return result
    }

    /** Marks lines that belong to a fenced code block, including the opening and closing fence lines. */
    private fun fencedLines(lines: List<String>): BooleanArray {
        val inCode = BooleanArray(lines.size)
        var fence: Fence? = null
        for ((index, line) in lines.withIndex()) {
            val open = fence
            if (open == null) {
                fence = openingFence(line)
                inCode[index] = fence != null
            } else {
                inCode[index] = true
                if (isFenceClose(line, open)) fence = null
            }
        }
        return inCode
    }

    private fun openingFence(line: String): Fence? {
        val group = FENCE_OPEN.matchEntire(line)?.let { it.groups[1] ?: it.groups[2] }
        return group?.let {
            val start = it.range.first
            val inList = line.substring(0, start).isNotBlank()
            Fence(it.value, if (inList) start + MAX_FENCE_INDENT else MAX_FENCE_INDENT)
        }
    }

    private fun isFenceClose(line: String, fence: Fence): Boolean {
        val body = line.trimStart(' ').trimEnd()
        val indent = line.length - line.trimStart(' ').length
        return indent <= fence.maxCloseIndent &&
            body.length >= fence.marker.length &&
            body.all { it == fence.marker[0] }
    }
}
