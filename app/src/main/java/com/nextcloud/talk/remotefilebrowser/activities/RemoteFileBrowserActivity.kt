/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.remotefilebrowser.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.ActivityRemoteFileBrowserBinding
import com.nextcloud.talk.interfaces.SelectionInterface
import com.nextcloud.talk.remotefilebrowser.adapters.RemoteFileBrowserItemsAdapter
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import com.nextcloud.talk.remotefilebrowser.viewmodels.RemoteFileBrowserItemsViewModel
import com.nextcloud.talk.ui.dialog.SortingOrderDialogFragment
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.FileSortOrder
import com.nextcloud.talk.utils.database.user.UserUtils
import java.io.File
import java.util.Collections
import java.util.TreeSet
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class RemoteFileBrowserActivity : AppCompatActivity(), SelectionInterface, SwipeRefreshLayout.OnRefreshListener {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    // TODO use CurrentUserProvider instead for narrower scope
    @Inject
    lateinit var userUtils: UserUtils

    private lateinit var binding: ActivityRemoteFileBrowserBinding
    private lateinit var viewModel: RemoteFileBrowserItemsViewModel

    private var filesSelectionDoneMenuItem: MenuItem? = null

    private val selectedPaths: MutableSet<String> = Collections.synchronizedSet(TreeSet())
    private var currentPath: String = "/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityRemoteFileBrowserBinding.inflate(layoutInflater)
        setSupportActionBar(binding.remoteFileBrowserItemsToolbar)
        setContentView(binding.root)

        DisplayUtils.applyColorToStatusBar(
            this,
            ResourcesCompat.getColor(
                resources, R.color.appbar, null
            )
        )
        DisplayUtils.applyColorToNavigationBar(
            this.window,
            ResourcesCompat.getColor(resources, R.color.bg_default, null)
        )

        supportActionBar?.title = "current patch"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initViewModel()

        binding.swipeRefreshList.setOnRefreshListener(this)
        binding.swipeRefreshList.setColorSchemeResources(R.color.colorPrimary)
        binding.swipeRefreshList.setProgressBackgroundColorSchemeResource(R.color.refresh_spinner_background)

        binding.pathNavigationBackButton.setOnClickListener { goBack() }
        binding.sortButton.setOnClickListener { changeSorting() }

        viewModel.loadItems(currentPath)
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this, viewModelFactory)[RemoteFileBrowserItemsViewModel::class.java]

        viewModel.viewState.observe(this) { state ->
            clearEmptyLoading()
            when (state) {
                is RemoteFileBrowserItemsViewModel.LoadingItemsState, RemoteFileBrowserItemsViewModel.InitialState -> {
                    showLoading()
                }
                is RemoteFileBrowserItemsViewModel.NoRemoteFileItemsState -> {
                    showEmpty()
                }
                is RemoteFileBrowserItemsViewModel.LoadedState -> {
                    val remoteFileBrowserItems = state.items
                    Log.d(TAG, "Items received: $remoteFileBrowserItems")

                    // TODO make shwoGrid based on preferences
                    val showGrid = false
                    val layoutManager = if (showGrid) {
                        GridLayoutManager(this, SPAN_COUNT)
                    } else {
                        LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
                    }

                    // TODO make mimeTypeSelectionFilter a bundled arg for the activity
                    val mimeTypeSelectionFilter = "image/"
                    // TODO do not needlesly recreate adapter if it can be reused
                    val adapter = RemoteFileBrowserItemsAdapter(
                        showGrid = showGrid,
                        mimeTypeSelectionFilter = mimeTypeSelectionFilter,
                        userEntity = userUtils.currentUser!!,
                        selectionInterface = this
                    ) { remoteFileBrowserItem ->
                        onItemClicked(remoteFileBrowserItem)
                    }
                        .apply {
                            items = if (remoteFileBrowserItems.size > 1) {
                                remoteFileBrowserItems.subList(1, remoteFileBrowserItems.size)
                            } else {
                                ArrayList()
                            }
                        }

                    binding.recyclerView.adapter = adapter
                    binding.recyclerView.layoutManager = layoutManager
                    binding.recyclerView.setHasFixedSize(true)

                    showList()
                }
            }
        }

        viewModel.fileSortOrder.observe(this) { sortOrder ->
            if (sortOrder != null) {
                binding.sortButton.setText(DisplayUtils.getSortOrderStringId(sortOrder))
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_share_files, menu)
        filesSelectionDoneMenuItem = menu?.findItem(R.id.files_selection_done)
        filesSelectionDoneMenuItem?.isVisible = selectedPaths.size > 0
        return true
    }

    private fun onItemClicked(remoteFileBrowserItem: RemoteFileBrowserItem) {
        if ("inode/directory" == remoteFileBrowserItem.mimeType) {
            currentPath = remoteFileBrowserItem.path!!
            viewModel.loadItems(currentPath)
        } else {
            toggleBrowserItemSelection(remoteFileBrowserItem.path!!)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentPath()
    }

    private fun changeSorting() {
        val newFragment: DialogFragment = SortingOrderDialogFragment
            .newInstance(FileSortOrder.getFileSortOrder(viewModel.fileSortOrder.value!!.name))
        newFragment.show(
            supportFragmentManager,
            SortingOrderDialogFragment.SORTING_ORDER_FRAGMENT
        )
    }

    private fun goBack(): Boolean {
        if (currentPath != "/") {
            viewModel.loadItems(File(currentPath).parent!!)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.files_selection_done -> {
                onFileSelectionDone()
                true
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
    }

    private fun onFileSelectionDone() {
        val data = Intent()
        data.putStringArrayListExtra(EXTRA_SELECTED_PATHS, ArrayList(selectedPaths))
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun clearEmptyLoading() {
        binding.emptyContainer.emptyListView.visibility = View.GONE
    }

    private fun showLoading() {
        binding.emptyContainer.emptyListViewHeadline.text = getString(R.string.file_list_loading)
        binding.emptyContainer.emptyListView.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun showEmpty() {
        binding.emptyContainer.emptyListViewHeadline.text = getString(R.string.nc_shared_items_empty)
        binding.emptyContainer.emptyListView.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun showList() {
        binding.recyclerView.visibility = View.VISIBLE
    }

    override fun onRefresh() {
        refreshCurrentPath()
    }

    private fun refreshCurrentPath() {
        viewModel.loadItems(currentPath)
    }

    private fun shouldPathBeSelectedDueToParent(currentPath: String): Boolean {
        var file = File(currentPath)
        if (selectedPaths.size > 0 && file.parent != "/") {
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
        return false
    }

    private fun checkAndRemoveAnySelectedParents(currentPath: String) {
        var file = File(currentPath)
        selectedPaths.remove(currentPath)
        while (file.parent != null) {
            selectedPaths.remove(file.parent!! + "/")
            file = File(file.parent!!)
        }
        runOnUiThread {
            binding.recyclerView.adapter!!.notifyDataSetChanged()
        }
    }

    companion object {
        private val TAG = RemoteFileBrowserActivity::class.simpleName
        const val SPAN_COUNT: Int = 4
        const val EXTRA_SELECTED_PATHS = "EXTRA_SELECTED_PATH"
        const val REQUEST_CODE_SELECT_AVATAR = 22
    }

    override fun toggleBrowserItemSelection(path: String) {
        if (selectedPaths.contains(path) || shouldPathBeSelectedDueToParent(path)) {
            checkAndRemoveAnySelectedParents(path)
        } else {
            // TODO if it's a folder, remove all the children we added manually
            selectedPaths.add(path)
        }
        filesSelectionDoneMenuItem?.isVisible = selectedPaths.size > 0
    }

    override fun isPathSelected(path: String): Boolean {
        return selectedPaths.contains(path) || shouldPathBeSelectedDueToParent(path)
    }

    override fun shouldOnlySelectOneImageFile(): Boolean {
        return true
    }
}
