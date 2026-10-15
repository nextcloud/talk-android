/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.message

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.CustomBlock
import org.commonmark.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("TooManyFunctions")
class MarkdownTablesTest {

    // Same parser setup as Markwon's TablePlugin: commonmark with the GFM tables extension 0.13.0.
    private val parser = Parser.builder().extensions(listOf(TablesExtension.create())).build()

    private fun tableCount(markdown: String): Int {
        var count = 0
        parser.parse(markdown).accept(object : AbstractVisitor() {
            override fun visit(customBlock: CustomBlock) {
                if (customBlock is TableBlock) count++
                super.visit(customBlock)
            }
        })
        return count
    }

    private fun render(markdown: String) = MarkdownTables.ensureBlankLineBeforeTables(markdown)

    @Test
    fun `table directly under text is parsed as a table after the fix`() {
        val markdown = "Some text\n| a | b |\n|---|---|\n| 1 | 2 |"

        assertEquals(0, tableCount(markdown))
        assertEquals(1, tableCount(render(markdown)))
        assertEquals("Some text\n\n| a | b |\n|---|---|\n| 1 | 2 |", render(markdown))
    }

    @Test
    fun `table with aligned delimiter row and no outer pipes under text`() {
        val markdown = "Intro\na | b\n:--|--:\n1 | 2"

        assertEquals(0, tableCount(markdown))
        assertEquals(1, tableCount(render(markdown)))
    }

    @Test
    fun `text without pipe is returned as is`() {
        val markdown = "Some text\n---\nmore"

        assertSame(markdown, render(markdown))
    }

    @Test
    fun `table without text above is not changed`() {
        val markdown = "| a | b |\n|---|---|\n| 1 | 2 |"

        assertSame(markdown, render(markdown))
        assertEquals(1, tableCount(render(markdown)))
    }

    @Test
    fun `table already separated by blank line is not changed`() {
        val markdown = "Text\n\n| a | b |\n|---|---|\n| 1 | 2 |"

        assertEquals(markdown, render(markdown))
    }

    @Test
    fun `text with pipe but no delimiter row is not changed`() {
        val markdown = "Choose a | b\nor c | d\nthen --- done"

        assertSame(markdown, render(markdown))
        assertEquals(markdown, render(markdown))
        assertEquals(0, tableCount(render(markdown)))
    }

    @Test
    fun `table inside fenced code block is not changed`() {
        val markdown = "Code:\n```\ntext\n| a | b |\n|---|---|\n| 1 | 2 |\n```\nafter"

        assertEquals(markdown, render(markdown))
        assertEquals(0, tableCount(render(markdown)))
    }

    @Test
    fun `table inside tilde fence is not changed but table after the fence is fixed`() {
        val markdown = "~~~\nx\n| a | b |\n|---|---|\n~~~\nText\n| c | d |\n|---|---|\n| 1 | 2 |"

        val expected = "~~~\nx\n| a | b |\n|---|---|\n~~~\nText\n\n| c | d |\n|---|---|\n| 1 | 2 |"
        assertEquals(expected, render(markdown))
        assertEquals(1, tableCount(render(markdown)))
    }

    @Test
    fun `several tables each under text are all fixed`() {
        val markdown = "First\n| a | b |\n|---|---|\n| 1 | 2 |\n\nSecond\n| c | d |\n|---|---|\n| 3 | 4 |"

        assertEquals(0, tableCount(markdown))
        assertEquals(2, tableCount(render(markdown)))
    }

    @Test
    fun `table rows are not split by a repeated delimiter row inside the table`() {
        val markdown = "Text\n| a | b |\n|---|---|\n| 1 | 2 |\n|---|---|\n| 3 | 4 |"

        assertEquals(0, tableCount(markdown))
        assertEquals("Text\n\n| a | b |\n|---|---|\n| 1 | 2 |\n|---|---|\n| 3 | 4 |", render(markdown))
        assertEquals(1, tableCount(render(markdown)))
    }

    @Test
    fun `windows line endings are kept`() {
        val markdown = "Text\r\n| a | b |\r\n|---|---|\r\n| 1 | 2 |"

        assertEquals(0, tableCount(markdown))
        assertEquals("Text\r\n\n| a | b |\r\n|---|---|\r\n| 1 | 2 |", render(markdown))
        assertEquals(1, tableCount(render(markdown)))
    }

    @Test
    fun `table in a list item is fixed`() {
        val markdown = "- Item\n  | a | b |\n  |---|---|\n  | 1 | 2 |"

        assertEquals(0, tableCount(markdown))
        assertEquals("- Item\n\n  | a | b |\n  |---|---|\n  | 1 | 2 |", render(markdown))
        assertEquals(1, tableCount(render(markdown)))
    }

    @Test
    fun `fence on a list marker line is code`() {
        val markdown = "- ```\n  Text\n  | a | b |\n  |---|---|\n  ```\nAfter"

        assertSame(markdown, render(markdown))
        val ordered = "1. ```\nText\n| a | b |\n|---|---|\n   ```\nAfter\n| c | d |\n|---|---|"
        assertEquals(ordered.replace("After\n", "After\n\n"), render(ordered))
    }

    @Test
    fun `closing fence indented by four spaces does not close the block`() {
        val markdown = "```\nx\n    ```\nText\n| a | b |\n|---|---|\n| 1 | 2 |"

        assertSame(markdown, render(markdown))
    }

    @Test
    fun `unclosed fence keeps the rest of the message as code`() {
        val markdown = "Text\n```\nText\n| a | b |\n|---|---|\n| 1 | 2 |"

        assertSame(markdown, render(markdown))
    }

    @Test
    fun `indented code is not changed`() {
        val markdown = "Text\n    | a | b |\n    |---|---|\n    | 1 | 2 |"

        assertSame(markdown, render(markdown))
        assertEquals(0, tableCount(render(markdown)))
    }

    @Test
    fun `delimiter row with fewer columns than the header is not a table and the paragraph stays whole`() {
        val markdown = "Intro\na | b | c\n--|--\n1 | 2 | 3"

        assertEquals(0, tableCount(markdown))
        assertSame(markdown, render(markdown))
        assertEquals(0, tableCount(render(markdown)))
    }

    @Test
    fun `escaped pipe does not add a header cell`() {
        val markdown = "Intro\na \\| b | c\n--|--\n1 | 2"

        assertEquals(1, tableCount(render(markdown)))
    }

    @Test
    fun `setext heading is not split`() {
        val markdown = "Text\na | b\n---"

        assertSame(markdown, render(markdown))
    }

    @Test
    fun `containsTable detects tables the old separator regex missed`() {
        assertTrue(MarkdownTables.containsTable("| a | b |\n|---|---|"))
        assertTrue(MarkdownTables.containsTable("Text\na | b\n:-:|-:"))
        assertTrue(MarkdownTables.containsTable("a | b\n-|-"))
        assertTrue(MarkdownTables.containsTable("a | b\r\n--|--\r\n1 | 2"))
    }

    @Test
    fun `containsTable ignores code and non tables`() {
        assertFalse(MarkdownTables.containsTable("no pipes ---"))
        assertFalse(MarkdownTables.containsTable("a | b\n---"))
        assertFalse(MarkdownTables.containsTable("```\n| a | b |\n|---|---|\n```"))
        assertFalse(MarkdownTables.containsTable("a | b | c\n--|--"))
    }
}
