package com.nextcloud.talk.utils.rx

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class ProgressRequestBody(inputFile: File, inputMimeType: String) : RequestBody() {

    val file: File = inputFile
    val mimeType: String = inputMimeType
    val ignoreWriteToCalls = 1

    var writeToCallsAmount = 0

    protected val getProgressPublishSubject: PublishSubject<Float> = PublishSubject.create<Float>()

    fun getProgress(): Observable<Float> {
        return getProgressPublishSubject
    }


    override fun contentType(): MediaType {
        return mimeType.toMediaTypeOrNull()!!
    }

    @Throws(IOException::class)
    override fun contentLength(): Long {
        return file.length()
    }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        writeToCallsAmount++

        val fileLength = file.length()
        val buffer = ByteArray(BUFFER_SIZE)
        val inputStream = FileInputStream(file)
        var uploaded: Long = 0

        try {
            var read: Int
            var lastProgressPercentUpdate = 0.0f
            read = inputStream.read(buffer)
            while (read != -1) {

                uploaded += read.toLong()
                sink.write(buffer, 0, read)
                read = inputStream.read(buffer)

                if (writeToCallsAmount > ignoreWriteToCalls ) {
                    val progress = (uploaded.toFloat() / fileLength.toFloat()) * 100f
                    if (progress - lastProgressPercentUpdate > 1 || progress == 100f) {
                        getProgressPublishSubject.onNext(progress)
                        lastProgressPercentUpdate = progress
                    }
                }
            }
        } finally {
            inputStream.close()
        }
    }


    companion object {
        private val BUFFER_SIZE = 2048
    }
}