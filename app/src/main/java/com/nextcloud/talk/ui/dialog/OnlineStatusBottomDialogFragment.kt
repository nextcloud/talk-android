/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Sowjanya Kota <sowjanya.kota@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogSetOnlineStatusBinding
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.status.Status
import com.nextcloud.talk.models.json.status.StatusType
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.Locale
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class OnlineStatusBottomDialogFragment : BottomSheetDialogFragment() {
    private lateinit var binding: DialogSetOnlineStatusBinding
    private var currentUser: User? = null
    private var currentStatus: Status? = null
    private val disposables: MutableList<Disposable> = ArrayList()

    @Inject lateinit var ncApi: NcApi

    @Inject lateinit var viewThemeUtils: ViewThemeUtils

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
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSetOnlineStatusBinding.inflate(inflater, container, false)
        viewThemeUtils.platform.themeDialog(binding.root)
        viewThemeUtils.material.themeDragHandleView(binding.dragHandle)

        dialog?.window?.let { window ->
            window.navigationBarColor = ContextCompat.getColor(requireContext(), R.color.bg_default)
            val inLightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = inLightMode
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGeneralStatusOptions()
        currentStatus?.let { visualizeStatus(it.status) }
    }

    private fun setupGeneralStatusOptions() {
        binding.onlineStatus.setOnClickListener { setStatus(StatusType.ONLINE) }
        binding.dndStatus.setOnClickListener { setStatus(StatusType.DND) }
        binding.busyStatus.setOnClickListener { setStatus(StatusType.BUSY) }
        binding.awayStatus.setOnClickListener { setStatus(StatusType.AWAY) }
        binding.invisibleStatus.setOnClickListener { setStatus(StatusType.INVISIBLE) }

        viewThemeUtils.talk.themeStatusCardView(binding.onlineStatus)
        viewThemeUtils.talk.themeStatusCardView(binding.dndStatus)
        viewThemeUtils.talk.themeStatusCardView(binding.busyStatus)
        viewThemeUtils.talk.themeStatusCardView(binding.awayStatus)
        viewThemeUtils.talk.themeStatusCardView(binding.invisibleStatus)
    }

    private fun setStatus(statusType: StatusType) {
        visualizeStatus(statusType)

        ncApi.setStatusType(credentials, ApiUtils.getUrlForSetStatusType(currentUser?.baseUrl!!), statusType.string)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposables.add(d)
                }
                override fun onNext(t: GenericOverall) {
                    dismiss()
                }
                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to set statusType", e)
                }
                override fun onComplete() { }
            })
    }

    private fun visualizeStatus(statusType: String) {
        StatusType.entries.firstOrNull { it.name == statusType.uppercase(Locale.ROOT) }?.let { visualizeStatus(it) }
    }

    private fun visualizeStatus(statusType: StatusType) {
        clearTopStatus()
        val views: Triple<MaterialCardView, TextView, ImageView> = when (statusType) {
            StatusType.ONLINE -> Triple(binding.onlineStatus, binding.onlineHeadline, binding.onlineIcon)
            StatusType.BUSY -> Triple(binding.busyStatus, binding.busyHeadline, binding.busyIcon)
            StatusType.AWAY -> Triple(binding.awayStatus, binding.awayHeadline, binding.awayIcon)
            StatusType.DND -> Triple(binding.dndStatus, binding.dndHeadline, binding.dndIcon)
            StatusType.INVISIBLE -> Triple(binding.invisibleStatus, binding.invisibleHeadline, binding.invisibleIcon)
            else -> return
        }
        views.first.isChecked = true
        viewThemeUtils.platform.colorTextView(views.second, ColorRole.ON_SECONDARY_CONTAINER)
    }

    private fun clearTopStatus() {
        context?.let {
            binding.onlineHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text, null))
            binding.awayHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text, null))
            binding.dndHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text, null))
            binding.busyHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text, null))
            binding.invisibleHeadline.setTextColor(resources.getColor(R.color.high_emphasis_text, null))

            binding.onlineIcon.imageTintList = null
            binding.awayIcon.imageTintList = null
            binding.dndIcon.imageTintList = null
            binding.busyIcon.imageTintList = null
            binding.invisibleIcon.imageTintList = null

            binding.onlineStatus.isChecked = false
            binding.awayStatus.isChecked = false
            binding.dndStatus.isChecked = false
            binding.busyStatus.isChecked = false
            binding.invisibleStatus.isChecked = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposables.forEach { if (!it.isDisposed) it.dispose() }
        disposables.clear()
    }

    companion object {
        private const val ARG_CURRENT_STATUS_PARAM = "currentStatus"
        private val TAG = OnlineStatusBottomDialogFragment::class.simpleName

        @JvmStatic
        fun newInstance(status: Status): OnlineStatusBottomDialogFragment {
            val args = Bundle()
            args.putParcelable(ARG_CURRENT_STATUS_PARAM, status)

            val fragment = OnlineStatusBottomDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
