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
import android.view.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
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
import com.nextcloud.talk.jobs.ShareOperationWorker;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import eu.davidea.fastscroller.FastScroller;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import okhttp3.OkHttpClient;
import org.parceler.Parcel;
import org.parceler.Parcels;

import javax.inject.Inject;
import java.io.File;
import java.util.*;

@AutoInjector(NextcloudTalkApplication.class)
public class BrowserController extends BaseController implements ListingInterface,
        FlexibleAdapter.OnItemClickListener, SelectionInterface {
    private final Set<String> selectedPaths;
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
    private UserEntity activeUser;
    private String roomToken;

    public BrowserController(Bundle args) {
        super(args);
        setHasOptionsMenu(true);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        browserType = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_BROWSER_TYPE));
        activeUser = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_USER_ENTITY));
        roomToken = args.getString(BundleKeys.KEY_ROOM_TOKEN);

        currentPath = "/";
        if (BrowserType.DAV_BROWSER.equals(browserType)) {
            listingAbstractClass = new DavListing(this);
        } else {
            //listingAbstractClass = new LocalListing(this);
        }

        selectedPaths = Collections.synchronizedSet(new TreeSet<>());
    }

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

    private void onFileSelectionDone() {
        synchronized (selectedPaths) {
            Iterator<String> iterator = selectedPaths.iterator();

            List<String> paths = new ArrayList<>();
            Data data;
            OneTimeWorkRequest shareWorker;

            while (iterator.hasNext()) {
                String path = iterator.next();
                paths.add(path);
                iterator.remove();
                if (paths.size() == 10 || !iterator.hasNext()) {
                    data = new Data.Builder()
                            .putLong(BundleKeys.KEY_INTERNAL_USER_ID, activeUser.getId())
                            .putString(BundleKeys.KEY_ROOM_TOKEN, roomToken)
                            .putStringArray(BundleKeys.KEY_FILE_PATHS, paths.toArray(new String[0]))
                            .build();
                    shareWorker = new OneTimeWorkRequest.Builder(ShareOperationWorker.class)
                            .setInputData(data)
                            .build();
                    WorkManager.getInstance().enqueue(shareWorker);
                    paths = new ArrayList<>();
                }
            }
        }

        getRouter().popCurrentController();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
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
    public void toggleBrowserItemSelection(String path) {
        if (selectedPaths.contains(path) || shouldPathBeSelectedDueToParent(path)) {
            checkAndRemoveAnySelectedParents(path);
        } else {
            // TOOD: if it's a folder, remove all the children we added manually
            selectedPaths.add(path);
        }

        filesSelectionDoneMenuItem.setVisible(selectedPaths.size() > 0);
    }

    @Override
    public boolean isPathSelected(String path) {
        return (selectedPaths.contains(path) || shouldPathBeSelectedDueToParent(path));
    }

    @Parcel
    public enum BrowserType {
        FILE_BROWSER,
        DAV_BROWSER,
    }
}
