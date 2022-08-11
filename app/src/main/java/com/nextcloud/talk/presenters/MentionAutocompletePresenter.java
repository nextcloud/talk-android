/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2021-2022 Andy Scherzinger <info@andy-scherzinger.de>
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
import com.otaliastudios.autocomplete.RecyclerViewPresenter;

import org.jetbrains.annotations.NotNull;

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
    ViewThemeUtils viewThemeUtils;

    private User currentUser;
    private FlexibleAdapter<AbstractFlexibleItem> adapter;
    private Context context;

    private String roomToken;

    private List<AbstractFlexibleItem> abstractFlexibleItemList = new ArrayList<>();

    public MentionAutocompletePresenter(Context context) {
        super(context);
        this.context = context;
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        currentUser = userManager.getCurrentUser().blockingGet();
    }

    public MentionAutocompletePresenter(Context context, String roomToken) {
        super(context);
        this.roomToken = roomToken;
        this.context = context;
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        currentUser = userManager.getCurrentUser().blockingGet();
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

        int apiVersion = ApiUtils.getChatApiVersion(currentUser, new int[] {1});

        adapter.setFilter(queryString);

        Map<String, String> queryMap = new HashMap<>();
        queryMap.put("includeStatus", "true");

        ncApi.getMentionAutocompleteSuggestions(
                ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                ApiUtils.getUrlForMentionSuggestions(apiVersion, currentUser.getBaseUrl(), roomToken),
                queryString, 5, queryMap)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(3)
                .subscribe(new Observer<MentionOverall>() {
                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                        // no actions atm
                    }

                    @Override
                    public void onNext(@NotNull MentionOverall mentionOverall) {
                        List<Mention> mentionsList = mentionOverall.getOcs().getData();

                        if (mentionsList.size() == 0) {
                            adapter.clear();
                        } else {
                            List<AbstractFlexibleItem> internalAbstractFlexibleItemList = new ArrayList<>();
                            for (Mention mention : mentionsList) {
                                internalAbstractFlexibleItemList.add(
                                        new MentionAutocompleteItem(
                                                mention,
                                                currentUser,
                                                context,
                                                viewThemeUtils));
                            }

                            if (adapter.getItemCount() != 0) {
                                adapter.clear();
                            }

                            adapter.updateDataSet(internalAbstractFlexibleItemList);
                        }
                    }

                    @SuppressLint("LongLogTag")
                    @Override
                    public void onError(@NotNull Throwable e) {
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
            mention.setId(mentionAutocompleteItem.getObjectId());
            mention.setLabel(mentionAutocompleteItem.getDisplayName());
            mention.setSource(mentionAutocompleteItem.getSource());
            dispatchClick(mention);
        }
        return true;
    }
}
