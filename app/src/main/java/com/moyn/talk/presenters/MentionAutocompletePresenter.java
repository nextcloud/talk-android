/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.moyn.talk.presenters;

import android.content.Context;
import android.view.View;

import com.moyn.talk.adapters.items.MentionAutocompleteItem;
import com.moyn.talk.api.NcApi;
import com.moyn.talk.application.NextcloudTalkApplication;
import com.moyn.talk.models.database.UserEntity;
import com.moyn.talk.models.json.mention.Mention;
import com.moyn.talk.models.json.mention.MentionOverall;
import com.moyn.talk.utils.ApiUtils;
import com.moyn.talk.utils.database.user.UserUtils;
import com.otaliastudios.autocomplete.RecyclerViewPresenter;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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
    @Inject
    NcApi ncApi;
    @Inject
    UserUtils userUtils;
    private UserEntity currentUser;
    private FlexibleAdapter<AbstractFlexibleItem> adapter;
    private Context context;

    private String roomToken;

    private List<AbstractFlexibleItem> abstractFlexibleItemList = new ArrayList<>();

    public MentionAutocompletePresenter(Context context) {
        super(context);
        this.context = context;
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        currentUser = userUtils.getCurrentUser();
    }

    public MentionAutocompletePresenter(Context context, String roomToken) {
        super(context);
        this.roomToken = roomToken;
        this.context = context;
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        currentUser = userUtils.getCurrentUser();
    }

    @Override
    protected RecyclerView.Adapter instantiateAdapter() {
        adapter = new FlexibleAdapter<>(abstractFlexibleItemList, context, false);
        adapter.addListener(this);
        return adapter;
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
        ncApi.getMentionAutocompleteSuggestions(
                ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                ApiUtils.getUrlForMentionSuggestions(apiVersion, currentUser.getBaseUrl(), roomToken),
                queryString, 5)
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
                                                mention.getId(),
                                                mention.getLabel(),
                                                mention.getSource(),
                                                currentUser,
                                                context));
                            }

                            if (adapter.getItemCount() != 0) {
                                adapter.clear();
                            }

                            adapter.updateDataSet(internalAbstractFlexibleItemList);
                        }
                    }

                    @Override
                    public void onError(@NotNull Throwable e) {
                        adapter.clear();
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
