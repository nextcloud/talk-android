/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.features.contactsflow.contacts

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.isVisible
import coil.api.load
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.features.contactsflow.ParticipantElement
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.services.GlobalService
import com.nextcloud.talk.newarch.utils.ElementPayload
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.utils.ApiUtils
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import com.otaliastudios.elements.extensions.FooterSource
import com.otaliastudios.elements.extensions.HeaderSource
import kotlinx.android.synthetic.main.rv_item_contact.view.*
import kotlinx.android.synthetic.main.rv_item_contact.view.avatarImageView
import kotlinx.android.synthetic.main.rv_item_contact.view.participantNameTextView
import kotlinx.android.synthetic.main.rv_item_contact_selected.view.*
import kotlinx.android.synthetic.main.rv_item_participant_rv_footer.view.*
import kotlinx.android.synthetic.main.rv_item_title_header.view.*
import org.koin.core.KoinComponent
import org.koin.core.inject

open class ContactPresenter<T : Any>(context: Context, onElementClick: ((Page, Holder, Element<T>) -> Unit)?) : Presenter<T>(context, onElementClick), KoinComponent {
    private val globalService: GlobalService by inject()

    override val elementTypes: Collection<Int>
        get() = listOf(ParticipantElementType.PARTICIPANT.ordinal, ParticipantElementType.PARTICIPANT_SELECTED.ordinal, ParticipantElementType.PARTICIPANT_HEADER.ordinal, ParticipantElementType.PARTICIPANT_FOOTER.ordinal, ParticipantElementType.PARTICIPANT_NEW_GROUP.ordinal, ParticipantElementType.PARTICIPANT_JOIN_VIA_LINK.ordinal)

    override fun onCreate(parent: ViewGroup, elementType: Int): Holder {
        return when (elementType) {
            ParticipantElementType.PARTICIPANT.ordinal -> {
                Holder(getLayoutInflater().inflate(R.layout.rv_item_contact, parent, false))
            }
            ParticipantElementType.PARTICIPANT_SELECTED.ordinal -> {
                Holder(getLayoutInflater().inflate(R.layout.rv_item_contact_selected, parent, false))
            }
            ParticipantElementType.PARTICIPANT_HEADER.ordinal -> {
                Holder(getLayoutInflater().inflate(R.layout.rv_item_title_header, parent, false))
            }
            ParticipantElementType.PARTICIPANT_FOOTER.ordinal -> {
                Holder(getLayoutInflater().inflate(R.layout.rv_item_participant_rv_footer, parent, false))
            }
            else -> {
                // for join via link and new group
                Holder(getLayoutInflater().inflate(R.layout.rv_item_contact, parent, false))
            }
        }
    }

    override fun onBind(page: Page, holder: Holder, element: Element<T>, payloads: List<Any>) {
        super.onBind(page, holder, element, payloads)

        if (element.type == ParticipantElementType.PARTICIPANT.ordinal || element.type == ParticipantElementType.PARTICIPANT_SELECTED.ordinal) {
            val participantElement = element.data as ParticipantElement
            val participant = participantElement.data as Participant
            val user = globalService.currentUserLiveData.value

            holder.itemView.checkedImageView?.isVisible = participant.selected == true

            if (!payloads.contains(ElementPayload.SELECTION_TOGGLE)) {
                participant.displayName?.let {
                    if (element.type == ParticipantElementType.PARTICIPANT_SELECTED.ordinal) {
                        holder.itemView.participantNameTextView.text = it.substringBefore(" ", it)
                    } else {
                        holder.itemView.participantNameTextView.text = it

                    }
                } ?: run {
                    holder.itemView.participantNameTextView.text = context.getString(R.string.nc_guest)
                }

                holder.itemView.clearImageView?.load(Images().getImageWithBackground(context, R.drawable.ic_baseline_clear_24, R.color.bg_selected_participant_clear_icon, R.color.white))

                when (participant.source) {
                    "users" -> {
                        val conversation = Conversation()
                        conversation.type = Conversation.ConversationType.ONE_TO_ONE_CONVERSATION

                        when (participant.type) {
                            Participant.ParticipantType.GUEST, Participant.ParticipantType.GUEST_AS_MODERATOR, Participant.ParticipantType.USER_FOLLOWING_LINK -> {
                                holder.itemView.avatarImageView.load(ApiUtils.getUrlForAvatarWithNameForGuests(user?.baseUrl, participant.userId, R.dimen.avatar_size)) {
                                    user?.getCredentials()?.let { addHeader("Authorization", it) }
                                    fallback(Images().getImageForConversation(context, conversation, true))
                                }
                            }
                            else -> {
                                holder.itemView.avatarImageView.load(ApiUtils.getUrlForAvatarWithName(user?.baseUrl, participant.userId, R.dimen.avatar_size)) {
                                    user?.getCredentials()?.let { addHeader("Authorization", it) }
                                    fallback(Images().getImageForConversation(context, conversation, true))
                                }
                            }
                        }
                    }
                    "groups", "circles" -> {
                        holder.itemView.avatarImageView.load(Images().getImageWithBackground(context, R.drawable.ic_people_group_white_24px))
                    }
                    "emails" -> {
                        holder.itemView.avatarImageView.load(Images().getImageWithBackground(context, R.drawable.ic_baseline_email_24))
                    }
                    else -> {
                    }
                }
            }
        } else if (element.type == ParticipantElementType.PARTICIPANT_HEADER.ordinal) {
            holder.itemView.titleTextView.text = (element.data as HeaderSource.Data<*, *>).header.toString()
        } else if (element.type == ParticipantElementType.PARTICIPANT_FOOTER.ordinal) {
            holder.itemView.messageTextView.text = (element.data as FooterSource.Data<*, *>).footer.toString()
        } else if (element.type == ParticipantElementType.PARTICIPANT_NEW_GROUP.ordinal) {
            val pairData = (element.data as ParticipantElement).data as Pair<*, *>
            holder.itemView.participantNameTextView.text = pairData.first as CharSequence
            holder.itemView.avatarImageView.load(Images().getImageWithBackground(context, pairData.second as Int))
        } else {
            val pairData = element.data as Pair<*, *>
            holder.itemView.participantNameTextView.text = pairData.first as CharSequence
            holder.itemView.avatarImageView.load(Images().getImageWithBackground(context, pairData.second as Int))
        }
    }
}