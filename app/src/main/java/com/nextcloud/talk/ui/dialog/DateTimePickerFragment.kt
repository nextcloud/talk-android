/*
 * Nextcloud Talk application
 *
 * @author Julius Linus
 * Copyright (C) 2023 Julius Linus <julius.linu@nextcloud.com>
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
package com.nextcloud.talk.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.databinding.DialogDateTimePickerBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

@Suppress("TooManyFunctions")
@AutoInjector(NextcloudTalkApplication::class)
class DateTimePickerFragment(
    token: String,
    id: String,
    chatViewModel: ChatViewModel
) : DialogFragment() {
    lateinit var binding: DialogDateTimePickerBinding
    private var dialogView: View? = null
    private var viewModel = chatViewModel
    private var currentTimeStamp: Long? = null
    private var roomToken = token
    private var messageId = id
    private var laterTodayTimeStamp = 0L
    private var tomorrowTimeStamp = 0L
    private var weekendTimeStamp = 0L
    private var nextWeekTimeStamp = 0L

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogDateTimePickerBinding.inflate(LayoutInflater.from(context))
        dialogView = binding.root
        return MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        setUpDefaults()
        setUpColors()
        setListeners()
        getReminder()
        viewModel.getReminderExistState.observe(this) { state ->
            when (state) {
                is ChatViewModel.GetReminderExistState -> {
                    val timeStamp = state.reminder.timestamp?.toLong()?.times(ONE_SEC)
                    showDelete(true)
                    setTimeStamp(getTimeFromTimeStamp(timeStamp!!))
                }

                else -> {
                    showDelete(false)
                    binding.dateTimePickerTimestamp.text = ""
                }
            }
        }

        return inflater.inflate(R.layout.dialog_date_time_picker, container, false)
    }

    private fun setUpDefaults() {
        val currTime = getTimeFromCalendar()
        val currentWeekInYear = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)

        laterTodayTimeStamp = getTimeFromCalendar(hour = HOUR_SIX_PM, minute = 0)
        binding.dateTimePickerLaterTodayTextview.text = getTimeFromTimeStamp(laterTodayTimeStamp)

        if (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
            tomorrowTimeStamp = getTimeFromCalendar(
                hour = HOUR_EIGHT_AM,
                minute = 0,
                daysToAdd = 1,
                weekInYear =
                currentWeekInYear + 1
            )

            binding.dateTimePickerWeekend.visibility = View.GONE // because today is the weekend
        } else {
            tomorrowTimeStamp = getTimeFromCalendar(hour = HOUR_EIGHT_AM, minute = 0, daysToAdd = 1)
            weekendTimeStamp = getTimeFromCalendar(hour = HOUR_EIGHT_AM, day = Calendar.SATURDAY, minute = 0)
        }
        binding.dateTimePickerTomorrowTextview.text = getTimeFromTimeStamp(tomorrowTimeStamp)
        binding.dateTimePickerWeekendTextview.text = getTimeFromTimeStamp(weekendTimeStamp)

        nextWeekTimeStamp = getTimeFromCalendar(
            hour = HOUR_EIGHT_AM,
            day = Calendar.MONDAY,
            minute = 0,
            weekInYear =
            currentWeekInYear + 1
        ) // this should only pick mondays from next week only
        binding.dateTimePickerNextWeekTextview.text = getTimeFromTimeStamp(nextWeekTimeStamp)

        // This is to hide the later today option, if it's past 6pm
        if (currTime > laterTodayTimeStamp) {
            binding.dateTimePickerLaterToday.visibility = View.GONE
        }

        // This is to hide the tomorrow option, if that's also the weekend
        if (binding.dateTimePickerTomorrowTextview.text == binding.dateTimePickerWeekendTextview.text) {
            binding.dateTimePickerTomorrow.visibility = View.GONE
        }
    }

    private fun getReminder() {
        viewModel.getReminder(userManager.currentUser.blockingGet(), roomToken, messageId)
    }

    private fun showDelete(value: Boolean) {
        if (value) {
            binding.buttonDelete.visibility = View.VISIBLE
        } else {
            binding.buttonDelete.visibility = View.GONE
        }
    }

    private fun setUpColors() {
        binding.root.let {
            viewThemeUtils.platform.colorViewBackground(it)
        }

        binding.dateTimePickerCustomIcon.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }

        binding.dateTimePickerTimestamp.let {
            viewThemeUtils.material.themeSearchBarText(it)
        }

        binding.run {
            listOf(
                binding.buttonClose,
                binding.buttonSet
            )
        }.forEach(viewThemeUtils.material::colorMaterialButtonPrimaryBorderless)
    }

    private fun setListeners() {
        binding.dateTimePickerLaterToday.setOnClickListener {
            currentTimeStamp = laterTodayTimeStamp / ONE_SEC
            setTimeStamp(getTimeFromTimeStamp(laterTodayTimeStamp))
        }
        binding.dateTimePickerTomorrow.setOnClickListener {
            currentTimeStamp = tomorrowTimeStamp / ONE_SEC
            setTimeStamp(getTimeFromTimeStamp(tomorrowTimeStamp))
        }
        binding.dateTimePickerWeekend.setOnClickListener {
            currentTimeStamp = weekendTimeStamp / ONE_SEC
            setTimeStamp(getTimeFromTimeStamp(weekendTimeStamp))
        }
        binding.dateTimePickerNextWeek.setOnClickListener {
            currentTimeStamp = nextWeekTimeStamp / ONE_SEC
            setTimeStamp(getTimeFromTimeStamp(nextWeekTimeStamp))
        }
        binding.dateTimePickerCustom.setOnClickListener {
            val constraintsBuilder = CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build()
            val time = System.currentTimeMillis()
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.nc_remind)
                .setSelection(time + TimeZone.getDefault().getOffset(time))
                .setCalendarConstraints(constraintsBuilder).build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                val localTimeInMillis = selection - TimeZone.getDefault().getOffset(selection)
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = localTimeInMillis

                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_WEEK)
                val weekInYear = calendar.get(Calendar.WEEK_OF_YEAR)

                setUpTimePicker(year, month, day, weekInYear)
            }
            datePicker.show(this.parentFragmentManager, TAG)
        }

        binding.buttonClose.setOnClickListener { dismiss() }
        binding.buttonSet.setOnClickListener {
            currentTimeStamp?.let { time ->
                viewModel.setReminder(userManager.currentUser.blockingGet(), roomToken, messageId, time.toInt())
            }
            dismiss()
        }
        binding.buttonDelete.setOnClickListener {
            viewModel.deleteReminder(userManager.currentUser.blockingGet(), roomToken, messageId)
        }
    }

    private fun setUpTimePicker(year: Int, month: Int, day: Int, weekInYear: Int) {
        val timePicker = MaterialTimePicker
            .Builder()
            .setTitleText(R.string.nc_remind)
            .build()

        timePicker.addOnPositiveButtonClickListener {
            val timestamp = getTimeFromCalendar(
                year,
                month,
                day,
                timePicker.hour,
                timePicker.minute,
                weekInYear = weekInYear
            )
            setTimeStamp(getTimeFromTimeStamp(timestamp))
            currentTimeStamp = timestamp / ONE_SEC
        }

        timePicker.show(this.parentFragmentManager, TAG)
    }

    @Suppress("LongParameterList")
    private fun getTimeFromCalendar(
        year: Int = Calendar.getInstance().get(Calendar.YEAR),
        month: Int = Calendar.getInstance().get(Calendar.MONTH),
        day: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
        hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        minute: Int = Calendar.getInstance().get(Calendar.MINUTE),
        daysToAdd: Int = 0,
        weekInYear: Int = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
    ): Long {
        val calendar: Calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_WEEK, day)
            add(Calendar.DAY_OF_WEEK, daysToAdd)
            set(Calendar.WEEK_OF_YEAR, weekInYear)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun setTimeStamp(date: String) {
        binding.dateTimePickerTimestamp.text = date
    }

    private fun getTimeFromTimeStamp(time: Long): String {
        return DateUtils.formatDateTime(
            requireContext(),
            time,
            DateUtils.FORMAT_SHOW_DATE
        ) + ", " + DateUtils.formatDateTime(
            requireContext(),
            time,
            DateUtils.FORMAT_SHOW_TIME
        )
    }

    companion object {
        val TAG = DateTimePickerFragment::class.simpleName
        private const val ONE_SEC = 1000
        private const val HOUR_EIGHT_AM = 8
        private const val HOUR_SIX_PM = 18

        @JvmStatic
        fun newInstance(
            token: String,
            id: String,
            chatViewModel: ChatViewModel
        ) = DateTimePickerFragment(
            token,
            id,
            chatViewModel
        )
    }
}
