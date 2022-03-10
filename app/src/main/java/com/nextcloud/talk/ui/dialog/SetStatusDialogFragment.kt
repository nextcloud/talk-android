/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * @author Marcel Hibbe
 * Copyright (C) 2020 Nextcloud GmbH
 * Copyright (C) 2022 Marcel Hibbe (dev@mhibbe.de)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.ui.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.PredefinedStatusClickListener
import com.nextcloud.talk.adapters.PredefinedStatusListAdapter
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogSetStatusBinding
import com.nextcloud.talk.models.database.User
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.status.ClearAt
import com.nextcloud.talk.models.json.status.Status
import com.nextcloud.talk.models.json.status.StatusType
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatus
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatusOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.vanniktech.emoji.EmojiPopup
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.dialog_set_status.*
import okhttp3.ResponseBody
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

private const val ARG_CURRENT_USER_PARAM = "currentUser"
private const val ARG_CURRENT_STATUS_PARAM = "currentStatus"

private const val POS_DONT_CLEAR = 0
private const val POS_HALF_AN_HOUR = 1
private const val POS_AN_HOUR = 2
private const val POS_FOUR_HOURS = 3
private const val POS_TODAY = 4
private const val POS_END_OF_WEEK = 5

private const val ONE_SECOND_IN_MILLIS = 1000
private const val ONE_MINUTE_IN_SECONDS = 60
private const val THIRTY_MINUTES = 30
private const val FOUR_HOURS = 4
private const val LAST_HOUR_OF_DAY = 23
private const val LAST_MINUTE_OF_HOUR = 59
private const val LAST_SECOND_OF_MINUTE = 59

@AutoInjector(NextcloudTalkApplication::class)
class SetStatusDialogFragment :
    DialogFragment(), PredefinedStatusClickListener {

    private val logTag = SetStatusDialogFragment::class.java.simpleName

    private lateinit var binding: DialogSetStatusBinding

    private var currentUser: User? = null
    private var currentStatus: Status? = null

    val predefinedStatusesList = ArrayList<PredefinedStatus>()

    private lateinit var adapter: PredefinedStatusListAdapter
    private var clearAt: Long? = null
    private lateinit var popup: EmojiPopup

    @Inject
    lateinit var ncApi: NcApi

    lateinit var credentials: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        arguments?.let {
            currentUser = it.getParcelable(ARG_CURRENT_USER_PARAM)
            currentStatus = it.getParcelable(ARG_CURRENT_STATUS_PARAM)

            credentials = ApiUtils.getCredentials(currentUser?.username, currentUser?.token)
            ncApi.getPredefinedStatuses(credentials, ApiUtils.getUrlForPredefinedStatuses(currentUser?.baseUrl))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<ResponseBody> {

                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(responseBody: ResponseBody) {
                        val predefinedStatusOverall: PredefinedStatusOverall = LoganSquare.parse(
                            responseBody
                                .string(),
                            PredefinedStatusOverall::class.java
                        )
                        predefinedStatusOverall.ocs?.data?.let { it1 -> predefinedStatusesList.addAll(it1) }

                        adapter.notifyDataSetChanged()
                    }

                    override fun onError(e: Throwable) {
                        // unused atm
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogSetStatusBinding.inflate(LayoutInflater.from(context))

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }

    @SuppressLint("DefaultLocale")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentStatus?.let {
            binding.emoji.setText(it.icon)
            binding.customStatusInput.text?.clear()
            binding.customStatusInput.setText(it.message?.trim())
            binding.setStatus.isEnabled = it.message?.isEmpty() == false
            visualizeStatus(it.status)

            if (it.clearAt > 0) {
                binding.clearStatusAfterSpinner.visibility = View.GONE
                binding.remainingClearTime.apply {
                    binding.clearStatusMessageTextView.text = getString(R.string.clear_status_message)
                    visibility = View.VISIBLE
                    text = DisplayUtils.getRelativeTimestamp(context, it.clearAt * ONE_SECOND_IN_MILLIS, true)
                        .toString()
                        .decapitalize(Locale.getDefault())
                    setOnClickListener {
                        visibility = View.GONE
                        binding.clearStatusAfterSpinner.visibility = View.VISIBLE
                        binding.clearStatusMessageTextView.text = getString(R.string.clear_status_message_after)
                    }
                }
            }
        }

        adapter = PredefinedStatusListAdapter(this, requireContext())
        adapter.list = predefinedStatusesList

        binding.predefinedStatusList.adapter = adapter
        binding.predefinedStatusList.layoutManager = LinearLayoutManager(context)

        binding.onlineStatus.setOnClickListener { setStatus(StatusType.ONLINE) }
        binding.dndStatus.setOnClickListener { setStatus(StatusType.DND) }
        binding.awayStatus.setOnClickListener { setStatus(StatusType.AWAY) }
        binding.invisibleStatus.setOnClickListener { setStatus(StatusType.INVISIBLE) }

        binding.clearStatus.setOnClickListener { clearStatus() }
        binding.setStatus.setOnClickListener { setStatusMessage() }
        binding.emoji.setOnClickListener { openEmojiPopup() }

        popup = EmojiPopup.Builder
            .fromRootView(view)
            .setOnEmojiClickListener { _, _ ->
                popup.dismiss()
                binding.emoji.clearFocus()
                val imm: InputMethodManager = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as
                    InputMethodManager
                imm.hideSoftInputFromWindow(binding.emoji.windowToken, 0)
            }
            .build(binding.emoji)
        binding.emoji.disableKeyboardInput(popup)
        binding.emoji.forceSingleEmoji()

        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        adapter.add(getString(R.string.dontClear))
        adapter.add(getString(R.string.thirtyMinutes))
        adapter.add(getString(R.string.oneHour))
        adapter.add(getString(R.string.fourHours))
        adapter.add(getString(R.string.today))
        adapter.add(getString(R.string.thisWeek))

        binding.clearStatusAfterSpinner.apply {
            this.adapter = adapter
            onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    setClearStatusAfterValue(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // nothing to do
                }
            }
        }

        binding.clearStatus.setTextColor(resources.getColor(R.color.colorPrimary))
        binding.setStatus.setBackgroundColor(resources.getColor(R.color.colorPrimary))

        binding.customStatusInput.highlightColor = resources.getColor(R.color.colorPrimary)

        binding.customStatusInput.doAfterTextChanged { text ->
            binding.setStatus.isEnabled = !text.isNullOrEmpty()
        }
    }

    @Suppress("ComplexMethod")
    private fun setClearStatusAfterValue(item: Int) {

        val currentTime = System.currentTimeMillis() / ONE_SECOND_IN_MILLIS

        when (item) {
            POS_DONT_CLEAR -> {
                // don't clear
                clearAt = null
            }

            POS_HALF_AN_HOUR -> {
                // 30 minutes
                clearAt = currentTime + THIRTY_MINUTES * ONE_MINUTE_IN_SECONDS
            }

            POS_AN_HOUR -> {
                // one hour
                clearAt = currentTime + ONE_MINUTE_IN_SECONDS * ONE_MINUTE_IN_SECONDS
            }

            POS_FOUR_HOURS -> {
                // four hours
                clearAt = currentTime + FOUR_HOURS * ONE_MINUTE_IN_SECONDS * ONE_MINUTE_IN_SECONDS
            }

            POS_TODAY -> {
                // today
                val date = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, LAST_HOUR_OF_DAY)
                    set(Calendar.MINUTE, LAST_MINUTE_OF_HOUR)
                    set(Calendar.SECOND, LAST_SECOND_OF_MINUTE)
                }
                clearAt = date.timeInMillis / ONE_SECOND_IN_MILLIS
            }

            POS_END_OF_WEEK -> {
                // end of week
                val date = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, LAST_HOUR_OF_DAY)
                    set(Calendar.MINUTE, LAST_MINUTE_OF_HOUR)
                    set(Calendar.SECOND, LAST_SECOND_OF_MINUTE)
                }

                while (date.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                    date.add(Calendar.DAY_OF_YEAR, 1)
                }

                clearAt = date.timeInMillis / ONE_SECOND_IN_MILLIS
            }
        }
    }

    @Suppress("ReturnCount")
    private fun clearAtToUnixTime(clearAt: ClearAt?): Long {
        if (clearAt != null) {
            if (clearAt.type == "period") {
                return System.currentTimeMillis() / ONE_SECOND_IN_MILLIS + clearAt.time.toLong()
            } else if (clearAt.type == "end-of") {
                if (clearAt.time == "day") {
                    val date = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, LAST_HOUR_OF_DAY)
                        set(Calendar.MINUTE, LAST_MINUTE_OF_HOUR)
                        set(Calendar.SECOND, LAST_SECOND_OF_MINUTE)
                    }
                    return date.timeInMillis / ONE_SECOND_IN_MILLIS
                }
            }
        }

        return -1
    }

    private fun openEmojiPopup() {
        popup.show()
    }

    private fun clearStatus() {
        val credentials = ApiUtils.getCredentials(currentUser?.username, currentUser?.token)
        ncApi.statusDeleteMessage(credentials, ApiUtils.getUrlForStatusMessage(currentUser?.baseUrl))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }
                override fun onNext(statusOverall: GenericOverall) {
                    // unused atm
                }
                override fun onError(e: Throwable) {
                    Log.e(logTag, "Failed to clear status", e)
                }

                override fun onComplete() {
                    dismiss()
                }
            })
    }

    private fun setStatus(statusType: StatusType) {
        visualizeStatus(statusType)

        ncApi.setStatusType(credentials, ApiUtils.getUrlForSetStatusType(currentUser?.baseUrl), statusType.string)
            .subscribeOn(
                Schedulers
                    .io()
            )
            .observeOn(AndroidSchedulers.mainThread()).subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }
                override fun onNext(statusOverall: GenericOverall) {
                    Log.d(logTag, "statusType successfully set")
                }

                override fun onError(e: Throwable) {
                    Log.e(logTag, "Failed to set statusType", e)
                    clearTopStatus()
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun visualizeStatus(statusType: String) {
        StatusType.values().firstOrNull { it.name == statusType.uppercase(Locale.ROOT) }?.let { visualizeStatus(it) }
    }

    private fun visualizeStatus(statusType: StatusType) {
        clearTopStatus()
        when (statusType) {
            StatusType.ONLINE -> {
                binding.onlineStatus.setCardBackgroundColor(resources.getColor(R.color.colorPrimary))
                binding.onlineHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text_dark_background))
            }
            StatusType.AWAY -> {
                binding.awayStatus.setCardBackgroundColor(resources.getColor(R.color.colorPrimary))
                binding.awayHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text_dark_background))
            }
            StatusType.DND -> {
                binding.dndStatus.setCardBackgroundColor(resources.getColor(R.color.colorPrimary))
                binding.dndHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text_dark_background))
            }
            StatusType.INVISIBLE -> {
                binding.invisibleStatus.setCardBackgroundColor(resources.getColor(R.color.colorPrimary))
                binding.invisibleHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text_dark_background))
            }
            else -> Log.d(logTag, "unknown status")
        }
    }

    private fun clearTopStatus() {
        context?.let {
            val grey = it.resources.getColor(R.color.grey_200)
            binding.onlineStatus.setCardBackgroundColor(grey)
            binding.awayStatus.setCardBackgroundColor(grey)
            binding.dndStatus.setCardBackgroundColor(grey)
            binding.invisibleStatus.setCardBackgroundColor(grey)

            binding.onlineHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text))
            binding.awayHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text))
            binding.dndHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text))
            binding.invisibleHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text))
        }
    }

    private fun setStatusMessage() {

        val inputText = binding.customStatusInput.text.toString().ifEmpty { "" }
        // The endpoint '/message/custom' expects a valid emoji as string or null
        val statusIcon = binding.emoji.text.toString().ifEmpty { null }

        ncApi.setCustomStatusMessage(
            credentials,
            ApiUtils.getUrlForSetCustomStatus(currentUser?.baseUrl),
            statusIcon,
            inputText,
            clearAt
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {

                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(t: GenericOverall) {
                    Log.d(logTag, "CustomStatusMessage successfully set")
                    dismiss()
                }

                override fun onError(e: Throwable) {
                    Log.e(logTag, "failed to set CustomStatusMessage", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return binding.root
    }

    override fun onClick(predefinedStatus: PredefinedStatus) {
        clearAt = clearAtToUnixTime(predefinedStatus.clearAt)
        binding.emoji.setText(predefinedStatus.icon)
        binding.customStatusInput.text?.clear()
        binding.customStatusInput.text?.append(predefinedStatus.message)

        binding.remainingClearTime.visibility = View.GONE
        binding.clearStatusAfterSpinner.visibility = View.VISIBLE
        binding.clearStatusMessageTextView.text = getString(R.string.clear_status_message_after)

        if (predefinedStatus.clearAt == null) {
            binding.clearStatusAfterSpinner.setSelection(0)
        } else {
            val clearAt = predefinedStatus.clearAt!!
            if (clearAt.type == "period") {
                when (clearAt.time) {
                    "1800" -> binding.clearStatusAfterSpinner.setSelection(POS_HALF_AN_HOUR)
                    "3600" -> binding.clearStatusAfterSpinner.setSelection(POS_AN_HOUR)
                    "14400" -> binding.clearStatusAfterSpinner.setSelection(POS_FOUR_HOURS)
                    else -> binding.clearStatusAfterSpinner.setSelection(POS_DONT_CLEAR)
                }
            } else if (clearAt.type == "end-of") {
                when (clearAt.time) {
                    "day" -> binding.clearStatusAfterSpinner.setSelection(POS_TODAY)
                    "week" -> binding.clearStatusAfterSpinner.setSelection(POS_END_OF_WEEK)
                    else -> binding.clearStatusAfterSpinner.setSelection(POS_DONT_CLEAR)
                }
            }
        }
        setClearStatusAfterValue(binding.clearStatusAfterSpinner.selectedItemPosition)
    }

    /**
     * Fragment creator
     */
    companion object {
        @JvmStatic
        fun newInstance(user: User, status: Status): SetStatusDialogFragment {
            val args = Bundle()
            args.putParcelable(ARG_CURRENT_USER_PARAM, user)
            args.putParcelable(ARG_CURRENT_STATUS_PARAM, status)

            val dialogFragment = SetStatusDialogFragment()
            dialogFragment.arguments = args
            return dialogFragment
        }
    }
}
