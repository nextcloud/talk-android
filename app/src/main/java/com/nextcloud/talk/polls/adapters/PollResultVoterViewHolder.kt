package com.nextcloud.talk.polls.adapters

import android.annotation.SuppressLint
import android.text.TextUtils
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.interfaces.DraweeController
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.PollResultVoterItemBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.polls.model.PollDetails
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils

class PollResultVoterViewHolder(
    private val user: UserEntity,
    override val binding: PollResultVoterItemBinding
) : PollResultViewHolder(binding) {

    @SuppressLint("SetTextI18n")
    override fun bind(pollResultItem: PollResultItem, clickListener: PollResultItemClickListener) {
        val item = pollResultItem as PollResultVoterItem
        binding.pollVoterName.text = item.details.actorDisplayName
        binding.pollVoterAvatar.controller = getAvatarDraweeController(item.details)
    }

    private fun getAvatarDraweeController(pollDetail: PollDetails): DraweeController? {
        if (pollDetail.actorType == "guests") {
            var displayName = NextcloudTalkApplication.sharedApplication?.resources?.getString(R.string.nc_guest)
            if (!TextUtils.isEmpty(pollDetail.actorDisplayName)) {
                displayName = pollDetail.actorDisplayName!!
            }
            return Fresco.newDraweeControllerBuilder()
                .setAutoPlayAnimations(true)
                .setImageRequest(
                    DisplayUtils.getImageRequestForUrl(
                        ApiUtils.getUrlForGuestAvatar(
                            user.baseUrl,
                            displayName,
                            false
                        ),
                        null
                    )
                )
                .build()
        } else if (pollDetail.actorType == "users") {
            return Fresco.newDraweeControllerBuilder()
                .setAutoPlayAnimations(true)
                .setImageRequest(
                    DisplayUtils.getImageRequestForUrl(
                        ApiUtils.getUrlForAvatar(
                            user.baseUrl,
                            pollDetail.actorId,
                            false
                        ),
                        null
                    )
                )
                .build()
        }
        return null
    }
}
