/*
 * Nextcloud Android client application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2020 Nextcloud GmbH
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

    private val logTag = ChooseAccountDialogFragment::class.java.simpleName


    private lateinit var binding: DialogSetStatusBinding

    private var currentUser: User? = null
    private var currentStatus: Status? = null

    // private lateinit var accountManager: UserAccountManager
    // private lateinit var predefinedStatus: ArrayList<PredefinedStatus>
    val predefinedStatusesList = ArrayList<PredefinedStatus>()

    private lateinit var adapter: PredefinedStatusListAdapter
    private var selectedPredefinedMessageId: String? = null
    private var clearAt: Long? = -1
    private lateinit var popup: EmojiPopup

    // @Inject
    // lateinit var arbitraryDataProvider: ArbitraryDataProvider
    //
    // @Inject
    // lateinit var asyncRunner: AsyncRunner
    //
    // @Inject
    // lateinit var clientFactory: ClientFactory

    @Inject
    lateinit var ncApi: NcApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        arguments?.let {
            currentUser = it.getParcelable(ARG_CURRENT_USER_PARAM)
            currentStatus = it.getParcelable(ARG_CURRENT_STATUS_PARAM)

            val credentials = ApiUtils.getCredentials(currentUser?.username, currentUser?.token)
            ncApi.getPredefinedStatuses(credentials, ApiUtils.getUrlForPredefinedStatuses(currentUser?.baseUrl))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<ResponseBody> {

                    override fun onSubscribe(d: Disposable) {
                    }

                    override fun onNext(responseBody: ResponseBody) {
                        val predefinedStatusOverall : PredefinedStatusOverall = LoganSquare.parse(responseBody
                                .string(),
                                PredefinedStatusOverall::class.java)
                        predefinedStatusesList.addAll(predefinedStatusOverall.getOcs().data)
                    }

                    override fun onError(e: Throwable) {
                    }

                    override fun onComplete() {}

                })
        }

      //  EmojiManager.install(GoogleEmojiProvider())
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


        // accountManager = (activity as BaseActivity).userAccountManager
        //
        currentStatus?.let {
            binding.emoji.setText(it.icon)
            binding.customStatusInput.text?.clear()
            binding.customStatusInput.setText(it.message)
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
        // ThemeButtonUtils.colorPrimaryButton(binding.setStatus, context)

        binding.customStatusInput.highlightColor = resources.getColor(R.color.colorPrimary)
        // ThemeTextInputUtils.colorTextInput(
        //     binding.customStatusInputContainer,
        //     binding.customStatusInput,
        //     ThemeColorUtils.primaryColor(activity)
        // )
    }

    @Suppress("ComplexMethod")
    private fun setClearStatusAfterValue(item: Int) {
        when (item) {
            POS_DONT_CLEAR -> {
                // don't clear
                clearAt = null
            }

            POS_HALF_AN_HOUR -> {
                // 30 minutes
                clearAt = System.currentTimeMillis() / ONE_SECOND_IN_MILLIS + THIRTY_MINUTES * ONE_MINUTE_IN_SECONDS
            }

            POS_AN_HOUR -> {
                // one hour
                clearAt =
                    System.currentTimeMillis() / ONE_SECOND_IN_MILLIS + ONE_MINUTE_IN_SECONDS * ONE_MINUTE_IN_SECONDS
            }

            POS_FOUR_HOURS -> {
                // four hours
                clearAt =
                    System.currentTimeMillis() / ONE_SECOND_IN_MILLIS
                +FOUR_HOURS * ONE_MINUTE_IN_SECONDS * ONE_MINUTE_IN_SECONDS
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
            if (clearAt.type.equals("period")) {
                return System.currentTimeMillis() / ONE_SECOND_IN_MILLIS + clearAt.time.toLong()
            } else if (clearAt.type.equals("end-of")) {
                if (clearAt.time.equals("day")) {
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
        // asyncRunner.postQuickTask(
        //     ClearStatusTask(accountManager.currentOwnCloudAccount?.savedAccount, context),
        //     { dismiss(it) }
        // )

        val credentials = ApiUtils.getCredentials(currentUser?.username, currentUser?.token)
        ncApi.statusDeleteMessage(credentials, ApiUtils.getUrlForStatus(currentUser?.baseUrl)).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {}
                override fun onNext(statusOverall: GenericOverall) {}
                override fun onError(e: Throwable) {
                    Log.e(logTag, "Error removing attendee from conversation", e)
                }
                override fun onComplete() {
                    dismiss()
                }
            })
    }

    private fun setStatus(statusType: StatusType) {
        visualizeStatus(statusType)

        // asyncRunner.postQuickTask(
        //     SetStatusTask(
        //         statusType,
        //         accountManager.currentOwnCloudAccount?.savedAccount,
        //         context
        //     ),
        //     {
        //         if (!it) {
        //             clearTopStatus()
        //         }
        //     },
        //     { clearTopStatus() }
        // )
    }

    private fun visualizeStatus(statusType: String) {
        StatusType.values().firstOrNull { it.name == statusType.uppercase(Locale.ROOT) }?.let { visualizeStatus(it) }
    }

    private fun visualizeStatus(statusType: StatusType) {
        when (statusType) {
            StatusType.ONLINE -> {
                clearTopStatus()
                binding.onlineStatus.setBackgroundColor(resources.getColor(R.color.colorPrimary))
            }
            StatusType.AWAY -> {
                clearTopStatus()
                binding.awayStatus.setBackgroundColor(resources.getColor(R.color.colorPrimary))
            }
            StatusType.DND -> {
                clearTopStatus()
                binding.dndStatus.setBackgroundColor(resources.getColor(R.color.colorPrimary))
            }
            StatusType.INVISIBLE -> {
                clearTopStatus()
                binding.invisibleStatus.setBackgroundColor(resources.getColor(R.color.colorPrimary))
            }
            else -> clearTopStatus()
        }
    }

    private fun clearTopStatus() {
        context?.let {
            val grey = it.resources.getColor(R.color.grey_200)
            binding.onlineStatus.setBackgroundColor(grey)
            binding.awayStatus.setBackgroundColor(grey)
            binding.dndStatus.setBackgroundColor(grey)
            binding.invisibleStatus.setBackgroundColor(grey)
        }
    }


    private fun setStatusMessage() {
        // if (selectedPredefinedMessageId != null) {
        //     asyncRunner.postQuickTask(
        //         SetPredefinedCustomStatusTask(
        //             selectedPredefinedMessageId!!,
        //             clearAt,
        //             accountManager.currentOwnCloudAccount?.savedAccount,
        //             context
        //         ),
        //         { dismiss(it) }
        //     )
        // } else {
        //     asyncRunner.postQuickTask(
        //         SetUserDefinedCustomStatusTask(
        //             binding.customStatusInput.text.toString(),
        //             binding.emoji.text.toString(),
        //             clearAt,
        //             accountManager.currentOwnCloudAccount?.savedAccount,
        //             context
        //         ),
        //         { dismiss(it) }
        //     )
        // }
    }

    // private fun dismiss(boolean: Boolean) {
    //     if (boolean) {
    //         dismiss()
    //     }
    // }



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
            // dialogFragment.setStyle(STYLE_NORMAL, R.style.Theme_ownCloud_Dialog)
            return dialogFragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return binding.root
    }

    override fun onClick(predefinedStatus: PredefinedStatus) {
        selectedPredefinedMessageId = predefinedStatus.id
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
            if (clearAt.type.equals("period")) {
                when (clearAt.time) {
                    "1800" -> binding.clearStatusAfterSpinner.setSelection(POS_HALF_AN_HOUR)
                    "3600" -> binding.clearStatusAfterSpinner.setSelection(POS_AN_HOUR)
                    "14400" -> binding.clearStatusAfterSpinner.setSelection(POS_FOUR_HOURS)
                    else -> binding.clearStatusAfterSpinner.setSelection(POS_DONT_CLEAR)
                }
            } else if (clearAt.type.equals("end-of")) {
                when (clearAt.time) {
                    "day" -> binding.clearStatusAfterSpinner.setSelection(POS_TODAY)
                    "week" -> binding.clearStatusAfterSpinner.setSelection(POS_END_OF_WEEK)
                    else -> binding.clearStatusAfterSpinner.setSelection(POS_DONT_CLEAR)
                }
            }
        }
        setClearStatusAfterValue(binding.clearStatusAfterSpinner.selectedItemPosition)
    }
    //
    // @VisibleForTesting
    // fun setPredefinedStatus(predefinedStatus: ArrayList<PredefinedStatus>) {
    //     adapter.list = predefinedStatus
    //     binding.predefinedStatusList.adapter?.notifyDataSetChanged()
    // }



}
