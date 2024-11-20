/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Dariusz Olszewski <starypatyk@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.emoji2.widget.EmojiEditText
import com.nextcloud.talk.utils.Mimetype.IMAGE_GIF
import com.nextcloud.talk.utils.Mimetype.IMAGE_JPEG
import com.nextcloud.talk.utils.Mimetype.IMAGE_PNG

/*
Subclass of EmojiEditText with support for image keyboards - primarily for GIF handling. ;-)
Implementation based on this example:
https://developer.android.com/guide/topics/text/image-keyboard
 */
class ImageEmojiEditText : EmojiEditText {

    // Callback function to be called when the user selects an image, pass image Uri
    lateinit var onCommitContentListener: ((Uri) -> Unit)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    @Suppress("Detekt.TooGenericExceptionCaught")
    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
        val ic: InputConnection? = super.onCreateInputConnection(editorInfo)

        EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf(IMAGE_GIF, IMAGE_JPEG, IMAGE_PNG))

        val callback =
            InputConnectionCompat.OnCommitContentListener { inputContentInfo, flags, _ ->

                val lacksPermission = (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
                if (lacksPermission) {
                    try {
                        inputContentInfo.requestPermission()
                    } catch (e: Exception) {
                        return@OnCommitContentListener false
                    }
                }

                if (::onCommitContentListener.isInitialized) {
                    onCommitContentListener.invoke(inputContentInfo.contentUri)
                    return@OnCommitContentListener true
                } else {
                    return@OnCommitContentListener false
                }
            }

        return InputConnectionCompat.createWrapper(ic!!, editorInfo, callback)
    }
}
