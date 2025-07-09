/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-FileCopyrightText: 2020 ownCloud GmbH
 * SPDX-License-Identifier: MIT
 */
package com.nextcloud.talk.upload.chunked

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * A Request body that represents a file chunk and include information about the progress when uploading it
 *
 * @author David González Verdugo
 */
class ChunkFromFileRequestBody(
    file: File,
    contentType: MediaType?,
    channel: FileChannel?,
    chunkSize: Long,
    offset: Long,
    listener: OnDataTransferProgressListener
) : RequestBody() {
    private val mFile: File
    private val mContentType: MediaType?
    private val mChannel: FileChannel
    private val mChunkSize: Long
    private val mOffset: Long
    private var mTransferred: Long
    private var mDataTransferListener: OnDataTransferProgressListener
    private val mBuffer = ByteBuffer.allocate(BUFFER_CAPACITY)
    override fun contentLength(): Long =
        try {
            mChunkSize.coerceAtMost(mChannel.size() - mOffset)
        } catch (e: IOException) {
            mChunkSize
        }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        var readCount: Int
        try {
            mChannel.position(mOffset)
            var size = mFile.length()
            if (size == 0L) {
                size = -1
            }
            val maxCount = (mOffset + mChunkSize - 1).coerceAtMost(mChannel.size())
            var percentageOld = 0
            while (mChannel.position() < maxCount) {
                readCount = mChannel.read(mBuffer)
                sink.buffer.write(mBuffer.array(), 0, readCount)
                mBuffer.clear()
                if (mTransferred < maxCount) { // condition to avoid accumulate progress for repeated chunks
                    mTransferred += readCount.toLong()
                }

                val percentage =
                    if (size > ZERO_PERCENT) (mTransferred * HUNDRED_PERCENT / size).toInt() else ZERO_PERCENT
                if (percentage > percentageOld) {
                    percentageOld = percentage
                    mDataTransferListener.onTransferProgress(
                        percentage
                    )
                }
            }
        } catch (io: IOException) {
            // any read problem will be handled as if the file is not there
            val fnf = java.io.FileNotFoundException("Exception reading source file")
            fnf.initCause(io)
            throw fnf
        }
    }

    override fun contentType(): MediaType? = mContentType

    companion object {
        private val TAG = ChunkFromFileRequestBody::class.java.simpleName
        private const val BUFFER_CAPACITY = 4096
        private const val HUNDRED_PERCENT = 100
        private const val ZERO_PERCENT = 0
    }

    init {
        requireNotNull(channel) { "File may not be null" }
        require(chunkSize > 0) { "Chunk size must be greater than zero" }
        mFile = file
        mChannel = channel
        mChunkSize = chunkSize
        mOffset = offset
        mTransferred = offset
        mDataTransferListener = listener
        mContentType = contentType
    }
}
