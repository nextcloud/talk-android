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
package com.nextcloud.talk.presenters

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.models.json.mention.Mention
import com.nextcloud.talk.models.json.mention.MentionOverall
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import com.otaliastudios.autocomplete.RecyclerViewPresenter
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*

class MentionAutocompletePresenter : RecyclerViewPresenter<Mention?>, FlexibleAdapter.OnItemClickListener, KoinComponent {
    val ncApi: NcApi by inject()
    val usersRepository: UsersRepository by inject()
    private var currentUser: UserNgEntity? = null
    private var adapter: FlexibleAdapter<AbstractFlexibleItem<*>>? = null
    private var internalContext: Context
    private var roomToken: String? = null
    private val abstractFlexibleItemList: List<AbstractFlexibleItem<*>> = ArrayList()

    constructor(context: Context) : super(context) {
        this.internalContext = context
        currentUser = usersRepository.getActiveUser()
    }

    constructor(context: Context, roomToken: String?) : super(context) {
        this.roomToken = roomToken
        this.internalContext = context
        GlobalScope.launch {
            currentUser = usersRepository.getActiveUser()
        }
    }

    override fun instantiateAdapter(): RecyclerView.Adapter<*> {
        adapter = FlexibleAdapter(abstractFlexibleItemList, context, false)
        adapter!!.addListener(this)
        return adapter!!
    }

    override fun onQuery(query: CharSequence?) {
        val queryString: String
        queryString = if (query != null && query.length > 1) {
            query.subSequence(1, query.length).toString()
        } else {
            ""
        }
        adapter!!.setFilter(queryString)
        ncApi.getMentionAutocompleteSuggestions(
                        currentUser!!.getCredentials(), ApiUtils.getUrlForMentionSuggestions(currentUser!!.baseUrl, roomToken),
                        queryString, 5)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(3)
                .subscribe(object : io.reactivex.Observer<MentionOverall> {
                    override fun onSubscribe(d: Disposable) {}
                    override fun onNext(mentionOverall: MentionOverall) {
                        val mentionsList: List<Mention> = mentionOverall.ocs.data
                        if (mentionsList.isEmpty()) {
                            adapter!!.clear()
                        } else {
                            val internalAbstractFlexibleItemList: MutableList<AbstractFlexibleItem<*>> = ArrayList()
                            for (mention in mentionsList) {
                                internalAbstractFlexibleItemList.add(
                                        MentionAutocompleteItem(mention.id,
                                                mention.label, mention.source,
                                                currentUser!!, internalContext))
                            }
                            if (adapter!!.itemCount != 0) {
                                adapter!!.clear()
                            }
                            adapter!!.updateDataSet(internalAbstractFlexibleItemList)
                        }
                    }

                    override fun onError(e: Throwable) {
                        adapter!!.clear()
                    }

                    override fun onComplete() {}
                })
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        val mention = Mention()
        val mentionAutocompleteItem = adapter!!.getItem(position) as MentionAutocompleteItem?
        if (mentionAutocompleteItem != null) {
            mention.id = mentionAutocompleteItem.objectId
            mention.label = mentionAutocompleteItem.displayName
            mention.source = mentionAutocompleteItem.source
            dispatchClick(mention)
        }
        return true
    }
}