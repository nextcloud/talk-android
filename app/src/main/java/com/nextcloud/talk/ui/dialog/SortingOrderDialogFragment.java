/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2017-2021 Andy Scherzinger
 * Copyright (C) 2017 Nextcloud
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

package com.nextcloud.talk.ui.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.databinding.SortingOrderFragmentBinding;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;
import com.nextcloud.talk.utils.FileSortOrder;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import autodagger.AutoInjector;

/**
 * Dialog to show and choose the sorting order for the file listing.
 */
@AutoInjector(NextcloudTalkApplication.class)
public class SortingOrderDialogFragment extends DialogFragment implements View.OnClickListener {

    private final static String TAG = SortingOrderDialogFragment.class.getSimpleName();

    public static final String SORTING_ORDER_FRAGMENT = "SORTING_ORDER_FRAGMENT";
    private static final String KEY_SORT_ORDER = "SORT_ORDER";

    @Inject
    AppPreferences appPreferences;

    @Inject
    ViewThemeUtils viewThemeUtils;

    private SortingOrderFragmentBinding binding;
    private View dialogView;

    private View[] taggedViews;
    private String currentSortOrderName;

    public static SortingOrderDialogFragment newInstance(@NotNull FileSortOrder sortOrder) {
        SortingOrderDialogFragment dialogFragment = new SortingOrderDialogFragment();

        Bundle args = new Bundle();
        args.putString(KEY_SORT_ORDER, sortOrder.getName());
        dialogFragment.setArguments(args);

        return dialogFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // keep the state of the fragment on configuration changes
        setRetainInstance(true);

        if (getArguments() != null) {
            currentSortOrderName = getArguments().getString(KEY_SORT_ORDER,
                                                            FileSortOrder.Companion.getSort_a_to_z().getName());
        }
    }

    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        binding = SortingOrderFragmentBinding.inflate(LayoutInflater.from(requireContext()));
        dialogView = binding.getRoot();

        return new MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return dialogView;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        setupDialogElements();
        setupListeners();
    }

    /**
     * find all relevant UI elements and set their values.
     */
    private void setupDialogElements() {
        viewThemeUtils.colorMaterialButtonText(binding.cancel);

        taggedViews = new View[12];
        taggedViews[0] = binding.sortByNameAscending;
        taggedViews[0].setTag(FileSortOrder.Companion.getSort_a_to_z());
        taggedViews[1] = binding.sortByNameAZText;
        taggedViews[1].setTag(FileSortOrder.Companion.getSort_a_to_z());
        taggedViews[2] = binding.sortByNameDescending;
        taggedViews[2].setTag(FileSortOrder.Companion.getSort_z_to_a());
        taggedViews[3] = binding.sortByNameZAText;
        taggedViews[3].setTag(FileSortOrder.Companion.getSort_z_to_a());
        taggedViews[4] = binding.sortByModificationDateAscending;
        taggedViews[4].setTag(FileSortOrder.Companion.getSort_old_to_new());
        taggedViews[5] = binding.sortByModificationDateOldestFirstText;
        taggedViews[5].setTag(FileSortOrder.Companion.getSort_old_to_new());
        taggedViews[6] = binding.sortByModificationDateDescending;
        taggedViews[6].setTag(FileSortOrder.Companion.getSort_new_to_old());
        taggedViews[7] = binding.sortByModificationDateNewestFirstText;
        taggedViews[7].setTag(FileSortOrder.Companion.getSort_new_to_old());
        taggedViews[8] = binding.sortBySizeAscending;
        taggedViews[8].setTag(FileSortOrder.Companion.getSort_small_to_big());
        taggedViews[9] = binding.sortBySizeSmallestFirstText;
        taggedViews[9].setTag(FileSortOrder.Companion.getSort_small_to_big());
        taggedViews[10] = binding.sortBySizeDescending;
        taggedViews[10].setTag(FileSortOrder.Companion.getSort_big_to_small());
        taggedViews[11] = binding.sortBySizeBiggestFirstText;
        taggedViews[11].setTag(FileSortOrder.Companion.getSort_big_to_small());

        setupActiveOrderSelection();
    }

    /**
     * tints the icon reflecting the actual sorting choice in the apps primary color.
     */
    private void setupActiveOrderSelection() {
        Log.i("SortOrder", "currentSortOrderName=" + currentSortOrderName);
        for (View view : taggedViews) {
            Log.i("SortOrder", ((FileSortOrder) view.getTag()).getName());
            if (!((FileSortOrder) view.getTag()).getName().equals(currentSortOrderName)) {
                continue;
            }
            if (view instanceof MaterialButton) {
                viewThemeUtils.colorMaterialButtonText((MaterialButton) view);
            }
            if (view instanceof TextView) {
                viewThemeUtils.colorTextViewElement((TextView) view);
                ((TextView) view).setTypeface(Typeface.DEFAULT_BOLD);
            }
        }
    }

    /**
     * setup all listeners.
     */
    private void setupListeners() {
        binding.cancel.setOnClickListener(view -> dismiss());

        for (View view : taggedViews) {
            Log.i("SortOrder", "view="+view.getTag().toString());
            view.setOnClickListener(this);
        }
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "destroy SortingOrderDialogFragment view");
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onClick(View v) {
        appPreferences.setSorting(((FileSortOrder) v.getTag()).getName());
        dismiss();
    }
}
