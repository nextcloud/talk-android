/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe (dev@mhibbe.de)
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nextcloud.talk.account.ServerSelectionActivity;
import com.nextcloud.talk.adapters.items.AdvancedUserItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.conversationlist.ConversationsListActivity;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.databinding.DialogChooseAccountBinding;
import com.nextcloud.talk.extensions.ImageViewExtensionsKt;
import com.nextcloud.talk.invitation.data.InvitationsModel;
import com.nextcloud.talk.invitation.data.InvitationsRepository;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.status.Status;
import com.nextcloud.talk.models.json.status.StatusOverall;
import com.nextcloud.talk.settings.SettingsActivity;
import com.nextcloud.talk.ui.StatusDrawable;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.CapabilitiesUtil;
import com.nextcloud.talk.utils.DisplayUtils;

import java.net.CookieManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

import static com.nextcloud.talk.utils.bundle.BundleKeys.ADD_ADDITIONAL_ACCOUNT;

@AutoInjector(NextcloudTalkApplication.class)
public class ChooseAccountDialogFragment extends DialogFragment {
    public static final String TAG = ChooseAccountDialogFragment.class.getSimpleName();

    private static final float STATUS_SIZE_IN_DP = 9f;

    Disposable disposable;

    @Inject
    UserManager userManager;

    @Inject
    CookieManager cookieManager;

    @Inject
    NcApi ncApi;

    @Inject
    ViewThemeUtils viewThemeUtils;

    @Inject
    InvitationsRepository invitationsRepository;

    private DialogChooseAccountBinding binding;
    private View dialogView;

    private FlexibleAdapter<AdvancedUserItem> adapter;
    private final List<AdvancedUserItem> userItems = new ArrayList<>();

    private Status status;

    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        binding = DialogChooseAccountBinding.inflate(getLayoutInflater());
        dialogView = binding.getRoot();

        return new MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Objects.requireNonNull(NextcloudTalkApplication.Companion.getSharedApplication()).getComponentApplication().inject(this);
        User user = userManager.getCurrentUser().blockingGet();

        themeViews();
        setupCurrentUser(user);
        setupListeners();
        setupAdapter();
        prepareViews();
    }

    private void setupCurrentUser(User user) {
        // Defining user picture
        binding.currentAccount.userIcon.setTag("");
        if (user != null) {
            binding.currentAccount.userName.setText(user.getDisplayName());
            binding.currentAccount.ticker.setVisibility(View.GONE);
            binding.currentAccount.account.setText((Uri.parse(user.getBaseUrl()).getHost()));

            viewThemeUtils.platform.colorImageView(binding.currentAccount.accountMenu);


            if (user.getBaseUrl() != null &&
                (user.getBaseUrl().startsWith("http://") || user.getBaseUrl().startsWith("https://"))) {
                binding.currentAccount.userIcon.setVisibility(View.VISIBLE);
                ImageViewExtensionsKt.loadUserAvatar(binding.currentAccount.userIcon, user, user.getUserId(), true,
                                                     false);
            } else {
                binding.currentAccount.userIcon.setVisibility(View.INVISIBLE);
            }
            loadCurrentStatus(user);
        }
    }

    private void setupAdapter() {
        if (adapter == null) {
            adapter = new FlexibleAdapter<>(userItems, getActivity(), false);

            User userEntity;

            for (User userItem : userManager.getUsers().blockingGet()) {
                userEntity = userItem;
                Log.d(TAG, "---------------------");
                Log.d(TAG, "userEntity.getUserId() " + userEntity.getUserId());
                Log.d(TAG, "userEntity.getCurrent() " + userEntity.getCurrent());
                Log.d(TAG, "---------------------");

                if (!userEntity.getCurrent()) {
                    String userId;
                    if (userEntity.getUserId() != null) {
                        userId = userEntity.getUserId();
                    } else {
                        userId = userEntity.getUsername();
                    }

                    User finalUserEntity = userEntity;
                    invitationsRepository.fetchInvitations(userItem)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                                disposable = d;
                            }

                            @Override
                            public void onNext(InvitationsModel invitationsModel) {
                                addAccountToSwitcherList(
                                    userId,
                                    finalUserEntity,
                                    invitationsModel.getInvitations().size()
                                );
                            }

                            @Override
                            public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                                Log.e(TAG, "Failed to fetch invitations", e);
                                addAccountToSwitcherList(
                                    userId,
                                    finalUserEntity,
                                    0
                                );
                            }

                            @Override
                            public void onComplete() {
                                // no actions atm
                            }
                        });
                }
            }
        }
    }

    private void addAccountToSwitcherList(
        String userId,
        User finalUserEntity,
        int actionsRequiredCount
    ) {
        Participant participant;
        participant = new Participant();
        participant.setActorType(Participant.ActorType.USERS);
        participant.setActorId(userId);
        participant.setDisplayName(finalUserEntity.getDisplayName());
        userItems.add(
            new AdvancedUserItem(
                participant,
                finalUserEntity,
                null,
                viewThemeUtils,
                actionsRequiredCount
            ));
        adapter.addListener(onSwitchItemClickListener);
        adapter.addListener(onSwitchItemLongClickListener);
        adapter.updateDataSet(userItems, false);
    }

    private void setupListeners() {
        // Creating listeners for quick-actions
        binding.currentAccount.getRoot().setOnClickListener(v -> dismiss());

        binding.addAccount.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ServerSelectionActivity.class);
            intent.putExtra(ADD_ADDITIONAL_ACCOUNT, true);
            startActivity(intent);
            dismiss();
        });
        binding.manageSettings.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), SettingsActivity.class);
            startActivity(intent);
            dismiss();
        });


        binding.setStatus.setOnClickListener(v -> {
            dismiss();

            if (status != null && getActivity() != null) {
                SetStatusDialogFragment setStatusDialog = SetStatusDialogFragment.newInstance(status);
                setStatusDialog.show(getActivity().getSupportFragmentManager(), "fragment_set_status");
            } else {
                Log.w(TAG, "status was null");
            }
        });
    }

    private void themeViews() {
        viewThemeUtils.platform.themeDialog(binding.getRoot());
        viewThemeUtils.platform.themeDialogDivider(binding.divider);

        viewThemeUtils.material.colorMaterialTextButton(binding.setStatus);
        viewThemeUtils.dialog.colorDialogMenuText(binding.setStatus);
        viewThemeUtils.material.colorMaterialTextButton(binding.addAccount);
        viewThemeUtils.dialog.colorDialogMenuText(binding.addAccount);
        viewThemeUtils.material.colorMaterialTextButton(binding.manageSettings);
        viewThemeUtils.dialog.colorDialogMenuText(binding.manageSettings);
    }

    private void loadCurrentStatus(User user) {
        String credentials = ApiUtils.getCredentials(user.getUsername(), user.getToken());

        if (CapabilitiesUtil.isUserStatusAvailable(userManager.getCurrentUser().blockingGet())) {
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
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        binding = null;
    }

    private final FlexibleAdapter.OnItemClickListener onSwitchItemClickListener =
        new FlexibleAdapter.OnItemClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if (userItems.size() > position) {
                    User user = (userItems.get(position)).user;

                    if (userManager.setUserAsActive(user).blockingGet()) {
                        cookieManager.getCookieStore().removeAll();

                        Intent intent = new Intent(getContext(), ConversationsListActivity.class);
                        // TODO: might be better with FLAG_ACTIVITY_SINGLE_TOP instead than FLAG_ACTIVITY_CLEAR_TOP to
                        // have a smoother transition. However the handling in onNewIntent() in
                        // ConversationListActivity must be improved for this.
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);

                        dismiss();
                    }
                }

                return true;
            }
        };

        private final FlexibleAdapter.OnItemLongClickListener onSwitchItemLongClickListener =
            position -> {
                // do nothing. OnItemLongClickListener is necessary anyway so the activity won't handle the event
            };

    private void drawStatus() {
        float size = DisplayUtils.convertDpToPixel(STATUS_SIZE_IN_DP, getContext());
        binding.currentAccount.ticker.setBackground(null);
        StatusDrawable drawable = new StatusDrawable(
            status.getStatus(),
            status.getIcon(),
            size,
            0,
            getContext());
        viewThemeUtils.talk.themeStatusDrawable(binding.currentAccount.ticker.getContext(), drawable);
        binding.currentAccount.ticker.setImageDrawable(drawable);
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
