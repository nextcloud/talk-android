/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.stream

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import java.io.EOFException
import java.io.IOException

@OptIn(UnstableApi::class)
internal class ProgressiveVideoDataSource(
    private val download: ProgressiveVideoFile,
    private val requestRange: (Long, String) -> Call<ResponseBody>
) : BaseDataSource(false) {
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false
    private var rangeBody: ResponseBody? = null

    @Volatile private var rangeCall: Call<ResponseBody>? = null

    @Volatile private var closed = false

    override fun open(dataSpec: DataSpec): Long {
        closed = false
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        position = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (download.length >= 0) {
            (download.length - position).coerceAtLeast(0)
        } else {
            C.LENGTH_UNSET.toLong()
        }
        download.acquire()
        opened = true
        transferStarted(dataSpec)
        if (download.needsRange(position)) openRange()
        return remaining
    }

    private fun openRange() {
        val call = requestRange(position, checkNotNull(download.validator))
        rangeCall = call
        val response = call.execute()
        if (response.code() == HTTP_OK) {
            // Servers without range support continue through the original sequential download.
            response.body()?.close()
            return
        }
        if (response.code() != HTTP_PARTIAL) {
            response.errorBody()?.close()
            response.body()?.close()
            throw IOException("Video range request failed: ${response.code()}")
        }
        val body = requireBody(response)
        try {
            val size = VideoRangeResponse.validate(response.headers()["Content-Range"], position, download.length)
            validateLength(body.contentLength(), size)
            rangeBody = body
        } catch (exception: IOException) {
            body.close()
            throw exception
        }
    }

    private fun requireBody(response: Response<ResponseBody>): ResponseBody =
        response.body() ?: throw IOException("Empty video response")

    private fun validateLength(actual: Long, expected: Long) {
        if (actual >= 0 && actual != expected) throw IOException("Invalid video length")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        when {
            length == 0 -> 0
            remaining == 0L -> C.RESULT_END_OF_INPUT
            else -> readAvailable(buffer, offset, length)
        }

    private fun readAvailable(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = if (remaining < 0) length else minOf(length.toLong(), remaining).toInt()
        val read = rangeBody?.byteStream()?.read(buffer, offset, count)
            ?: download.read(position, buffer, offset, count) { closed }
        if (read < 0) {
            if (remaining > 0) throw EOFException("Incomplete video stream")
            return C.RESULT_END_OF_INPUT
        }
        position += read
        if (remaining >= 0) remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closed = true
        rangeCall?.cancel()
        rangeCall = null
        rangeBody?.close()
        rangeBody = null
        uri = null
        if (opened) {
            opened = false
            download.release()
            transferEnded()
        }
    }

    companion object {
        private const val HTTP_OK = 200
        private const val HTTP_PARTIAL = 206
    }
}
