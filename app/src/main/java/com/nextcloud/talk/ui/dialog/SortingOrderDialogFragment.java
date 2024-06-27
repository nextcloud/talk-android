/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Nextcloud Gmbh
 * SPDX-License-Identifier: GPL-3.0-or-later
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

import java.util.ArrayList;
import java.util.List;

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

    private List<View> taggedViews;
    private String currentSortOrderName;

    public static SortingOrderDialogFragment newInstance(@NonNull FileSortOrder sortOrder) {
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
        binding = SortingOrderFragmentBinding.inflate(getLayoutInflater());
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
        viewThemeUtils.platform.themeDialog(binding.root);
        viewThemeUtils.material.colorMaterialButtonPrimaryBorderless(binding.cancel);

        taggedViews = new ArrayList<>(12);

        binding.sortByNameAscending.setTag(FileSortOrder.Companion.getSort_a_to_z());
        taggedViews.add(binding.sortByNameAscending);
        binding.sortByNameAZText.setTag(FileSortOrder.Companion.getSort_a_to_z());
        taggedViews.add(binding.sortByNameAZText);
        binding.sortByNameDescending.setTag(FileSortOrder.Companion.getSort_z_to_a());
        taggedViews.add(binding.sortByNameDescending);
        binding.sortByNameZAText.setTag(FileSortOrder.Companion.getSort_z_to_a());
        taggedViews.add(binding.sortByNameZAText);
        binding.sortByModificationDateAscending.setTag(FileSortOrder.Companion.getSort_old_to_new());
        taggedViews.add(binding.sortByModificationDateAscending);
        binding.sortByModificationDateOldestFirstText.setTag(FileSortOrder.Companion.getSort_old_to_new());
        taggedViews.add(binding.sortByModificationDateOldestFirstText);
        binding.sortByModificationDateDescending.setTag(FileSortOrder.Companion.getSort_new_to_old());
        taggedViews.add(binding.sortByModificationDateDescending);
        binding.sortByModificationDateNewestFirstText.setTag(FileSortOrder.Companion.getSort_new_to_old());
        taggedViews.add(binding.sortByModificationDateNewestFirstText);
        binding.sortBySizeAscending.setTag(FileSortOrder.Companion.getSort_small_to_big());
        taggedViews.add(binding.sortBySizeAscending);
        binding.sortBySizeSmallestFirstText.setTag(FileSortOrder.Companion.getSort_small_to_big());
        taggedViews.add(binding.sortBySizeSmallestFirstText);
        binding.sortBySizeDescending.setTag(FileSortOrder.Companion.getSort_big_to_small());
        taggedViews.add(binding.sortBySizeDescending);
        binding.sortBySizeBiggestFirstText.setTag(FileSortOrder.Companion.getSort_big_to_small());
        taggedViews.add(binding.sortBySizeBiggestFirstText);

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
                viewThemeUtils.material.colorMaterialButtonText((MaterialButton) view);
            }
            if (view instanceof TextView) {
                viewThemeUtils.platform.colorPrimaryTextViewElement((TextView) view);
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
