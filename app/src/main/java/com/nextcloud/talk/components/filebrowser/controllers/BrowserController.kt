/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
package com.nextcloud.talk.components.filebrowser.controllers

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.components.filebrowser.adapters.items.BrowserFileItem
import com.nextcloud.talk.components.filebrowser.interfaces.ListingInterface
import com.nextcloud.talk.components.filebrowser.models.BrowserFile
import com.nextcloud.talk.components.filebrowser.models.DavResponse
import com.nextcloud.talk.components.filebrowser.operations.DavListing
import com.nextcloud.talk.components.filebrowser.operations.ListingAbstractClass
import com.nextcloud.talk.controllers.base.NewBaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.databinding.ControllerBrowserBinding
import com.nextcloud.talk.interfaces.SelectionInterface
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.ui.dialog.SortingOrderDialogFragment
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.LegacyFileSortOrder
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BROWSER_TYPE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.database.user.UserUtils
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import kotlinx.android.parcel.Parcelize
import net.orange_box.storebox.listeners.OnPreferenceValueChangedListener
import okhttp3.OkHttpClient
import org.parceler.Parcels
import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.TreeSet
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
abstract class BrowserController(args: Bundle) :
    NewBaseController(
        R.layout.controller_browser,
        args
    ),
    ListingInterface,
    FlexibleAdapter.OnItemClickListener,
    SwipeRefreshLayout.OnRefreshListener,
    SelectionInterface {

    private val binding: ControllerBrowserBinding by viewBinding(ControllerBrowserBinding::bind)

    @JvmField
    protected val selectedPaths: MutableSet<String>

    @JvmField
    @Inject
    var userUtils: UserUtils? = null

    @JvmField
    @Inject
    var okHttpClient: OkHttpClient? = null

    @JvmField
    protected var activeUser: UserEntity

    private var filesSelectionDoneMenuItem: MenuItem? = null
    private var layoutManager: RecyclerView.LayoutManager? = null
    private var adapter: FlexibleAdapter<BrowserFileItem>? = null
    private var recyclerViewItems: List<BrowserFileItem> = ArrayList()
    private var listingAbstractClass: ListingAbstractClass? = null
    private val browserType: BrowserType
    private var currentPath: String

    private var sortingChangeListener: OnPreferenceValueChangedListener<String>? = null

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        if (adapter == null) {
            adapter = FlexibleAdapter(recyclerViewItems, context, false)
        }

        appPreferences!!.registerSortingChangeListener(
            SortingChangeListener(this).also {
                sortingChangeListener = it
            }
        )

        changeEnabledStatusForBarItems(true)
        prepareViews()
    }

    abstract fun onFileSelectionDone()
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_share_files, menu)
        filesSelectionDoneMenuItem = menu.findItem(R.id.files_selection_done)
        filesSelectionDoneMenuItem?.isVisible = selectedPaths.size > 0
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.files_selection_done) {
            onFileSelectionDone()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)

        binding.pathNavigation.menu.findItem(R.id.action_back)?.setOnMenuItemClickListener { goBack() }
        binding.sortButton.setOnClickListener { changeSorting() }

        binding.sortButton.setText(
            DisplayUtils.getSortOrderStringId(LegacyFileSortOrder.getFileSortOrder(appPreferences?.sorting))
        )

        refreshCurrentPath()
    }

    override fun onRefresh() {
        refreshCurrentPath()
    }

    fun changeSorting() {
        val newFragment: DialogFragment = SortingOrderDialogFragment
            .newInstance(LegacyFileSortOrder.getFileSortOrder(appPreferences?.sorting))
        newFragment.show(
            (activity as MainActivity?)!!.supportFragmentManager,
            SortingOrderDialogFragment.SORTING_ORDER_FRAGMENT
        )
    }

    public override fun onDestroy() {
        super.onDestroy()
        listingAbstractClass!!.tearDown()
    }

    override val title: String
        get() =
            currentPath

    fun goBack(): Boolean {
        fetchPath(File(currentPath).parent)
        return true
    }

    fun refreshCurrentPath(): Boolean {
        fetchPath(currentPath)
        return true
    }

    @SuppressLint("RestrictedApi")
    private fun changeEnabledStatusForBarItems(shouldBeEnabled: Boolean) {
        binding.pathNavigation.menu.findItem(R.id.action_back)?.isEnabled = shouldBeEnabled && currentPath != "/"
    }

    private fun fetchPath(path: String) {
        listingAbstractClass!!.cancelAllJobs()
        changeEnabledStatusForBarItems(false)
        listingAbstractClass!!.getFiles(
            path,
            activeUser,
            if (BrowserType.DAV_BROWSER == browserType) okHttpClient else null
        )
    }

    override fun listingResult(davResponse: DavResponse) {
        recyclerViewItems = ArrayList()
        if (davResponse.getData() != null) {
            val objectList = davResponse.getData() as List<BrowserFile>
            currentPath = objectList[0].path!!
            if (activity != null) {
                activity!!.runOnUiThread { setTitle() }
            }
            for (i in 1 until objectList.size) {
                (recyclerViewItems as ArrayList<BrowserFileItem>).add(BrowserFileItem(objectList[i], activeUser, this))
            }
        }

        LegacyFileSortOrder.getFileSortOrder(appPreferences?.sorting).sortCloudFiles(recyclerViewItems)

        if (activity != null) {
            activity!!.runOnUiThread {
                adapter!!.clear()
                adapter!!.addItems(0, recyclerViewItems)
                adapter!!.notifyDataSetChanged()
                changeEnabledStatusForBarItems(true)
            }
        }

        binding.swipeRefreshList.isRefreshing = false
    }

    private fun shouldPathBeSelectedDueToParent(currentPath: String): Boolean {
        if (selectedPaths.size > 0) {
            var file = File(currentPath)
            if (file.parent != "/") {
                while (file.parent != null) {
                    var parent = file.parent!!
                    if (File(file.parent!!).parent != null) {
                        parent += "/"
                    }
                    if (selectedPaths.contains(parent)) {
                        return true
                    }
                    file = File(file.parent!!)
                }
            }
        }
        return false
    }

    private fun checkAndRemoveAnySelectedParents(currentPath: String) {
        var file = File(currentPath)
        selectedPaths.remove(currentPath)
        while (file.parent != null) {
            selectedPaths.remove(file.parent!! + "/")
            file = File(file.parent!!)
        }
        if (activity != null) {
            activity!!.runOnUiThread {
                adapter!!.notifyDataSetChanged()
            }
        }
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        val browserFile = (adapter!!.getItem(position) as BrowserFileItem).model
        if ("inode/directory" == browserFile.mimeType) {
            fetchPath(browserFile.path!!)
            return true
        }
        return false
    }

    private fun prepareViews() {
        if (activity != null) {
            layoutManager = SmoothScrollLinearLayoutManager(activity)
            binding.recyclerView.layoutManager = layoutManager
            binding.recyclerView.setHasFixedSize(true)
            binding.recyclerView.adapter = adapter
            adapter!!.addListener(this)

            binding.swipeRefreshList.setOnRefreshListener(this)
            binding.swipeRefreshList.setColorSchemeResources(R.color.colorPrimary)
            binding.swipeRefreshList.setProgressBackgroundColorSchemeResource(R.color.refresh_spinner_background)
        }
    }

    @SuppressLint("RestrictedApi")
    override fun toggleBrowserItemSelection(path: String) {
        if (selectedPaths.contains(path) || shouldPathBeSelectedDueToParent(path)) {
            checkAndRemoveAnySelectedParents(path)
        } else {
            // TOOD: if it's a folder, remove all the children we added manually
            selectedPaths.add(path)
        }
        filesSelectionDoneMenuItem?.isVisible = selectedPaths.size > 0
    }

    override fun isPathSelected(path: String): Boolean {
        return selectedPaths.contains(path) || shouldPathBeSelectedDueToParent(path)
    }

    abstract override fun shouldOnlySelectOneImageFile(): Boolean

    @Parcelize
    enum class BrowserType : Parcelable {
        FILE_BROWSER, DAV_BROWSER
    }

    init {
        setHasOptionsMenu(true)
        sharedApplication!!.componentApplication.inject(this)
        browserType = Parcels.unwrap(args.getParcelable(KEY_BROWSER_TYPE))
        activeUser = Parcels.unwrap(args.getParcelable(KEY_USER_ENTITY))
        currentPath = "/"
        if (BrowserType.DAV_BROWSER == browserType) {
            listingAbstractClass = DavListing(this)
        } // else {
        // listingAbstractClass = new LocalListing(this);
        // }
        selectedPaths = Collections.synchronizedSet(TreeSet())
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private class SortingChangeListener(private val browserController: BrowserController) :
        OnPreferenceValueChangedListener<String> {
        override fun onChanged(newValue: String) {
            try {
                val sortOrder = LegacyFileSortOrder.getFileSortOrder(newValue)

                browserController.binding.sortButton.setText(DisplayUtils.getSortOrderStringId(sortOrder))
                browserController.recyclerViewItems = sortOrder.sortCloudFiles(browserController.recyclerViewItems)

                if (browserController.activity != null) {
                    browserController.activity!!.runOnUiThread {
                        browserController.adapter!!.updateDataSet(browserController.recyclerViewItems)
                        browserController.changeEnabledStatusForBarItems(true)
                    }
                }
            } catch (npe: NullPointerException) {
                // view binding can be null
                // since this is called asynchronously and UI might have been destroyed in the meantime
                Log.i(BrowserController.TAG, "UI destroyed - view binding already gone")
            }
        }
    }

    companion object {
        private const val TAG = "BrowserController"
    }
}
