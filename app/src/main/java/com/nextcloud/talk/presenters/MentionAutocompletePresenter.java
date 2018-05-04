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

package com.nextcloud.talk.presenters;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.nextcloud.talk.adapters.items.MentionAutocompleteItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.mention.Mention;
import com.nextcloud.talk.models.json.mention.MentionOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.otaliastudios.autocomplete.RecyclerViewPresenter;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

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

    private FlexibleAdapter<AbstractFlexibleItem> adapter;
    private Context context;

    private String roomToken;
    private List<AbstractFlexibleItem> userItemList = new ArrayList<>();

    public MentionAutocompletePresenter(Context context) {
        super(context);
        this.context = context;
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
    }

    public MentionAutocompletePresenter(Context context, String roomToken) {
        super(context);
        this.roomToken = roomToken;
        this.context = context;
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
    }

    @Override
    protected RecyclerView.Adapter instantiateAdapter() {
        adapter = new FlexibleAdapter<>(userItemList, context, true);
        adapter.addListener(this);
        return adapter;
    }

    @Override
    protected void onQuery(@Nullable CharSequence query) {
        if (query != null && query.length() > 0) {
            UserEntity currentUser = userUtils.getCurrentUser();

            adapter.setFilter(query.toString());
            ncApi.getMentionAutocompleteSuggestions(ApiUtils.getCredentials(currentUser.getUserId(), currentUser
                            .getToken()), ApiUtils.getUrlForMentionSuggestions(currentUser.getBaseUrl(), roomToken),
                    query.toString(), null)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .retry(3)
                    .subscribe(new Observer<MentionOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(MentionOverall mentionOverall) {
                            List<Mention> mentionsList = mentionOverall.getOcs().getData();
                            List<AbstractFlexibleItem> internalUserItemList = new ArrayList<>();
                            if (mentionsList.size() == 0 ||
                                    (mentionsList.size() == 1 && mentionsList.get(0).getId().equals(query.toString()))) {
                                userItemList = new ArrayList<>();
                                adapter.notifyDataSetChanged();
                            } else {
                                for (Mention mention : mentionsList) {
                                    internalUserItemList.add(new MentionAutocompleteItem(mention.getId(), mention
                                            .getLabel(), currentUser));
                                }
                                userItemList = internalUserItemList;
                                adapter.updateDataSet(internalUserItemList, true);
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                            userItemList = new ArrayList<>();
                            adapter.updateDataSet(new ArrayList<>(), false);
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        } else {
            userItemList = new ArrayList<>();
            adapter.updateDataSet(new ArrayList<>(), false);
        }
    }

    @Override
    public boolean onItemClick(View view, int position) {
        Mention mention = new Mention();
        MentionAutocompleteItem mentionAutocompleteItem = (MentionAutocompleteItem) userItemList.get(position);
        mention.setId(mentionAutocompleteItem.getUserId());
        mention.setLabel(mentionAutocompleteItem.getDisplayName());
        mention.setSource("users");
        dispatchClick(mention);
        return true;
    }
}
