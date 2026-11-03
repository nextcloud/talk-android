/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist

import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.SharedFileCopies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Exported entry point for the content other apps share to Talk, from the share sheet or one of its direct share
 * targets. It copies the shared files into the app (see [SharedFileCopies]), so the chat and the upload don't depend
 * on the read access of the other app, and opens the conversation list to pick the conversation with only that
 * content, see [ShareListIntentFactory].
 *
 * The share sheet starts it in a task of its own, which is kept out of the recents, also after the list replaced it
 * there: the chat is opened in the task of the app (see ConversationsListActivity.openSharedConversationInAppTask).
 */
class ShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // shown while large files are copied
        setContentView(
            FrameLayout(this).apply {
                addView(
                    CircularProgressIndicator(context).apply { isIndeterminate = true },
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
            }
        )
        lifecycleScope.launch {
            val shareListIntent = ShareListIntentFactory(this@ShareActivity).create(intent)
            try {
                // e.g. the screen was turned off while the files were copied: the list opens when it is shown again
                lifecycle.withStarted {
                    if (shareListIntent != null) {
                        startActivity(shareListIntent)
                    } else {
                        Toast.makeText(this@ShareActivity, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
                    }
                    finish()
                }
            } catch (e: CancellationException) {
                // closed before it opened the list, which otherwise removes the copies of a share that is left there
                shareListIntent?.let { SharedFileCopies.removeSharesLater(ShareListIntentFactory.sharedUrisOf(it)) }
                throw e
            }
        }
    }

    // Left with Home or the recents while the files are copied: its task is kept out of the recents, so the share
    // couldn't be continued and is left.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finish()
    }
}
