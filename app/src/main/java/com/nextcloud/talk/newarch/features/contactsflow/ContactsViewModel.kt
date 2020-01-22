package com.nextcloud.talk.newarch.features.contactsflow

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.domain.usecases.GetContactsUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.features.conversationslist.ConversationsListView
import com.nextcloud.talk.newarch.services.GlobalService
import org.koin.core.parameter.parametersOf

class ContactsViewModel constructor(
        application: Application,
        private val getContactsUseCase: GetContactsUseCase,
        val globalService: GlobalService
) : BaseViewModel<ConversationsListView>(application) {
    val contactsLiveData = MutableLiveData<List<Participant>>()
    private var searchQuery: String? = null
    var conversationToken: String? = null

    fun setSearchQuery(query: String?) {
        searchQuery = query
        loadContacts()
    }

    fun loadContacts() {
        getContactsUseCase.invoke(viewModelScope, parametersOf(globalService.currentUserLiveData.value, searchQuery, conversationToken), object :
                UseCaseResponse<List<Participant>> {
            override suspend fun onSuccess(result: List<Participant>) {
                val sortPriority = mapOf("users" to 0, "groups" to 1, "emails" to 2, "circles" to 0)
                val typeComparator = Comparator<Participant> { o1, o2 ->
                    sortPriority[o2.source]?.let { sortPriority[o1.source]?.compareTo(it) }
                    0
                }

                val sortedList = result.sortedWith(compareBy({
                    sortPriority[it.source]
                }, {
                    it.displayName.toLowerCase()
                }))

                contactsLiveData.postValue(sortedList)
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                // handle errors here
            }
        })
    }
}
