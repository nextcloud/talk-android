/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.polls.adapters.PollResultHeaderItem
import com.nextcloud.talk.polls.adapters.PollResultItem
import com.nextcloud.talk.polls.adapters.PollResultVoterItem
import com.nextcloud.talk.polls.adapters.PollResultVotersOverviewItem
import com.nextcloud.talk.polls.model.Poll
import io.reactivex.disposables.Disposable
import javax.inject.Inject

class PollResultsViewModel @Inject constructor() : ViewModel() {

    sealed interface ViewState
    object InitialState : ViewState

    private var _poll: Poll? = null
    val poll: Poll?
        get() = _poll

    private var itemsOverviewList: ArrayList<PollResultItem> = ArrayList()
    private var itemsDetailsList: ArrayList<PollResultItem> = ArrayList()

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

        var oneVoteInPercent = 0
        if (poll.numVoters != 0) {
            oneVoteInPercent = HUNDRED / poll.numVoters
        }

        poll.options?.forEachIndexed { index, option ->
            val votersAmountForThisOption = getVotersAmountForOption(poll, index)
            val optionsPercent = oneVoteInPercent * votersAmountForThisOption

            val pollResultHeaderItem = PollResultHeaderItem(
                option,
                optionsPercent,
                isOptionSelfVoted(poll, index)
            )
            itemsOverviewList.add(pollResultHeaderItem)
            itemsDetailsList.add(pollResultHeaderItem)

            val voters = poll.details?.filter { it.optionId == index }

            if (!voters.isNullOrEmpty()) {
                itemsOverviewList.add(PollResultVotersOverviewItem(voters))
            }

            if (!voters.isNullOrEmpty()) {
                voters.forEach {
                    itemsDetailsList.add(PollResultVoterItem(it))
                }
            }
        }

        _items.value = itemsOverviewList
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

    private fun isOptionSelfVoted(poll: Poll, index: Int): Boolean = poll.votedSelf?.contains(index) == true

    fun toggleDetails() {
        if (_items.value?.containsAll(itemsDetailsList) == true) {
            _items.value = itemsOverviewList
        } else {
            _items.value = itemsDetailsList
        }
    }

    companion object {
        private val TAG = PollResultsViewModel::class.java.simpleName
        private const val HUNDRED = 100
    }
}
