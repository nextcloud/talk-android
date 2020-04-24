/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.nextcloud.talk.newarch.features.settingsflow.settings

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import coil.Coil
import coil.api.load
import coil.transform.CircleCropTransformation
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.archlifecycle.ControllerLifecycleOwner
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.controllers.bottomsheet.items.BasicListItemWithImage
import com.nextcloud.talk.controllers.bottomsheet.items.listItemsWithImage
import com.nextcloud.talk.newarch.features.account.serverentry.ServerEntryView
import com.nextcloud.talk.newarch.features.settingsflow.looknfeel.SettingsLookNFeelView
import com.nextcloud.talk.newarch.features.settingsflow.privacy.SettingsPrivacyView
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.local.models.toUser
import com.nextcloud.talk.newarch.mvvm.BaseView
import com.nextcloud.talk.newarch.mvvm.ext.initRecyclerView
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.utils.ApiUtils
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.settings_view.view.*
import org.koin.android.ext.android.inject

class SettingsView(private val bundle: Bundle? = null) : BaseView() {
    override val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)
    override val lifecycleOwner = ControllerLifecycleOwner(this)

    private lateinit var viewModel: SettingsViewModel
    val factory: SettingsViewModelFactory by inject()

    private lateinit var settingsUsersAdapter: Adapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        viewModel = viewModelProvider(factory).get(SettingsViewModel::class.java)
        val view = super.onCreateView(inflater, container)
        setHasOptionsMenu(true)

        setupAboutSection(view)
        view.settings_privacy_options.setOnClickListener {
            router.pushController(RouterTransaction.with(SettingsPrivacyView())
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler())
            )
        }

        view.settings_look_n_feel.setOnClickListener {
            router.pushController(RouterTransaction.with(SettingsLookNFeelView())
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler())
            )
        }

        view.activeUserMoreOptions.setOnClickListener {
            viewModel.activeUser.value?.let {
                onMoreOptionsClick(it.toUser())
            }
        }

        showFallbackAvatar(view.avatar_image)

        viewModel.activeUser.observe(this@SettingsView) { user ->
            view.display_name_text.text = user?.displayName ?: ""
            view.base_url_text.text = user?.baseUrl?.replace("http://", "")?.replace("https://", "")
                    ?: ""
            loadAvatar(user?.toUser(), view.avatar_image)
        }

        settingsUsersAdapter = Adapter.builder(this)
                .addSource(SettingsViewSource(viewModel.users))
                .addSource(SettingsViewFooterSource(activity as Context))
                .addPresenter(Presenter.forLoadingIndicator(activity as Context, R.layout.loading_state))
                .addPresenter(SettingsPresenter(activity as Context, ::onElementClick, ::onMoreOptionsClick))
                .into(view.settingsRecyclerView)

        view.settingsRecyclerView.initRecyclerView(LinearLayoutManager(activity), settingsUsersAdapter, false)

        return view
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            router.popController(this)
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private fun showFallbackAvatar(target: ImageView) {
        val fallbackImage = BitmapDrawable(Images().getImageWithBackground(activity as Context, R.drawable.ic_user))
        Coil.load(context, fallbackImage) {
            target(target)
            transformations(CircleCropTransformation())
        }
    }

    private fun loadAvatar(user: User?, target: ImageView) {
        user?.let {
            val imageLoader = viewModel.networkComponents.getImageLoader(it)
            imageLoader.load(activity as Context, ApiUtils.getUrlForAvatarWithName(it.baseUrl, it.userId, R.dimen.avatar_size_big)) {
                target(target)
                addHeader("Authorization", user.getCredentials())
                transformations(CircleCropTransformation())
                fallback(BitmapDrawable(Images().getImageWithBackground(activity as Context, R.drawable.ic_user)))
                error(BitmapDrawable(Images().getImageWithBackground(activity as Context, R.drawable.ic_user)))
            }
        } ?: run {
            showFallbackAvatar(target)
        }
    }

    private fun setupAboutSection(view: View) {
        val privacyUrl = resources?.getString(R.string.nc_privacy_url)
        privacyUrl?.let { privacyUrlString ->
            view.settings_privacy.setOnClickListener {
                val browserIntent =
                        Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrlString))
                startActivity(browserIntent)
            }
        }

        view.settings_privacy.isVisible = !privacyUrl.isNullOrEmpty()

        val sourceCodeUrl = resources?.getString(R.string.nc_source_code_url)
        sourceCodeUrl?.let { sourceCodeUrlString ->
            view.settings_source_code.setOnClickListener {
                val browserIntent =
                        Intent(Intent.ACTION_VIEW, Uri.parse(sourceCodeUrlString))
                startActivity(browserIntent)
            }
        }
        view.settings_source_code.isVisible = !sourceCodeUrl.isNullOrEmpty()

        val licenceUrl = resources?.getString(R.string.nc_gpl3_url)
        licenceUrl?.let { licenceUrlString ->
            view.settings_licence.setOnClickListener {
                val browserIntent =
                        Intent(Intent.ACTION_VIEW, Uri.parse(licenceUrlString))
                startActivity(browserIntent)
            }
        }
        view.settings_licence.isVisible = !licenceUrl.isNullOrEmpty()

        view.settings_version.setSummary("v" + BuildConfig.VERSION_NAME)
    }

    private fun onMoreOptionsClick(user: User) {
        activity?.let { activity ->
            MaterialDialog(activity, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                cornerRadius(res = R.dimen.corner_radius)
                title(text = user.displayName)
                listItemsWithImage(getSettingsMenuItemForUser(user)) { _, _, item ->
                    when (item.iconRes) {
                        R.drawable.ic_baseline_clear_24 -> {
                            val weHaveActiveUser = viewModel.removeUser(user)
                            if (!weHaveActiveUser) {
                                router.setRoot(RouterTransaction.with(ServerEntryView())
                                        .popChangeHandler(HorizontalChangeHandler())
                                        .popChangeHandler(HorizontalChangeHandler()))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getSettingsMenuItemForUser(user: User): MutableList<BasicListItemWithImage> {
        val items = mutableListOf<BasicListItemWithImage>()

        resources?.let {
            /*items.add(
                    BasicListItemWithImage(
                            R.drawable.ic_baseline_clear_24,
                            it.getString(R.string.nc_settings_reauthorize)
                    )
            )
            items.add(
                    BasicListItemWithImage(
                            R.drawable.ic_baseline_clear_24,
                            it.getString(R.string.nc_client_cert_setup)
                    )
            )*/
            items.add(
                    BasicListItemWithImage(
                            R.drawable.ic_baseline_clear_24,
                            it.getString(R.string.nc_settings_remove_account)
                    )
            )
        }

        return items
    }

    private fun onElementClick(page: Page, holder: Presenter.Holder, element: Element<Any>) {
        if (element.type == SettingsElementType.USER.ordinal) {
            if (viewModel.setUserAsActive(element.data as User)) {
                router.popController(this)
            }
        } else {
            router.pushController(RouterTransaction.with(ServerEntryView())
                    .pushChangeHandler(VerticalChangeHandler())
                    .popChangeHandler(VerticalChangeHandler())
            )
        }
    }

    override fun getLayoutId(): Int {
        return R.layout.settings_view
    }

    override fun getTitle(): String? {
        return resources?.getString(R.string.nc_settings)
    }
}