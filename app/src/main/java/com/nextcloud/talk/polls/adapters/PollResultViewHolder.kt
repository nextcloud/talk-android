package com.nextcloud.talk.polls.adapters

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.generic.RoundingParams
import com.facebook.drawee.interfaces.DraweeController
import com.facebook.drawee.view.SimpleDraweeView
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.PollResultItemBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.polls.model.PollDetails
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils

class PollResultViewHolder(
    private val user: UserEntity,
    private val binding: PollResultItemBinding
) : RecyclerView.ViewHolder(binding.root) {

    @SuppressLint("SetTextI18n")
    fun bind(pollResultItem: PollResultItem, clickListener: PollResultItemClickListener) {
        binding.root.setOnClickListener { clickListener.onClick(pollResultItem) }

        // binding.root.setOnClickListener { clickListener.onClick(pollResultItem) }

        binding.pollOptionText.text = pollResultItem.name
        binding.pollOptionPercentText.text = "${pollResultItem.percent}%"

        if (pollResultItem.selfVoted) {
            binding.pollOptionText.setTypeface(null, Typeface.BOLD)
            binding.pollOptionPercentText.setTypeface(null, Typeface.BOLD)
        }

        binding.pollOptionBar.progress = pollResultItem.percent

        if (!pollResultItem.voters.isNullOrEmpty()) {
            binding.pollOptionDetail.visibility = View.VISIBLE

            val lp = LinearLayout.LayoutParams(
                90,
                70
            )

            pollResultItem.voters.forEach {
                val avatar = SimpleDraweeView(binding.root.context)
                avatar.layoutParams = lp

                val roundingParams = RoundingParams.fromCornersRadius(5f)
                roundingParams.roundAsCircle = true

                avatar.hierarchy.roundingParams = roundingParams
                avatar.controller = getAvatarDraweeController(it)

                binding.pollOptionDetail.addView(avatar)
            }
        } else {
            binding.pollOptionDetail.visibility = View.GONE
        }
    }

    private fun getAvatarDraweeController(pollDetail: PollDetails): DraweeController? {
        if (pollDetail.actorType == "guests") {
            var displayName = NextcloudTalkApplication.sharedApplication?.resources?.getString(R.string.nc_guest)
            if (!TextUtils.isEmpty(pollDetail.actorDisplayName)) {
                displayName = pollDetail.actorDisplayName!!
            }
            val draweeController: DraweeController = Fresco.newDraweeControllerBuilder()
                // .setOldController(binding.avatar.controller)
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
            return draweeController
        } else if (pollDetail.actorType == "users") {
            val draweeController: DraweeController = Fresco.newDraweeControllerBuilder()
                // .setOldController(binding.avatar.controller)
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
            return draweeController
        }
        return null
    }
}
