/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class FileAttachmentPreviewViewModelTest {

    private fun viewModel(vararg uris: String) =
        FileAttachmentPreviewViewModel(mock<Context>()).apply { setInitialFiles(uris.toList()) }

    @Test
    fun replaceFileSwapsUriInPlace() {
        val vm = viewModel("a", "b", "c")
        vm.replaceFile("b", "b2")
        assertEquals(listOf("a", "b2", "c"), vm.files.toList())
    }

    @Test
    fun replaceFileKeepsExcludedState() {
        val vm = viewModel("a", "b")
        vm.toggleSelected("b")
        vm.replaceFile("b", "b2")
        assertEquals(listOf("a"), vm.selectedFiles())
        assertEquals(listOf("b2"), vm.unselected.toList())
    }

    @Test
    fun replaceFileOfUnknownUriIsIgnored() {
        val vm = viewModel("a")
        vm.replaceFile("zzz", "y")
        assertEquals(listOf("a"), vm.files.toList())
    }

    @Test
    fun excludedFileIsNotSelectedButStaysInList() {
        val vm = viewModel("a", "b", "c")
        vm.toggleSelected("b")
        assertEquals(listOf("a", "c"), vm.selectedFiles())
        assertEquals(listOf("a", "b", "c"), vm.files.toList())
        vm.toggleSelected("b")
        assertEquals(listOf("a", "b", "c"), vm.selectedFiles())
    }

    @Test
    fun lastSelectedFileStaysSelected() {
        val vm = viewModel("a", "b")
        vm.toggleSelected("a")
        vm.toggleSelected("b")
        assertEquals(listOf("b"), vm.selectedFiles())
        assertEquals(listOf("a"), vm.unselected.toList())
    }

    @Test
    fun filesAddedLaterAreSelected() {
        val vm = viewModel("a", "b")
        vm.toggleSelected("a")
        vm.addFiles(listOf("c"))
        assertEquals(listOf("b", "c"), vm.selectedFiles())
    }

    private fun description(uri: String, detail: String = "d") =
        FileDescription(uri, "name", MediaKind.IMAGE, "image/jpeg", detail)

    @Test
    fun replacedPageKeepsItsDescriptionUntilRefreshed() {
        val vm = viewModel("a", "b")
        vm.publishDescriptions(mapOf("a" to description("a"), "b" to description("b")))
        vm.replaceFile("b", "b2")
        assertEquals(listOf("a", "b2"), vm.files.mapNotNull { vm.descriptionsByUri[it]?.uri })
    }

    @Test
    fun staleDescriptionResultCannotWipeOutReplacement() {
        val vm = viewModel("a", "b")
        vm.publishDescriptions(mapOf("a" to description("a"), "b" to description("b")))
        vm.replaceFile("b", "b2")
        // a describe run started before the replacement finishes late and only knows the old uris
        vm.publishDescriptions(mapOf("a" to description("a", "late"), "b" to description("b", "late")))
        assertEquals(setOf("a", "b2"), vm.descriptionsByUri.keys)
        assertEquals("b2", vm.descriptionsByUri["b2"]?.uri)
    }

    @Test
    fun freshDescriptionsReplaceCarriedOverOnes() {
        val vm = viewModel("a")
        vm.publishDescriptions(mapOf("a" to description("a")))
        vm.replaceFile("a", "a2")
        vm.publishDescriptions(mapOf("a2" to description("a2", "fresh")))
        assertEquals("fresh", vm.descriptionsByUri["a2"]?.detail)
    }
}
