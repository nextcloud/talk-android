/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecentMediaLoaderTest {

    class FakeMediaProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<String>?,
            selection: String?,
            selectionArgs: Array<String>?,
            sortOrder: String?
        ): Cursor {
            val isVideo = uri.toString().startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString())
            if (isVideo && refuseVideos || !isVideo && refuseImages) {
                throw SecurityException("refused")
            }
            val cursor = MatrixCursor(projection!!)
            (if (isVideo) videoRows else imageRows).forEach { (id, date) ->
                cursor.addRow(projection.map<String, Any> { if (it == MediaStore.MediaColumns._ID) id else date })
            }
            return cursor
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int =
            0

        companion object {
            var refuseImages = false
            var refuseVideos = false
            var imageRows = listOf(1L to 100L, 3L to 300L)
            var videoRows = listOf(2L to 200L)
        }
    }

    @Before
    fun setUp() {
        FakeMediaProvider.refuseImages = false
        FakeMediaProvider.refuseVideos = false
        Robolectric.setupContentProvider(FakeMediaProvider::class.java, MediaStore.AUTHORITY)
    }

    @After
    fun tearDown() {
        FakeMediaProvider.refuseImages = false
        FakeMediaProvider.refuseVideos = false
    }

    private fun load(): List<RecentMedia> {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        return runBlocking { RecentMediaLoader.load(resolver) }
    }

    @Test
    fun bothTypesAreMergedNewestFirst() {
        assertEquals(listOf(3L, 2L, 1L), load().map { it.id })
    }

    @Test
    fun refusedVideosKeepThePhotos() {
        FakeMediaProvider.refuseVideos = true
        assertEquals(listOf(3L, 1L), load().map { it.id })
    }

    @Test
    fun refusedPhotosKeepTheVideos() {
        FakeMediaProvider.refuseImages = true
        assertEquals(listOf(2L), load().map { it.id })
    }

    @Test
    fun bothRefusedGiveAnEmptyList() {
        FakeMediaProvider.refuseImages = true
        FakeMediaProvider.refuseVideos = true
        assertEquals(emptyList<RecentMedia>(), load())
    }
}
