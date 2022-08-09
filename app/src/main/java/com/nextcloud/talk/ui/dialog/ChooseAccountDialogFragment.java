/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
 * @author Marcel Hibbe
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2022 Marcel Hibbe (dev@mhibbe.de)
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
 *
 * Parts related to account import were either copied from or inspired by the great work done by David Luhmer at:
 * https://github.com/nextcloud/ownCloud-Account-Importer
 */

package com.nextcloud.talk.ui.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MainActivity;
import com.nextcloud.talk.adapters.items.AdvancedUserItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.databinding.DialogChooseAccountBinding;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.status.Status;
import com.nextcloud.talk.models.json.status.StatusOverall;
import com.nextcloud.talk.ui.StatusDrawable;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew;

import java.net.CookieManager;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import autodagger.AutoInjector;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@AutoInjector(NextcloudTalkApplication.class)
public class ChooseAccountDialogFragment extends DialogFragment {
    private static final String TAG = ChooseAccountDialogFragment.class.getSimpleName();

    private static final float STATUS_SIZE_IN_DP = 9f;

    @Inject
    UserManager userManager;

    @Inject
    CookieManager cookieManager;

    @Inject
    NcApi ncApi;

    @Inject
    ViewThemeUtils viewThemeUtils;

    private DialogChooseAccountBinding binding;
    private View dialogView;

    private FlexibleAdapter<AdvancedUserItem> adapter;
    private final List<AdvancedUserItem> userItems = new ArrayList<>();

    private Status status;

    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        binding = DialogChooseAccountBinding.inflate(LayoutInflater.from(requireContext()));
        dialogView = binding.getRoot();

        return new MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        viewThemeUtils.themeDialog(binding.getRoot());
        viewThemeUtils.themeDialogDivider(binding.divider);

        viewThemeUtils.colorMaterialTextButton(binding.setStatus);
        viewThemeUtils.colorMaterialTextButton(binding.addAccount);
        viewThemeUtils.colorMaterialTextButton(binding.manageSettings);

        // Defining user picture
        binding.currentAccount.userIcon.setTag("");

        // Defining user texts, accounts, etc.
        User user = userManager.getCurrentUser().blockingGet();
        if (user != null) {
            binding.currentAccount.userName.setText(user.getDisplayName());
            binding.currentAccount.ticker.setVisibility(View.GONE);
            binding.currentAccount.account.setText((Uri.parse(user.getBaseUrl()).getHost()));

            viewThemeUtils.colorImageView(binding.currentAccount.accountMenu);


            if (user.getBaseUrl() != null &&
                (user.getBaseUrl().startsWith("http://") || user.getBaseUrl().startsWith("https://"))) {
                binding.currentAccount.userIcon.setVisibility(View.VISIBLE);

                DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                    .setOldController(binding.currentAccount.userIcon.getController())
                    .setAutoPlayAnimations(true)
                    .setImageRequest(DisplayUtils.getImageRequestForUrl(
                        ApiUtils.getUrlForAvatar(
                            user.getBaseUrl(),
                            user.getUserId(),
                            false)))
                    .build();
                binding.currentAccount.userIcon.setController(draweeController);

            } else {
                binding.currentAccount.userIcon.setVisibility(View.INVISIBLE);
            }

            loadCurrentStatus(user);
        }

        // Creating listeners for quick-actions
        binding.currentAccount.getRoot().setOnClickListener(v -> dismiss());

        if (getActivity() instanceof MainActivity) {
            binding.addAccount.setOnClickListener(v -> {
                dismiss();
                ((MainActivity) getActivity()).addAccount();
            });
            binding.manageSettings.setOnClickListener(v -> {
                dismiss();
                ((MainActivity) getActivity()).openSettings();
            });
        }

        binding.setStatus.setOnClickListener(v -> {
            dismiss();

            if (status != null) {
                SetStatusDialogFragment setStatusDialog = SetStatusDialogFragment.newInstance(user, status);
                setStatusDialog.show(getActivity().getSupportFragmentManager(), "fragment_set_status");
            } else {
                Log.w(TAG, "status was null");
            }
        });

        if (adapter == null) {
            adapter = new FlexibleAdapter<>(userItems, getActivity(), false);

            User userEntity;
            Participant participant;

            for (Object userItem : userManager.getUsers().blockingGet()) {
                userEntity = (User) userItem;
                if (!userEntity.getCurrent()) {
                    String userId;
                    if (userEntity.getUserId() != null) {
                        userId = userEntity.getUserId();
                    } else {
                        userId = userEntity.getUsername();
                    }

                    participant = new Participant();
                    participant.setActorType(Participant.ActorType.USERS);
                    participant.setActorId(userId);
                    participant.setDisplayName(userEntity.getDisplayName());
                    userItems.add(new AdvancedUserItem(participant, userEntity, null));
                }
            }

            adapter.addListener(onSwitchItemClickListener);
            adapter.updateDataSet(userItems, false);
        }

        prepareViews();
    }

    private void loadCurrentStatus(User user) {
        String credentials = ApiUtils.getCredentials(user.getUsername(), user.getToken());

        if (CapabilitiesUtilNew.isUserStatusAvailable(userManager.getCurrentUser().blockingGet())) {
            binding.statusView.setVisibility(View.VISIBLE);

            ncApi.status(credentials, ApiUtils.getUrlForStatus(user.getBaseUrl())).
                subscribeOn(Schedulers.io()).
                observeOn(AndroidSchedulers.mainThread()).
                subscribe(new Observer<StatusOverall>() {

                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        // unused atm
                    }

                    @Override
                    public void onNext(@NonNull StatusOverall statusOverall) {
                        if (statusOverall.getOcs() != null) {
                            status = statusOverall.getOcs().getData();
                        }

                        try {
                            binding.setStatus.setEnabled(true);
                            drawStatus();
                        } catch (NullPointerException npe) {
                            Log.i(TAG, "UI already teared down", npe);
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Log.e(TAG, "Can't receive user status from server. ", e);
                        try {
                            binding.statusView.setVisibility(View.GONE);
                        } catch (NullPointerException npe) {
                            Log.i(TAG, "UI already teared down", npe);
                        }
                    }

                    @Override
                    public void onComplete() {
                        // unused atm
                    }
                });
        }
    }

    private void prepareViews() {
        if (getActivity() != null) {
            LinearLayoutManager layoutManager = new SmoothScrollLinearLayoutManager(getActivity());
            binding.accountsList.setLayoutManager(layoutManager);
        }
        binding.accountsList.setHasFixedSize(true);
        binding.accountsList.setAdapter(adapter);
    }

    public static ChooseAccountDialogFragment newInstance() {
        return new ChooseAccountDialogFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return dialogView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private final FlexibleAdapter.OnItemClickListener onSwitchItemClickListener =
        new FlexibleAdapter.OnItemClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if (userItems.size() > position) {
                    User user = (userItems.get(position)).getUser();

                    if (userManager.setUserAsActive(user).blockingGet()) {
                        cookieManager.getCookieStore().removeAll();
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(
                                () -> ((MainActivity) getActivity()).resetConversationsList());
                        }
                        dismiss();
                    }
                }

                return true;
            }
        };

    private void drawStatus() {
        float size = DisplayUtils.convertDpToPixel(STATUS_SIZE_IN_DP, getContext());
        binding.currentAccount.ticker.setBackground(null);
        binding.currentAccount.ticker.setImageDrawable(new StatusDrawable(
            status.getStatus(),
            status.getIcon(),
            size,
            viewThemeUtils.getScheme(binding.currentAccount.ticker.getContext()).getSurface(),
            getContext()));
        binding.currentAccount.ticker.setVisibility(View.VISIBLE);


        if (status.getMessage() != null && !status.getMessage().isEmpty()) {
            binding.currentAccount.status.setText(status.getMessage());
            binding.currentAccount.status.setVisibility(View.VISIBLE);
        } else {
            binding.currentAccount.status.setText("");
            binding.currentAccount.status.setVisibility(View.GONE);
        }
    }
}
