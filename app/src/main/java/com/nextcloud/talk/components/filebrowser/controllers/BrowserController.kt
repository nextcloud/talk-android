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

package com.nextcloud.talk.components.filebrowser.controllers

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import butterknife.BindView
import butterknife.OnClick
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.nextcloud.talk.R
import com.nextcloud.talk.components.filebrowser.adapters.items.BrowserFileItem
import com.nextcloud.talk.components.filebrowser.interfaces.ListingInterface
import com.nextcloud.talk.components.filebrowser.models.BrowserFile
import com.nextcloud.talk.components.filebrowser.models.DavResponse
import com.nextcloud.talk.components.filebrowser.operations.DavListing
import com.nextcloud.talk.components.filebrowser.operations.ListingAbstractClass
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.interfaces.SelectionInterface
import com.nextcloud.talk.jobs.ShareOperationWorker
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.bundle.BundleKeys
import eu.davidea.fastscroller.FastScroller
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.parceler.Parcel
import org.parceler.Parcels
import java.io.File
import java.util.*

class BrowserController(args: Bundle) : BaseController(), ListingInterface, FlexibleAdapter.OnItemClickListener, SelectionInterface {
    private val selectedPaths: MutableSet<String>

    @JvmField
    @BindView(R.id.recyclerView)
    internal var recyclerView: RecyclerView? = null

    @JvmField
    @BindView(R.id.fast_scroller)
    internal var fastScroller: FastScroller? = null

    @JvmField
    @BindView(R.id.action_back)
    internal var backMenuItem: BottomNavigationItemView? = null

    @JvmField
    @BindView(R.id.action_refresh)
    internal var actionRefreshMenuItem: BottomNavigationItemView? = null

    val okHttpClient: OkHttpClient by inject()

    private var filesSelectionDoneMenuItem: MenuItem? = null
    private var layoutManager: RecyclerView.LayoutManager? = null

    private var adapter: FlexibleAdapter<AbstractFlexibleItem<*>>? = null
    private val recyclerViewItems = ArrayList<AbstractFlexibleItem<*>>()

    private var listingAbstractClass: ListingAbstractClass? = null
    private val browserType: BrowserType
    private var currentPath: String? = null
    private val activeUser: User
    private val roomToken: String?

    init {
        setHasOptionsMenu(true)
        browserType = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_BROWSER_TYPE))
        activeUser = args.getParcelable(BundleKeys.KEY_USER_ENTITY)!!
        roomToken = args.getString(BundleKeys.KEY_CONVERSATION_TOKEN)

        currentPath = "/"
        if (BrowserType.DAV_BROWSER == browserType) {
            listingAbstractClass = DavListing(this)
        } else {
            //listingAbstractClass = new LocalListing(this);
        }

        selectedPaths = Collections.synchronizedSet(TreeSet())
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_browser, container, false)
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        if (adapter == null) {
            adapter = FlexibleAdapter(recyclerViewItems, context, false)
        }

        changeEnabledStatusForBarItems(true)
        prepareViews()
    }

    private fun onFileSelectionDone() {
        synchronized(selectedPaths) {
            val iterator = selectedPaths.iterator()

            var paths: MutableList<String> = ArrayList()
            var data: Data
            var shareWorker: OneTimeWorkRequest

            while (iterator.hasNext()) {
                val path = iterator.next()
                paths.add(path)
                iterator.remove()
                if (paths.size == 10 || !iterator.hasNext()) {
                    data = Data.Builder()
                            .putLong(BundleKeys.KEY_INTERNAL_USER_ID, activeUser.id!!)
                            .putString(BundleKeys.KEY_CONVERSATION_TOKEN, roomToken)
                            .putStringArray(BundleKeys.KEY_FILE_PATHS, paths.toTypedArray())
                            .build()
                    shareWorker = OneTimeWorkRequest.Builder(ShareOperationWorker::class.java)
                            .setInputData(data)
                            .build()
                    WorkManager.getInstance().enqueue(shareWorker)
                    paths = ArrayList()
                }
            }
        }

        router.popCurrentController()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_share_files, menu)
        filesSelectionDoneMenuItem = menu.findItem(R.id.files_selection_done)
        filesSelectionDoneMenuItem!!.isVisible = selectedPaths.size > 0
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.files_selection_done -> {
                onFileSelectionDone()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        refreshCurrentPath()
    }

    public override fun onDestroy() {
        super.onDestroy()
        listingAbstractClass!!.tearDown()
    }

    override fun getTitle(): String? {
        return currentPath
    }

    @OnClick(R.id.action_back)
    internal fun goBack() {
        fetchPath(File(currentPath!!).parent)
    }

    @OnClick(R.id.action_refresh)
    internal fun refreshCurrentPath() {
        fetchPath(currentPath)
    }

    @SuppressLint("RestrictedApi")
    private fun changeEnabledStatusForBarItems(shouldBeEnabled: Boolean) {
        if (actionRefreshMenuItem != null) {
            actionRefreshMenuItem!!.isEnabled = shouldBeEnabled
        }

        if (backMenuItem != null) {
            backMenuItem!!.isEnabled = shouldBeEnabled && currentPath != "/"
        }
    }

    private fun fetchPath(path: String?) {
        listingAbstractClass!!.cancelAllJobs()
        changeEnabledStatusForBarItems(false)

        listingAbstractClass!!.getFiles(path, activeUser,
                if (BrowserType.DAV_BROWSER == browserType) okHttpClient else null)
    }

    override fun listingResult(davResponse: DavResponse) {
        adapter!!.clear()
        val fileBrowserItems = ArrayList<AbstractFlexibleItem<*>>()
        if (davResponse.data != null) {
            val objectList = davResponse.data as List<BrowserFile>

            currentPath = objectList[0].path

            if (activity != null) {
                activity!!.runOnUiThread { setTitle() }
            }

            for (i in 1 until objectList.size) {
                fileBrowserItems.add(BrowserFileItem(objectList[i], activeUser, this))
            }
        }

        adapter!!.addItems(0, fileBrowserItems)

        if (activity != null) {
            activity!!.runOnUiThread {
                adapter!!.notifyDataSetChanged()
                changeEnabledStatusForBarItems(true)
            }
        }
    }

    private fun shouldPathBeSelectedDueToParent(currentPath: String): Boolean {
        if (selectedPaths.size > 0) {
            var file = File(currentPath)
            if (file.parent != "/") {
                while (file.parent != null) {
                    var parent = file.parent
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

        adapter!!.notifyDataSetChanged()
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        val browserFile = (adapter!!.getItem(position) as BrowserFileItem).model
        if ("inode/directory" == browserFile.mimeType) {
            fetchPath(browserFile.path)
            return true
        }

        return false
    }

    private fun prepareViews() {
        if (activity != null) {
            layoutManager = SmoothScrollLinearLayoutManager(activity!!)
            recyclerView!!.layoutManager = layoutManager
            recyclerView!!.setHasFixedSize(true)
            recyclerView!!.adapter = adapter

            adapter!!.fastScroller = fastScroller
            adapter!!.addListener(this)

            fastScroller!!.setBubbleTextCreator { position ->
                val abstractFlexibleItem = adapter!!.getItem(position)
                if (abstractFlexibleItem is BrowserFileItem) {
                    (adapter!!.getItem(position) as BrowserFileItem).model.displayName.toCharArray()[0].toString()
                } else {
                    ""
                }
            }
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

        filesSelectionDoneMenuItem!!.isVisible = selectedPaths.size > 0
    }

    override fun isPathSelected(path: String): Boolean {
        return selectedPaths.contains(path) || shouldPathBeSelectedDueToParent(path)
    }

    @Parcel
    enum class BrowserType {
        FILE_BROWSER,
        DAV_BROWSER
    }
}
