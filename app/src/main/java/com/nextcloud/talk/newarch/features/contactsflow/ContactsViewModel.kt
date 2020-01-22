package com.nextcloud.talk.newarch.features.contactsflow

import android.app.Application
import android.util.Log
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
    val searchQuery = MutableLiveData<String?>(null)
    var conversationToken: String? = null

    fun loadContacts() {
        getContactsUseCase.invoke(viewModelScope, parametersOf(globalService.currentUserLiveData.value, searchQuery.value, conversationToken), object :
                UseCaseResponse<List<Participant>> {
            override suspend fun onSuccess(result: List<Participant>) {
                val sortPriority = mapOf("users" to 3, "groups" to 2, "emails" to 1, "circles" to 0)
                contactsLiveData.postValue(result.sortedWith(Comparator { o1, o2 ->
                    sortPriority[o2.source]?.let { sortPriority[o1.source]?.compareTo(it) }
                    0
                }))
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                // handle errors here
            }
        })
    }
}
