package com.nextcloud.talk.newarch.features.contactsflow

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.isVisible
import coil.api.load
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.services.GlobalService
import com.nextcloud.talk.newarch.utils.ElementPayload
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.utils.ApiUtils
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import kotlinx.android.synthetic.main.rv_item_contact.view.*
import org.koin.core.KoinComponent
import org.koin.core.inject

open class ContactPresenter(context: Context, onElementClick: ((Page, Holder, Element<Participant>) -> Unit)?) : Presenter<Participant>(context, onElementClick), KoinComponent {
    private val globalService: GlobalService by inject()

    override val elementTypes: Collection<Int>
        get() = listOf(0)

    override fun onCreate(parent: ViewGroup, elementType: Int): Holder {
        return Holder(getLayoutInflater().inflate(R.layout.rv_item_contact, parent, false))
    }

    override fun onBind(page: Page, holder: Holder, element: Element<Participant>, payloads: List<Any>) {
        super.onBind(page, holder, element, payloads)

        val participant = element.data
        val user = globalService.currentUserLiveData.value

        holder.itemView.checkedImageView.isVisible = element.data?.selected == true

        if (!payloads.contains(ElementPayload.SELECTION_TOGGLE)) {
            participant?.displayName?.let {
                holder.itemView.name_text.text = it
            } ?: run {
                holder.itemView.name_text.text = context.getString(R.string.nc_guest)
            }

            when (participant?.source) {
                "users" -> {
                    when (participant.type) {
                        Participant.ParticipantType.GUEST, Participant.ParticipantType.GUEST_AS_MODERATOR, Participant.ParticipantType.USER_FOLLOWING_LINK -> {
                            holder.itemView.avatarImageView.load(ApiUtils.getUrlForAvatarWithNameForGuests(user?.baseUrl, participant.userId, R.dimen.avatar_size)) {
                                user?.getCredentials()?.let { addHeader("Authorization", it) }
                            }
                        }
                        else -> {
                            holder.itemView.avatarImageView.load(ApiUtils.getUrlForAvatarWithName(user?.baseUrl, participant.userId, R.dimen.avatar_size)) {
                                user?.getCredentials()?.let { addHeader("Authorization", it) }
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
    }
}