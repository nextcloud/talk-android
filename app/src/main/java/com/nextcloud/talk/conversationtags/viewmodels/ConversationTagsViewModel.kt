/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationtags.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.conversationtags.data.ConversationTagsRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.tags.ConversationTag
import com.nextcloud.talk.models.json.tags.ConversationTagErrorOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

@Suppress("TooManyFunctions")
class ConversationTagsViewModel @Inject constructor(
    private val conversationTagsRepository: ConversationTagsRepository,
    private val repository: OfflineConversationsRepository,
    private val currentUserProvider: CurrentUserProviderOld
) : ViewModel() {

    private val currentUser: User = currentUserProvider.currentUser.blockingGet()
    private val credentials = ApiUtils.getCredentials(currentUser.username, currentUser.token) ?: ""

    private val _conversationTagsFlow = MutableStateFlow<List<ConversationTag>>(emptyList())
    val conversationTagsFlow: StateFlow<List<ConversationTag>> = _conversationTagsFlow.asStateFlow()

    sealed class TagActionUiState {
        data object None : TagActionUiState()
        data object Success : TagActionUiState()
        data class Error(val errorType: String?) : TagActionUiState()
    }

    private val _tagActionState = MutableStateFlow<TagActionUiState>(TagActionUiState.None)
    val tagActionState: StateFlow<TagActionUiState> = _tagActionState.asStateFlow()

    fun resetTagActionState() {
        _tagActionState.value = TagActionUiState.None
    }

    private val _conversationForTagAssignment = MutableStateFlow<ConversationModel?>(null)
    val conversationForTagAssignment: StateFlow<ConversationModel?> = _conversationForTagAssignment.asStateFlow()

    fun setConversationForTagAssignment(model: ConversationModel?) {
        _conversationForTagAssignment.value = model
    }

    private val _showManageTagsSheet = MutableStateFlow(false)
    val showManageTagsSheet: StateFlow<Boolean> = _showManageTagsSheet.asStateFlow()

    fun setShowManageTagsSheet(show: Boolean) {
        _showManageTagsSheet.value = show
    }

    /** Fetch conversation tags for the current user, gated behind the `conversation-tags` capability. */
    @Suppress("Detekt.TooGenericExceptionCaught")
    fun loadConversationTags() {
        if (!hasSpreedFeatureCapability(currentUser.capabilities?.spreedCapability, SpreedFeatures.CONVERSATION_TAGS)) {
            return
        }
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    conversationTagsRepository.getTags(credentials, currentUser.baseUrl!!)
                }
                _conversationTagsFlow.value = response.ocs?.data.toDisplayTags()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load conversation tags", e)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun createConversationTag(name: String) {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    conversationTagsRepository.createTag(credentials, currentUser.baseUrl!!, name)
                }
                response.ocs?.data?.let { tag ->
                    _conversationTagsFlow.value = (_conversationTagsFlow.value + tag).toDisplayTags()
                }
                _tagActionState.value = TagActionUiState.Success
            } catch (e: Exception) {
                _tagActionState.value = TagActionUiState.Error(extractTagErrorType(e))
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun renameConversationTag(tagId: String, name: String) {
        if (!isCustomTag(tagId)) return
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    conversationTagsRepository.updateTag(credentials, currentUser.baseUrl!!, tagId, name)
                }
                response.ocs?.data?.let { updated ->
                    _conversationTagsFlow.value =
                        _conversationTagsFlow.value.map { if (it.id == updated.id) updated else it }
                }
                _tagActionState.value = TagActionUiState.Success
            } catch (e: Exception) {
                _tagActionState.value = TagActionUiState.Error(extractTagErrorType(e))
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun deleteConversationTag(tagId: String) {
        if (!isCustomTag(tagId)) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    conversationTagsRepository.deleteTag(credentials, currentUser.baseUrl!!, tagId)
                }
                _conversationTagsFlow.value = _conversationTagsFlow.value.filter { it.id != tagId }
                _tagActionState.value = TagActionUiState.Success
            } catch (e: Exception) {
                _tagActionState.value = TagActionUiState.Error(extractTagErrorType(e))
            }
        }
    }

    /** [orderedIds] includes the built-in Favorites tag's real id — the server tracks its sortOrder too. */
    @Suppress("Detekt.TooGenericExceptionCaught")
    fun reorderConversationTags(orderedIds: List<String>) {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    conversationTagsRepository.reorderTags(credentials, currentUser.baseUrl!!, orderedIds)
                }
                response.ocs?.data?.let { tags ->
                    _conversationTagsFlow.value = tags.toDisplayTags()
                }
                _tagActionState.value = TagActionUiState.Success
            } catch (e: Exception) {
                _tagActionState.value = TagActionUiState.Error(extractTagErrorType(e))
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun assignConversationTags(conversation: ConversationModel, tagIds: List<String>) {
        val original = conversation.copy()
        val optimistic = conversation.copy(tagIds = tagIds)
        replaceConversationForTagAssignment(conversation.token, optimistic)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateConversationLocallyAndEmit(currentUser, optimistic)
            }
            try {
                withContext(Dispatchers.IO) {
                    conversationTagsRepository.assignTags(
                        credentials,
                        currentUser.baseUrl!!,
                        conversation.token,
                        tagIds
                    )
                }
            } catch (e: Exception) {
                replaceConversationForTagAssignment(conversation.token, original)
                withContext(Dispatchers.IO) {
                    repository.updateConversationLocallyAndEmit(currentUser, original)
                }
                Log.e(TAG, "Failed to assign conversation tags", e)
            }
        }
    }

    /** [conversationForTagAssignment] is a snapshot, not fed from the live room list — keep it in sync. */
    private fun replaceConversationForTagAssignment(token: String, replacement: ConversationModel) {
        if (_conversationForTagAssignment.value?.token == token) {
            _conversationForTagAssignment.value = replacement
        }
    }

    private fun extractTagErrorType(exception: Exception): String? {
        val httpException = exception as? HttpException ?: return null
        return runCatching {
            val errorBody = httpException.response()?.errorBody()?.string()
            errorBody?.let { LoganSquare.parse(it, ConversationTagErrorOverall::class.java).ocs?.data?.error }
        }.getOrNull()
    }

    private fun isCustomTag(tagId: String): Boolean =
        _conversationTagsFlow.value.firstOrNull { it.id == tagId }?.type == ConversationTag.TYPE_CUSTOM

    /** Drops the built-in "Other" tag (not surfaced in this UI) and sorts the rest by sortOrder. */
    private fun List<ConversationTag>?.toDisplayTags(): List<ConversationTag> =
        this?.filter { it.type != ConversationTag.TYPE_OTHER }?.sortedBy { it.sortOrder } ?: emptyList()

    companion object {
        private val TAG = ConversationTagsViewModel::class.simpleName
    }
}
