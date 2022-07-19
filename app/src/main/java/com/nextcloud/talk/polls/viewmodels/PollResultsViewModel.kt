/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * @author Marcel Hibbe
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.polls.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.polls.adapters.PollResultHeaderItem
import com.nextcloud.talk.polls.adapters.PollResultItem
import com.nextcloud.talk.polls.adapters.PollResultVoterItem
import com.nextcloud.talk.polls.model.Poll
import io.reactivex.disposables.Disposable
import javax.inject.Inject

class PollResultsViewModel @Inject constructor() : ViewModel() {

    sealed interface ViewState
    object InitialState : ViewState

    private var _poll: Poll? = null
    val poll: Poll?
        get() = _poll

    private var _unfilteredItems: ArrayList<PollResultItem> = ArrayList()

    private var _items: MutableLiveData<ArrayList<PollResultItem>?> = MutableLiveData<ArrayList<PollResultItem>?>()
    val items: MutableLiveData<ArrayList<PollResultItem>?>
        get() = _items

    private var disposable: Disposable? = null

    override fun onCleared() {
        super.onCleared()
        disposable?.dispose()
    }

    fun setPoll(poll: Poll) {
        _poll = poll
        initPollResults(_poll!!)
    }

    private fun initPollResults(poll: Poll) {
        _items.value = ArrayList()

        val oneVoteInPercent = HUNDRED / poll.numVoters

        poll.options?.forEachIndexed { index, option ->
            val votersAmountForThisOption = getVotersAmountForOption(poll, index)
            val optionsPercent = oneVoteInPercent * votersAmountForThisOption

            val pollResultHeaderItem = PollResultHeaderItem(
                option,
                optionsPercent,
                isOptionSelfVoted(poll, index)
            )
            addToItems(pollResultHeaderItem)

            val voters = poll.details?.filter { it.optionId == index }
            if (!voters.isNullOrEmpty()) {
                voters.forEach {
                    addToItems(PollResultVoterItem(it))
                }
            }
        }

        _unfilteredItems = _items.value?.let { ArrayList(it) }!!
    }

    private fun addToItems(pollResultItem: PollResultItem) {
        val tempList = _items.value
        tempList!!.add(pollResultItem)
        _items.value = tempList
    }

    private fun getVotersAmountForOption(poll: Poll, index: Int): Int {
        var votersAmountForThisOption: Int? = 0
        if (poll.details != null) {
            votersAmountForThisOption = poll.details.filter { it.optionId == index }.size
        } else if (poll.votes != null) {
            votersAmountForThisOption = poll.votes.filter { it.key.toInt() == index }[index.toString()]
            if (votersAmountForThisOption == null) {
                votersAmountForThisOption = 0
            }
        }
        return votersAmountForThisOption!!
    }

    private fun isOptionSelfVoted(poll: Poll, index: Int): Boolean {
        return poll.votedSelf?.contains(index) == true
    }

    fun filterItems() {
        if (_items.value?.containsAll(_unfilteredItems) == true) {
            val filteredList = _items.value?.filter { it.getViewType() == PollResultHeaderItem.VIEW_TYPE } as
                MutableList<PollResultItem>

            _items.value = ArrayList(filteredList)
        } else {
            _items.value = _unfilteredItems
        }
    }

    companion object {
        private val TAG = PollResultsViewModel::class.java.simpleName
        private const val HUNDRED = 100
    }
}
