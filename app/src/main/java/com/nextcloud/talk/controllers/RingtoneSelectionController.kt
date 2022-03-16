/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.controllers

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.NotificationSoundItem
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.base.NewBaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.databinding.ControllerGenericRvBinding
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ARE_CALL_SOUNDS
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import java.io.IOException
import java.util.ArrayList

@AutoInjector(NextcloudTalkApplication::class)
class RingtoneSelectionController(args: Bundle) :
    NewBaseController(
        R.layout.controller_generic_rv,
        args
    ),
    FlexibleAdapter.OnItemClickListener {
    private val binding: ControllerGenericRvBinding by viewBinding(ControllerGenericRvBinding::bind)

    private var adapter: FlexibleAdapter<*>? = null
    private var adapterDataObserver: RecyclerView.AdapterDataObserver? = null
    private val abstractFlexibleItemList: MutableList<AbstractFlexibleItem<*>> = ArrayList()
    private val callNotificationSounds: Boolean
    private var mediaPlayer: MediaPlayer? = null
    private var cancelMediaPlayerHandler: Handler? = null

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        if (adapter == null) {
            adapter = FlexibleAdapter(abstractFlexibleItemList, activity, false)
            adapter!!.setNotifyChangeOfUnfilteredItems(true).mode = SelectableAdapter.Mode.SINGLE
            adapter!!.addListener(this)
            cancelMediaPlayerHandler = Handler()
        }
        adapter!!.addListener(this)
        prepareViews()
        fetchNotificationSounds()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            router.popCurrentController()
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun prepareViews() {
        val layoutManager: RecyclerView.LayoutManager = SmoothScrollLinearLayoutManager(activity)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = adapter
        adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                super.onChanged()
                findSelectedSound()
            }
        }
        adapter!!.registerAdapterDataObserver(adapterDataObserver!!)
        binding.swipeRefreshLayout.isEnabled = false
    }

    @SuppressLint("LongLogTag")
    private fun findSelectedSound() {
        var foundDefault = false
        var preferencesString: String? = null
        if (callNotificationSounds &&
            TextUtils.isEmpty(appPreferences!!.callRingtoneUri.also { preferencesString = it }) ||
            !callNotificationSounds &&
            TextUtils.isEmpty(appPreferences!!.messageRingtoneUri.also { preferencesString = it })
        ) {
            adapter!!.toggleSelection(1)
            foundDefault = true
        }
        if (!TextUtils.isEmpty(preferencesString) && !foundDefault) {
            try {
                val ringtoneSettings: RingtoneSettings =
                    LoganSquare.parse<RingtoneSettings>(preferencesString, RingtoneSettings::class.java)
                if (ringtoneSettings.getRingtoneUri() == null) {
                    adapter!!.toggleSelection(0)
                } else if (ringtoneSettings.getRingtoneUri().toString() == ringtoneString) {
                    adapter!!.toggleSelection(1)
                } else {
                    var notificationSoundItem: NotificationSoundItem?
                    for (i in 2 until adapter!!.itemCount) {
                        notificationSoundItem = adapter!!.getItem(i) as NotificationSoundItem?
                        if (
                            notificationSoundItem!!.notificationSoundUri == ringtoneSettings.getRingtoneUri().toString()
                        ) {
                            adapter!!.toggleSelection(i)
                            break
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to parse ringtone settings")
            }
        }
        adapter!!.unregisterAdapterDataObserver(adapterDataObserver!!)
        adapterDataObserver = null
    }

    private val ringtoneString: String
        get() = if (callNotificationSounds) {
            NotificationUtils.DEFAULT_CALL_RINGTONE_URI
        } else {
            NotificationUtils.DEFAULT_MESSAGE_RINGTONE_URI
        }

    private fun fetchNotificationSounds() {
        abstractFlexibleItemList.add(
            NotificationSoundItem(
                resources!!.getString(R.string.nc_settings_no_ringtone),
                null
            )
        )
        abstractFlexibleItemList.add(
            NotificationSoundItem(
                resources!!.getString(R.string.nc_settings_default_ringtone),
                ringtoneString
            )
        )
        if (activity != null) {
            val manager = RingtoneManager(activity)
            if (callNotificationSounds) {
                manager.setType(RingtoneManager.TYPE_RINGTONE)
            } else {
                manager.setType(RingtoneManager.TYPE_NOTIFICATION)
            }
            val cursor = manager.cursor
            var notificationSoundItem: NotificationSoundItem
            while (cursor.moveToNext()) {
                val notificationTitle = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val notificationUri = cursor.getString(RingtoneManager.URI_COLUMN_INDEX)
                val completeNotificationUri = notificationUri + "/" + cursor.getString(RingtoneManager.ID_COLUMN_INDEX)
                notificationSoundItem = NotificationSoundItem(notificationTitle, completeNotificationUri)
                abstractFlexibleItemList.add(notificationSoundItem)
            }
        }
        adapter!!.updateDataSet(abstractFlexibleItemList as List<Nothing>?, false)
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        val notificationSoundItem = adapter!!.getItem(position) as NotificationSoundItem?
        var ringtoneUri: Uri? = null
        if (!TextUtils.isEmpty(notificationSoundItem!!.notificationSoundUri)) {
            ringtoneUri = Uri.parse(notificationSoundItem.notificationSoundUri)
            endMediaPlayer()
            mediaPlayer = MediaPlayer.create(activity, ringtoneUri)
            cancelMediaPlayerHandler = Handler()
            cancelMediaPlayerHandler!!.postDelayed(
                { endMediaPlayer() },
                (mediaPlayer!!.duration + DURATION_EXTENSION).toLong()
            )
            mediaPlayer!!.start()
        }
        if (adapter!!.selectedPositions.size == 0 || adapter!!.selectedPositions[0] != position) {
            val ringtoneSettings = RingtoneSettings()
            ringtoneSettings.setRingtoneName(notificationSoundItem.notificationSoundName)
            ringtoneSettings.setRingtoneUri(ringtoneUri)
            if (callNotificationSounds) {
                try {
                    appPreferences!!.callRingtoneUri = LoganSquare.serialize(ringtoneSettings)
                    adapter!!.toggleSelection(position)
                    adapter!!.notifyDataSetChanged()
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to store selected ringtone for calls")
                }
            } else {
                try {
                    appPreferences!!.messageRingtoneUri = LoganSquare.serialize(ringtoneSettings)
                    adapter!!.toggleSelection(position)
                    adapter!!.notifyDataSetChanged()
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to store selected ringtone for calls")
                }
            }
        }
        return true
    }

    private fun endMediaPlayer() {
        if (cancelMediaPlayerHandler != null) {
            cancelMediaPlayerHandler!!.removeCallbacksAndMessages(null)
        }
        if (mediaPlayer != null) {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.stop()
            }
            mediaPlayer!!.release()
            mediaPlayer = null
        }
    }

    public override fun onDestroy() {
        endMediaPlayer()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RingtoneSelection"
        private const val DURATION_EXTENSION = 25
    }

    init {
        setHasOptionsMenu(true)
        sharedApplication!!.componentApplication.inject(this)
        callNotificationSounds = args.getBoolean(KEY_ARE_CALL_SOUNDS, false)
    }

    override val title: String
        get() =
            resources!!.getString(R.string.nc_settings_notification_sounds)
}
