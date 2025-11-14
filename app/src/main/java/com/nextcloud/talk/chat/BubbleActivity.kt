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
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.bundle.BundleKeys

class BubbleActivity : ChatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowHomeEnabled(false)
    }
    
    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        
        menu.findItem(R.id.create_conversation_bubble)?.isVisible = false
        menu.findItem(R.id.open_conversation_in_app)?.isVisible = true
        
        return true
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.open_conversation_in_app -> {
                openInMainApp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun openInMainApp() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtras(this@BubbleActivity.intent)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        moveTaskToBack(false)
    }
    
    override fun onBackPressed() {
        moveTaskToBack(false)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onSupportNavigateUp(): Boolean {
        moveTaskToBack(false)
        return true
    }
    
    companion object {
        fun newIntent(context: Context, roomToken: String, conversationName: String?): Intent {
            return Intent(context, BubbleActivity::class.java).apply {
                putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
                conversationName?.let { putExtra(BundleKeys.KEY_CONVERSATION_NAME, it) }
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            }
        }
    }
}