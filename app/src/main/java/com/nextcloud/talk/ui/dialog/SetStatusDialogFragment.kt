/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022-2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2020 Nextcloud GmbH
 * SPDX-FileCopyrightText: 2020 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.PredefinedStatusClickListener
import com.nextcloud.talk.adapters.PredefinedStatusListAdapter
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogSetStatusBinding
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.status.ClearAt
import com.nextcloud.talk.models.json.status.Status
import com.nextcloud.talk.models.json.status.StatusOverall
import com.nextcloud.talk.models.json.status.StatusType
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatus
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatusOverall
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil.isRestoreStatusAvailable
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.vanniktech.emoji.EmojiPopup
import com.vanniktech.emoji.installDisableKeyboardInput
import com.vanniktech.emoji.installForceSingleEmoji
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.ResponseBody
import retrofit2.HttpException
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
    DialogFragment(),
    PredefinedStatusClickListener {

    private var selectedPredefinedStatus: PredefinedStatus? = null

    private lateinit var binding: DialogSetStatusBinding

    private var currentUser: User? = null
    private var currentStatus: Status? = null
    private lateinit var backupStatus: Status

    val predefinedStatusesList = ArrayList<PredefinedStatus>()

    private val disposables: MutableList<Disposable> = ArrayList()

    private lateinit var adapter: PredefinedStatusListAdapter
    private var clearAt: Long? = null
    private lateinit var popup: EmojiPopup
    private var isBackupStatusAvailable = false

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    var currentUserProvider: CurrentUserProviderNew? = null
        @Inject set

    lateinit var credentials: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        arguments?.let {
            currentUser = currentUserProvider?.currentUser?.blockingGet()
            currentStatus = it.getParcelable(ARG_CURRENT_STATUS_PARAM)

            credentials = ApiUtils.getCredentials(currentUser?.username, currentUser?.token)!!
            if (isRestoreStatusAvailable(currentUser!!)) {
                checkBackupStatus()
            }
            fetchPredefinedStatuses()
        }
    }

    private fun fetchPredefinedStatuses() {
        ncApi.getPredefinedStatuses(credentials, ApiUtils.getUrlForPredefinedStatuses(currentUser?.baseUrl!!))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<ResponseBody> {
                override fun onSubscribe(d: Disposable) {
                    disposables.add(d)
                }

                @SuppressLint("NotifyDataSetChanged")
                override fun onNext(responseBody: ResponseBody) {
                    val predefinedStatusOverall: PredefinedStatusOverall = LoganSquare.parse(
                        responseBody.string(),
                        PredefinedStatusOverall::class.java
                    )
                    predefinedStatusOverall.ocs?.data?.let { predefinedStatusesList.addAll(it) }

                    if (currentStatus?.messageIsPredefined == true && currentStatus?.messageId?.isNotEmpty() == true) {
                        val messageId = currentStatus!!.messageId
                        selectedPredefinedStatus = predefinedStatusesList.firstOrNull { ps -> messageId == ps.id }
                    }

                    adapter.notifyDataSetChanged()
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error while fetching predefined statuses", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun checkBackupStatus() {
        ncApi.backupStatus(credentials, ApiUtils.getUrlForBackupStatus(currentUser?.baseUrl!!, currentUser?.userId!!))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<StatusOverall> {

                override fun onSubscribe(d: Disposable) {
                    disposables.add(d)
                }

                @SuppressLint("NotifyDataSetChanged")
                override fun onNext(statusOverall: StatusOverall) {
                    if (statusOverall.ocs?.meta?.statusCode == HTTP_STATUS_CODE_OK) {
                        statusOverall.ocs?.data?.let { status ->
                            backupStatus = status
                            if (backupStatus.message != null) {
                                isBackupStatusAvailable = true
                                val backupPredefinedStatus = PredefinedStatus(
                                    backupStatus.userId!!,
                                    backupStatus.icon,
                                    backupStatus.message!!,
                                    ClearAt(type = "period", time = backupStatus.clearAt.toString())
                                )
                                binding.automaticStatus.visibility = View.VISIBLE
                                adapter.isBackupStatusAvailable = true
                                predefinedStatusesList.add(0, backupPredefinedStatus)
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    if (e is HttpException && e.code() == HTTP_STATUS_CODE_NOT_FOUND) {
                        Log.d(TAG, "User does not have a backup status set")
                    } else {
                        Log.e(TAG, "Error while getting user backup status", e)
                    }
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogSetStatusBinding.inflate(layoutInflater)

        val dialogBuilder = MaterialAlertDialogBuilder(binding.root.context).setView(binding.root)
        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(binding.root.context, dialogBuilder)

        return dialogBuilder.create()
    }

    @SuppressLint("DefaultLocale")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCurrentStatus()

        adapter = PredefinedStatusListAdapter(this, requireContext(), isBackupStatusAvailable)
        adapter.list = predefinedStatusesList

        binding.predefinedStatusList.adapter = adapter
        binding.predefinedStatusList.layoutManager = LinearLayoutManager(context)

        setupGeneralStatusOptions()

        if (currentStatus?.icon == null) {
            binding.emoji.setText(getString(R.string.default_emoji))
        }

        binding.clearStatus.setOnClickListener { clearStatus() }
        binding.setStatus.setOnClickListener { setStatusMessage() }
        binding.emoji.setOnClickListener { openEmojiPopup() }
        popup = EmojiPopup(
            rootView = view,
            editText = binding.emoji,
            onEmojiClickListener = {
                popup.dismiss()
                binding.emoji.clearFocus()
                val imm: InputMethodManager = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as
                    InputMethodManager
                imm.hideSoftInputFromWindow(binding.emoji.windowToken, 0)
            }
        )
        binding.emoji.installDisableKeyboardInput(popup)
        binding.emoji.installForceSingleEmoji()

        binding.clearStatusAfterSpinner.apply {
            this.adapter = createClearTimesArrayAdapter()
            onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    view?.let {
                        setClearStatusAfterValue(position)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // nothing to do
                }
            }
        }

        viewThemeUtils.platform.themeDialog(binding.root)

        viewThemeUtils.material.colorMaterialButtonPrimaryBorderless(binding.clearStatus)
        viewThemeUtils.material.colorMaterialButtonPrimaryTonal(binding.setStatus)

        viewThemeUtils.material.colorTextInputLayout(binding.customStatusInputContainer)
    }

    private fun setupCurrentStatus() {
        currentStatus?.let {
            binding.emoji.setText(it.icon)
            binding.customStatusInput.text?.clear()
            binding.customStatusInput.setText(it.message?.trim())
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
                clearAt = it.clearAt
            } else {
                clearAt = null
            }
        }
    }

    override fun revertStatus() {
        if (isRestoreStatusAvailable(currentUser!!)) {
            ncApi.revertStatus(
                credentials,
                ApiUtils.getUrlForRevertStatus(currentUser?.baseUrl!!, currentStatus?.messageId)
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<GenericOverall> {

                    override fun onSubscribe(d: Disposable) {
                        disposables.add(d)
                    }

                    @SuppressLint("NotifyDataSetChanged")
                    override fun onNext(genericOverall: GenericOverall) {
                        Log.d(TAG, "$genericOverall")
                        if (genericOverall.ocs?.meta?.statusCode == HTTP_STATUS_CODE_OK) {
                            binding.automaticStatus.visibility = View.GONE
                            adapter.isBackupStatusAvailable = false
                            predefinedStatusesList.removeAt(0)
                            adapter.notifyDataSetChanged()
                            currentStatus = backupStatus
                            setupCurrentStatus()
                            dismiss()
                        }
                    }
                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Failed to revert user status", e)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    private fun setupGeneralStatusOptions() {
        binding.onlineStatus.setOnClickListener { setStatus(StatusType.ONLINE) }
        binding.dndStatus.setOnClickListener { setStatus(StatusType.DND) }
        binding.awayStatus.setOnClickListener { setStatus(StatusType.AWAY) }
        binding.invisibleStatus.setOnClickListener { setStatus(StatusType.INVISIBLE) }

        viewThemeUtils.talk.themeStatusCardView(binding.onlineStatus)
        viewThemeUtils.talk.themeStatusCardView(binding.dndStatus)
        viewThemeUtils.talk.themeStatusCardView(binding.awayStatus)
        viewThemeUtils.talk.themeStatusCardView(binding.invisibleStatus)
    }

    private fun createClearTimesArrayAdapter(): ArrayAdapter<String> {
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        adapter.add(getString(R.string.dontClear))
        adapter.add(getString(R.string.thirtyMinutes))
        adapter.add(getString(R.string.oneHour))
        adapter.add(getString(R.string.fourHours))
        adapter.add(getString(R.string.today))
        adapter.add(getString(R.string.thisWeek))
        return adapter
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

    private fun clearAtToUnixTime(clearAt: ClearAt?): Long {
        var returnValue = -1L

        if (clearAt != null) {
            if (clearAt.type == "period") {
                returnValue = System.currentTimeMillis() / ONE_SECOND_IN_MILLIS + clearAt.time.toLong()
            } else if (clearAt.type == "end-of") {
                returnValue = clearAtToUnixTimeTypeEndOf(clearAt)
            }
        }

        return returnValue
    }

    private fun clearAtToUnixTimeTypeEndOf(clearAt: ClearAt): Long {
        var returnValue = -1L
        if (clearAt.time == "day") {
            val date = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, LAST_HOUR_OF_DAY)
                set(Calendar.MINUTE, LAST_MINUTE_OF_HOUR)
                set(Calendar.SECOND, LAST_SECOND_OF_MINUTE)
            }
            returnValue = date.timeInMillis / ONE_SECOND_IN_MILLIS
        }
        return returnValue
    }

    private fun openEmojiPopup() {
        popup.show()
    }

    private fun clearStatus() {
        val credentials = ApiUtils.getCredentials(currentUser?.username, currentUser?.token)
        ncApi.statusDeleteMessage(credentials, ApiUtils.getUrlForStatusMessage(currentUser?.baseUrl!!))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposables.add(d)
                }

                override fun onNext(statusOverall: GenericOverall) {
                    // unused atm
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to clear status", e)
                }

                override fun onComplete() {
                    dismiss()
                }
            })
    }

    private fun setStatus(statusType: StatusType) {
        visualizeStatus(statusType)

        ncApi.setStatusType(credentials, ApiUtils.getUrlForSetStatusType(currentUser?.baseUrl!!), statusType.string)
            .subscribeOn(
                Schedulers
                    .io()
            )
            .observeOn(AndroidSchedulers.mainThread()).subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposables.add(d)
                }

                override fun onNext(statusOverall: GenericOverall) {
                    Log.d(TAG, "statusType successfully set")
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to set statusType", e)
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
        val views: Triple<MaterialCardView, TextView, ImageView> = when (statusType) {
            StatusType.ONLINE -> Triple(binding.onlineStatus, binding.onlineHeadline, binding.onlineIcon)
            StatusType.AWAY -> Triple(binding.awayStatus, binding.awayHeadline, binding.awayIcon)
            StatusType.DND -> Triple(binding.dndStatus, binding.dndHeadline, binding.dndIcon)
            StatusType.INVISIBLE -> Triple(binding.invisibleStatus, binding.invisibleHeadline, binding.invisibleIcon)
            else -> {
                Log.d(TAG, "unknown status")
                return
            }
        }
        views.first.isChecked = true
        viewThemeUtils.platform.colorTextView(views.second, ColorRole.ON_SECONDARY_CONTAINER)
    }

    private fun clearTopStatus() {
        context?.let {
            binding.onlineHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text, null))
            binding.awayHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text, null))
            binding.dndHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text, null))
            binding.invisibleHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text, null))

            binding.onlineIcon.imageTintList = null
            binding.awayIcon.imageTintList = null
            binding.dndIcon.imageTintList = null
            binding.invisibleIcon.imageTintList = null

            binding.onlineStatus.isChecked = false
            binding.awayStatus.isChecked = false
            binding.dndStatus.isChecked = false
            binding.invisibleStatus.isChecked = false
        }
    }

    private fun setStatusMessage() {
        val inputText = binding.customStatusInput.text.toString().ifEmpty { "" }
        // The endpoint '/message/custom' expects a valid emoji as string or null
        val statusIcon = binding.emoji.text.toString().ifEmpty { null }

        if (selectedPredefinedStatus == null ||
            selectedPredefinedStatus!!.message != inputText ||
            selectedPredefinedStatus!!.icon != binding.emoji.text.toString()
        ) {
            ncApi.setCustomStatusMessage(
                credentials,
                ApiUtils.getUrlForSetCustomStatus(currentUser?.baseUrl!!),
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
                        Log.d(TAG, "CustomStatusMessage successfully set")
                        dismiss()
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "failed to set CustomStatusMessage", e)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        } else {
            ncApi.setPredefinedStatusMessage(
                credentials,
                ApiUtils.getUrlForSetPredefinedStatus(currentUser?.baseUrl!!),
                selectedPredefinedStatus!!.id,
                clearAt
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())?.subscribe(object : Observer<GenericOverall> {
                    override fun onSubscribe(d: Disposable) {
                        disposables.add(d)
                    }

                    override fun onNext(t: GenericOverall) {
                        Log.d(TAG, "PredefinedStatusMessage successfully set")
                        dismiss()
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "failed to set PredefinedStatusMessage", e)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        binding.root

    override fun onClick(predefinedStatus: PredefinedStatus) {
        selectedPredefinedStatus = predefinedStatus

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
            setClearAt(predefinedStatus.clearAt!!)
        }
        setClearStatusAfterValue(binding.clearStatusAfterSpinner.selectedItemPosition)
    }

    private fun setClearAt(clearAt: ClearAt) {
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

    private fun dispose() {
        for (i in disposables.indices) {
            if (!disposables[i].isDisposed) {
                disposables[i].dispose()
            }
        }
    }

    override fun onDestroy() {
        dispose()
        super.onDestroy()
    }

    /**
     * Fragment creator
     */
    companion object {
        private val TAG = SetStatusDialogFragment::class.simpleName
        private const val HTTP_STATUS_CODE_OK = 200
        private const val HTTP_STATUS_CODE_NOT_FOUND = 404

        @JvmStatic
        fun newInstance(status: Status): SetStatusDialogFragment {
            val args = Bundle()
            args.putParcelable(ARG_CURRENT_STATUS_PARAM, status)

            val dialogFragment = SetStatusDialogFragment()
            dialogFragment.arguments = args
            return dialogFragment
        }
    }
}
