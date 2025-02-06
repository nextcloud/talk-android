/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.nextcloud.talk.adapters.items.MentionAutocompleteItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.json.mention.Mention;
import com.nextcloud.talk.models.json.mention.MentionOverall;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew;
import com.otaliastudios.autocomplete.RecyclerViewPresenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import autodagger.AutoInjector;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@AutoInjector(NextcloudTalkApplication.class)
public class MentionAutocompletePresenter extends RecyclerViewPresenter<Mention> implements FlexibleAdapter.OnItemClickListener {
    private static final String TAG = "MentionAutocompletePresenter";

    @Inject
    NcApi ncApi;

    @Inject
    UserManager userManager;

    @Inject
    CurrentUserProviderNew currentUserProvider;

    @Inject
    ViewThemeUtils viewThemeUtils;

    private User currentUser;
    private FlexibleAdapter<AbstractFlexibleItem> adapter;
    private Context context;

    private String roomToken;
    private int chatApiVersion;

    private List<AbstractFlexibleItem> abstractFlexibleItemList = new ArrayList<>();

    public MentionAutocompletePresenter(Context context) {
        super(context);
        this.context = context;
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        currentUser = currentUserProvider.getCurrentUser().blockingGet();
    }

    public MentionAutocompletePresenter(Context context, String roomToken, int chatApiVersion) {
        super(context);
        this.roomToken = roomToken;
        this.context = context;
        this.chatApiVersion = chatApiVersion;
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        currentUser = currentUserProvider.getCurrentUser().blockingGet();
    }

    @Override
    protected RecyclerView.Adapter instantiateAdapter() {
        adapter = new FlexibleAdapter<>(abstractFlexibleItemList, context, false);
        adapter.addListener(this);
        return adapter;
    }

    @Override
    protected PopupDimensions getPopupDimensions() {
        PopupDimensions popupDimensions = new PopupDimensions();
        popupDimensions.width = ViewGroup.LayoutParams.MATCH_PARENT;
        popupDimensions.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        return popupDimensions;
    }

    @Override
    protected void onQuery(@Nullable CharSequence query) {

        String queryString;
        if (query != null && query.length() > 1) {
            queryString = String.valueOf(query.subSequence(1, query.length()));
        } else {
            queryString = "";
        }

        adapter.setFilter(queryString);

        Map<String, String> queryMap = new HashMap<>();
        queryMap.put("includeStatus", "true");

        ncApi.getMentionAutocompleteSuggestions(
                ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                ApiUtils.getUrlForMentionSuggestions(chatApiVersion, currentUser.getBaseUrl(), roomToken),
                queryString, 5, queryMap)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .retry(3)
            .subscribe(new Observer<MentionOverall>() {
                @Override
                public void onSubscribe(@NonNull Disposable d) {
                    // no actions atm
                }

                @Override
                public void onNext(@NonNull MentionOverall mentionOverall) {
                    if (mentionOverall.getOcs() != null) {
                        List<Mention> mentionsList = mentionOverall.getOcs().getData();

                        if (mentionsList != null) {

                            if (mentionsList.isEmpty()) {
                                adapter.clear();
                            } else {
                                List<AbstractFlexibleItem> internalAbstractFlexibleItemList =
                                    new ArrayList<>(mentionsList.size());
                                for (Mention mention : mentionsList) {
                                    internalAbstractFlexibleItemList.add(
                                        new MentionAutocompleteItem(
                                            mention,
                                            currentUser,
                                            context,
                                            roomToken,
                                            viewThemeUtils));
                                }

                                if (adapter.getItemCount() != 0) {
                                    adapter.clear();
                                }

                                adapter.updateDataSet(internalAbstractFlexibleItemList);
                            }
                        }
                    }
                }

                @SuppressLint("LongLogTag")
                @Override
                public void onError(@NonNull Throwable e) {
                    adapter.clear();
                    Log.e(TAG, "failed to get MentionAutocompleteSuggestions", e);
                }

                @Override
                public void onComplete() {
                    // no actions atm
                }
            });
    }

    @Override
    public boolean onItemClick(View view, int position) {
        Mention mention = new Mention();
        MentionAutocompleteItem mentionAutocompleteItem = (MentionAutocompleteItem) adapter.getItem(position);
        if (mentionAutocompleteItem != null) {
            String mentionId = mentionAutocompleteItem.mentionId;
            if (mentionId != null) {
                mention.setMentionId(mentionId);
            }
            mention.setId(mentionAutocompleteItem.objectId);
            mention.setLabel(mentionAutocompleteItem.displayName);
            mention.setSource(mentionAutocompleteItem.source);
            mention.setRoomToken(mentionAutocompleteItem.roomToken);
            dispatchClick(mention);
        }
        return true;
    }
}
