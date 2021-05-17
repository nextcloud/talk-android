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

package com.nextcloud.talk.components.filebrowser.controllers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.components.filebrowser.adapters.items.BrowserFileItem;
import com.nextcloud.talk.components.filebrowser.interfaces.ListingInterface;
import com.nextcloud.talk.components.filebrowser.models.BrowserFile;
import com.nextcloud.talk.components.filebrowser.models.DavResponse;
import com.nextcloud.talk.components.filebrowser.operations.DavListing;
import com.nextcloud.talk.components.filebrowser.operations.ListingAbstractClass;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.interfaces.SelectionInterface;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import org.parceler.Parcel;
import org.parceler.Parcels;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import eu.davidea.fastscroller.FastScroller;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import okhttp3.OkHttpClient;

@AutoInjector(NextcloudTalkApplication.class)
public abstract class BrowserController extends BaseController implements ListingInterface,
        FlexibleAdapter.OnItemClickListener, SelectionInterface {
    protected final Set<String> selectedPaths;
    @Inject
    UserUtils userUtils;
    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;
    @BindView(R.id.fast_scroller)
    FastScroller fastScroller;
    @BindView(R.id.action_back)
    BottomNavigationItemView backMenuItem;
    @BindView(R.id.action_refresh)
    BottomNavigationItemView actionRefreshMenuItem;
    @Inject
    Context context;
    @Inject
    OkHttpClient okHttpClient;

    private MenuItem filesSelectionDoneMenuItem;
    private RecyclerView.LayoutManager layoutManager;

    private FlexibleAdapter<AbstractFlexibleItem> adapter;
    private List<AbstractFlexibleItem> recyclerViewItems = new ArrayList<>();

    private ListingAbstractClass listingAbstractClass;
    private BrowserType browserType;
    private String currentPath;
    protected UserEntity activeUser;

    public BrowserController(Bundle args) {
        super(args);
        setHasOptionsMenu(true);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        browserType = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_BROWSER_TYPE()));
        activeUser = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY()));

        currentPath = "/";
        if (BrowserType.DAV_BROWSER.equals(browserType)) {
            listingAbstractClass = new DavListing(this);
        } else {
            //listingAbstractClass = new LocalListing(this);
        }

        selectedPaths = Collections.synchronizedSet(new TreeSet<>());
    }

    @NonNull
    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_browser, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        if (adapter == null) {
            adapter = new FlexibleAdapter<>(recyclerViewItems, context, false);
        }

        changeEnabledStatusForBarItems(true);
        prepareViews();
    }

    abstract void onFileSelectionDone();

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_share_files, menu);
        filesSelectionDoneMenuItem = menu.findItem(R.id.files_selection_done);
        filesSelectionDoneMenuItem.setVisible(selectedPaths.size() > 0);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.files_selection_done:
                onFileSelectionDone();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        refreshCurrentPath();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        listingAbstractClass.tearDown();
    }

    @Override
    protected String getTitle() {
        return currentPath;
    }

    @OnClick(R.id.action_back)
    void goBack() {
        fetchPath(new File(currentPath).getParent());
    }

    @OnClick(R.id.action_refresh)
    void refreshCurrentPath() {
        fetchPath(currentPath);
    }

    @SuppressLint("RestrictedApi")
    private void changeEnabledStatusForBarItems(boolean shouldBeEnabled) {
        if (actionRefreshMenuItem != null) {
            actionRefreshMenuItem.setEnabled(shouldBeEnabled);
        }

        if (backMenuItem != null) {
            backMenuItem.setEnabled(shouldBeEnabled && !currentPath.equals("/"));
        }
    }

    private void fetchPath(String path) {
        listingAbstractClass.cancelAllJobs();
        changeEnabledStatusForBarItems(false);

        listingAbstractClass.getFiles(path, activeUser, BrowserType.DAV_BROWSER.equals(browserType) ? okHttpClient : null);
    }

    @Override
    public void listingResult(DavResponse davResponse) {
        adapter.clear();
        List<AbstractFlexibleItem> fileBrowserItems = new ArrayList<>();
        if (davResponse.getData() != null) {
            final List<BrowserFile> objectList = (List<BrowserFile>) davResponse.getData();

            currentPath = objectList.get(0).getPath();

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> setTitle());
            }

            for (int i = 1; i < objectList.size(); i++) {
                fileBrowserItems.add(new BrowserFileItem(objectList.get(i), activeUser, this));
            }
        }

        adapter.addItems(0, fileBrowserItems);

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                changeEnabledStatusForBarItems(true);

            });
        }
    }

    private boolean shouldPathBeSelectedDueToParent(String currentPath) {
        if (selectedPaths.size() > 0) {
            File file = new File(currentPath);
            if (!file.getParent().equals("/")) {
                while (file.getParent() != null) {
                    String parent = file.getParent();
                    if (new File(file.getParent()).getParent() != null) {
                        parent += "/";
                    }

                    if (selectedPaths.contains(parent)) {
                        return true;
                    }

                    file = new File(file.getParent());
                }
            }
        }

        return false;
    }

    private void checkAndRemoveAnySelectedParents(String currentPath) {
        File file = new File(currentPath);
        selectedPaths.remove(currentPath);
        while (file.getParent() != null) {
            selectedPaths.remove(file.getParent() + "/");
            file = new File(file.getParent());
        }

        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onItemClick(View view, int position) {
        BrowserFile browserFile = ((BrowserFileItem) adapter.getItem(position)).getModel();
        if ("inode/directory".equals((browserFile.getMimeType()))) {
            fetchPath(browserFile.getPath());
            return true;
        }

        return false;
    }

    private void prepareViews() {
        if (getActivity() != null) {
            layoutManager = new SmoothScrollLinearLayoutManager(getActivity());
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setHasFixedSize(true);
            recyclerView.setAdapter(adapter);

            adapter.setFastScroller(fastScroller);
            adapter.addListener(this);

            fastScroller.setBubbleTextCreator(position -> {
                IFlexible abstractFlexibleItem = adapter.getItem(position);
                if (abstractFlexibleItem instanceof BrowserFileItem) {
                    return String.valueOf(((BrowserFileItem) adapter.getItem(position)).getModel().getDisplayName().charAt(0));
                } else {
                    return "";
                }
            });
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void toggleBrowserItemSelection(@NonNull String path) {
        if (selectedPaths.contains(path) || shouldPathBeSelectedDueToParent(path)) {
            checkAndRemoveAnySelectedParents(path);
        } else {
            // TOOD: if it's a folder, remove all the children we added manually
            selectedPaths.add(path);
        }

        filesSelectionDoneMenuItem.setVisible(selectedPaths.size() > 0);
    }

    @Override
    public boolean isPathSelected(@NonNull String path) {
        return (selectedPaths.contains(path) || shouldPathBeSelectedDueToParent(path));
    }

    @Override
    abstract public boolean shouldOnlySelectOneImageFile();

    @Parcel
    public enum BrowserType {
        FILE_BROWSER,
        DAV_BROWSER,
    }
}
