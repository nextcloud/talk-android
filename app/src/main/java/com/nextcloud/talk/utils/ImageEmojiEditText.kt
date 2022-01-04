/*
 * Nextcloud Talk application
 *
 * @author Dariusz Olszewski
 * Copyright (C) 2021 Dariusz Olszewski <starypatyk@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.emoji.widget.EmojiEditText

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

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection {

        val ic: InputConnection = super.onCreateInputConnection(editorInfo)

        EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf("image/gif", "image/jpeg", "image/png"))

        val callback =
            InputConnectionCompat.OnCommitContentListener { inputContentInfo, flags, _ ->

                val lacksPermission = (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && lacksPermission) {
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

        return InputConnectionCompat.createWrapper(ic, editorInfo, callback)
    }
}
