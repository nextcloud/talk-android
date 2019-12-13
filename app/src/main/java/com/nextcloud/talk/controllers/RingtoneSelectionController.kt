/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import butterknife.BindView
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.NotificationSoundItem
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.utils.bundle.BundleKeys
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import java.io.IOException
import java.util.*

class RingtoneSelectionController(args: Bundle) : BaseController(), FlexibleAdapter.OnItemClickListener {

    @JvmField
    @BindView(R.id.recyclerView)
    internal var recyclerView: RecyclerView? = null

    @JvmField
    @BindView(R.id.swipe_refresh_layout)
    internal var swipeRefreshLayout: SwipeRefreshLayout? = null

    private var adapter: FlexibleAdapter<AbstractFlexibleItem<*>>? = null
    private var adapterDataObserver: RecyclerView.AdapterDataObserver? = null
    private val abstractFlexibleItemList = ArrayList<AbstractFlexibleItem<*>>()

    private val callNotificationSounds: Boolean
    private var mediaPlayer: MediaPlayer? = null
    private var cancelMediaPlayerHandler: Handler? = null

    private val ringtoneString: String
        get() = if (callNotificationSounds) {
            "android.resource://" + context.packageName +
                    "/raw/librem_by_feandesign_call"
        } else {
            "android.resource://" + context.packageName + "/raw" +
                    "/librem_by_feandesign_message"
        }

    init {
        setHasOptionsMenu(true)
        this.callNotificationSounds = args.getBoolean(BundleKeys.KEY_ARE_CALL_SOUNDS, false)
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_generic_rv, container, false)
    }

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
        when (item.itemId) {
            android.R.id.home -> return router.popCurrentController()
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun prepareViews() {
        val layoutManager = SmoothScrollLinearLayoutManager(activity!!)
        recyclerView!!.layoutManager = layoutManager
        recyclerView!!.setHasFixedSize(true)
        recyclerView!!.adapter = adapter

        adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                super.onChanged()
                findSelectedSound()
            }
        }

        adapter!!.registerAdapterDataObserver(adapterDataObserver!!)
        swipeRefreshLayout!!.isEnabled = false
    }

    @SuppressLint("LongLogTag")
    private fun findSelectedSound() {
        var foundDefault = false

        var preferencesString: String?
        if (callNotificationSounds) {
            preferencesString = appPreferences.callRingtoneUri
        } else {
            preferencesString = appPreferences.messageRingtoneUri
        }

        if (preferencesString.isNullOrEmpty()) {
            adapter!!.toggleSelection(1)
            foundDefault = true
        }

        if (!preferencesString.isNullOrEmpty() && !foundDefault) {
            try {
                val ringtoneSettings = LoganSquare.parse(preferencesString, RingtoneSettings::class.java)
                if (ringtoneSettings.ringtoneUri == null) {
                    adapter!!.toggleSelection(0)
                } else if (ringtoneSettings.ringtoneUri.toString().equals(ringtoneString)) {
                    adapter!!.toggleSelection(1)
                } else {
                    var notificationSoundItem: NotificationSoundItem
                    for (i in 2 until adapter!!.itemCount) {
                        notificationSoundItem = adapter!!.getItem(i) as NotificationSoundItem
                        if (notificationSoundItem.notificationSoundUri == ringtoneSettings.ringtoneUri.toString()) {
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

    private fun fetchNotificationSounds() {
        abstractFlexibleItemList.add(
                NotificationSoundItem(resources!!.getString(R.string.nc_settings_no_ringtone), null))
        abstractFlexibleItemList.add(NotificationSoundItem(resources!!
                .getString(R.string.nc_settings_default_ringtone), ringtoneString))

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
                val completeNotificationUri = "$notificationUri/" + cursor.getString(RingtoneManager
                        .ID_COLUMN_INDEX)

                notificationSoundItem = NotificationSoundItem(notificationTitle, completeNotificationUri)

                abstractFlexibleItemList.add(notificationSoundItem)
            }
        }

        adapter!!.updateDataSet(abstractFlexibleItemList, false)
    }

    override fun getTitle(): String? {
        return resources!!.getString(R.string.nc_settings_notification_sounds)
    }

    @SuppressLint("LongLogTag")
    override fun onItemClick(view: View, position: Int): Boolean {
        val notificationSoundItem = adapter!!.getItem(position) as NotificationSoundItem?

        var ringtoneUri: Uri? = null

        if (!TextUtils.isEmpty(notificationSoundItem!!.notificationSoundUri)) {
            ringtoneUri = Uri.parse(notificationSoundItem.notificationSoundUri)

            endMediaPlayer()
            mediaPlayer = MediaPlayer.create(activity, ringtoneUri)

            cancelMediaPlayerHandler = Handler()
            cancelMediaPlayerHandler!!.postDelayed({ endMediaPlayer() }, (mediaPlayer!!.duration + 25).toLong())
            mediaPlayer!!.start()
        }

        if (adapter!!.selectedPositions.size == 0 || adapter!!.selectedPositions[0] != position) {
            val ringtoneSettings = RingtoneSettings()
            ringtoneSettings.ringtoneName = notificationSoundItem.notificationSoundName
            ringtoneSettings.ringtoneUri = ringtoneUri

            if (callNotificationSounds) {
                try {
                    appPreferences.callRingtoneUri = LoganSquare.serialize(ringtoneSettings)
                    adapter!!.toggleSelection(position)
                    adapter!!.notifyDataSetChanged()
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to store selected ringtone for calls")
                }

            } else {
                try {
                    appPreferences.messageRingtoneUri = LoganSquare.serialize(ringtoneSettings)
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

        private val TAG = "RingtoneSelectionController"
    }
}
