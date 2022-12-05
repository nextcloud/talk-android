/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogMoreCallActionsBinding
import com.nextcloud.talk.models.domain.StartCallRecordingModel
import com.nextcloud.talk.repositories.callrecording.CallRecordingRepository
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MoreCallActionsDialog(val callActivity: CallActivity) : BottomSheetDialog(callActivity) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var callRecordingRepository: CallRecordingRepository

    private lateinit var binding: DialogMoreCallActionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)

        binding = DialogMoreCallActionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        viewThemeUtils.platform.themeDialogDark(binding.root)
        initClickListeners()
    }

    private fun initClickListeners() {
        binding.recordCall.setOnClickListener {
            callRecordingRepository.startRecording(callActivity.roomToken)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(CallStartRecordingObserver())
            // dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    inner class CallStartRecordingObserver : Observer<StartCallRecordingModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(startCallRecordingModel: StartCallRecordingModel) {
            if (startCallRecordingModel.success) {
                binding.recordCallText.text = "started"
                callActivity.showCallRecordingIndicator()
            }
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "failure in CallStartRecordingObserver", e)
        }

        override fun onComplete() {
            // dismiss()
        }
    }

    companion object {
        private const val TAG = "MoreCallActionsDialog"
    }
}
