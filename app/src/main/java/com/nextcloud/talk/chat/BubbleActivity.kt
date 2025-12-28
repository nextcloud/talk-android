/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Alexandre Wery <nextcloud-talk-android@alwy.be>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.utils.bundle.BundleKeys

class BubbleActivity : ChatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_talk)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.chat_toolbar)?.setNavigationOnClickListener {
            openConversationList()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(false)
            }
        })
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        super.onPrepareOptionsMenu(menu)

        menu.findItem(R.id.create_conversation_bubble)?.isVisible = false
        menu.findItem(R.id.open_conversation_in_app)?.isVisible = true

        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean =
        when (item.itemId) {
            R.id.open_conversation_in_app -> {
                openInMainApp()
                true
            }
            android.R.id.home -> {
                openConversationList()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun openInMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtras(this@BubbleActivity.intent)
            conversationUser?.id?.let { putExtra(BundleKeys.KEY_INTERNAL_USER_ID, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun openConversationList() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onSupportNavigateUp(): Boolean {
        openInMainApp()
        return true
    }

    companion object {
        fun newIntent(context: Context, roomToken: String, conversationName: String?): Intent =
            Intent(context, BubbleActivity::class.java).apply {
                putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
                conversationName?.let { putExtra(BundleKeys.KEY_CONVERSATION_NAME, it) }
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            }
    }
}
