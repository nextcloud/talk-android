/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.ui.dialog

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.ConversationsListController
import com.nextcloud.talk.controllers.bottomsheet.ConversationOperationEnum
import com.nextcloud.talk.controllers.bottomsheet.ConversationOperationEnum.OPS_CODE_ADD_FAVORITE
import com.nextcloud.talk.controllers.bottomsheet.ConversationOperationEnum.OPS_CODE_CHANGE_PASSWORD
import com.nextcloud.talk.controllers.bottomsheet.ConversationOperationEnum.OPS_CODE_CLEAR_PASSWORD
import com.nextcloud.talk.controllers.bottomsheet.ConversationOperationEnum.OPS_CODE_MAKE_PRIVATE
import com.nextcloud.talk.controllers.bottomsheet.ConversationOperationEnum.OPS_CODE_MAKE_PUBLIC
import com.nextcloud.talk.controllers.bottomsheet.ConversationOperationEnum.OPS_CODE_MARK_AS_READ
import com.nextcloud.talk.controllers.bottomsheet.ConversationOperationEnum.OPS_CODE_REMOVE_FAVORITE
import com.nextcloud.talk.controllers.bottomsheet.ConversationOperationEnum.OPS_CODE_RENAME_ROOM
import com.nextcloud.talk.controllers.bottomsheet.ConversationOperationEnum.OPS_CODE_SET_PASSWORD
import com.nextcloud.talk.controllers.bottomsheet.EntryMenuController
import com.nextcloud.talk.controllers.bottomsheet.OperationsMenuController
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.DialogConversationOperationsBinding
import com.nextcloud.talk.jobs.LeaveConversationWorker
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.Mimetype.TEXT_PLAIN
import com.nextcloud.talk.utils.ShareUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_OPERATION_CODE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import org.parceler.Parcels
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ConversationsListBottomDialog(
    val activity: Activity,
    val controller: ConversationsListController,
    val currentUser: User,
    val conversation: Conversation
) : BottomSheetDialog(activity) {

    private var dialogRouter: Router? = null

    private lateinit var binding: DialogConversationOperationsBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var userManager: UserManager

    init {
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogConversationOperationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        viewThemeUtils.themeDialog(binding.root)
        initHeaderDescription()
        initItemsVisibility()
        initClickListeners()
    }

    private fun initHeaderDescription() {
        if (!TextUtils.isEmpty(conversation.displayName)) {
            binding.conversationOperationHeader.text = conversation.displayName
        } else if (!TextUtils.isEmpty(conversation.name)) {
            binding.conversationOperationHeader.text = conversation.name
        }
    }

    private fun initItemsVisibility() {
        val hasFavoritesCapability = CapabilitiesUtilNew.hasSpreedFeatureCapability(currentUser, "favorites")
        val canModerate = conversation.canModerate(currentUser)

        binding.conversationOperationRemoveFavorite.visibility = setVisibleIf(
            hasFavoritesCapability && conversation.favorite
        )
        binding.conversationOperationAddFavorite.visibility = setVisibleIf(
            hasFavoritesCapability && !conversation.favorite
        )

        binding.conversationOperationMarkAsRead.visibility = setVisibleIf(
            conversation.unreadMessages > 0 && CapabilitiesUtilNew.canSetChatReadMarker(currentUser)
        )

        binding.conversationOperationRename.visibility = setVisibleIf(
            conversation.isNameEditable(currentUser)
        )

        binding.conversationOperationMakePublic.visibility = setVisibleIf(
            canModerate && !conversation.isPublic
        )

        binding.conversationOperationChangePassword.visibility = setVisibleIf(
            canModerate && conversation.hasPassword && conversation.isPublic
        )

        binding.conversationOperationClearPassword.visibility = setVisibleIf(
            canModerate && conversation.hasPassword && conversation.isPublic
        )

        binding.conversationOperationSetPassword.visibility = setVisibleIf(
            canModerate && !conversation.hasPassword && conversation.isPublic
        )

        binding.conversationOperationDelete.visibility = setVisibleIf(
            canModerate
        )

        binding.conversationOperationShareLink.visibility = setVisibleIf(
            conversation.isPublic
        )

        binding.conversationOperationMakePrivate.visibility = setVisibleIf(
            conversation.isPublic && canModerate
        )

        binding.conversationOperationLeave.visibility = setVisibleIf(
            conversation.canLeave() &&
                // leaving is by api not possible for the last user with moderator permissions.
                // for now, hide this option for all moderators.
                !conversation.canModerate(currentUser)
        )
    }

    private fun setVisibleIf(boolean: Boolean): Int {
        return if (boolean) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun initClickListeners() {
        binding.conversationOperationAddFavorite.setOnClickListener {
            executeOperationsMenuController(OPS_CODE_ADD_FAVORITE)
        }

        binding.conversationOperationRemoveFavorite.setOnClickListener {
            executeOperationsMenuController(OPS_CODE_REMOVE_FAVORITE)
        }

        binding.conversationOperationLeave.setOnClickListener {
            val dataBuilder = Data.Builder()
            dataBuilder.putString(KEY_ROOM_TOKEN, conversation.token)
            dataBuilder.putLong(KEY_INTERNAL_USER_ID, currentUser.id!!)
            val data = dataBuilder.build()

            val leaveConversationWorker =
                OneTimeWorkRequest.Builder(LeaveConversationWorker::class.java).setInputData(
                    data
                ).build()
            WorkManager.getInstance().enqueue(leaveConversationWorker)

            dismiss()
        }

        binding.conversationOperationDelete.setOnClickListener {
            if (!TextUtils.isEmpty(conversation.token)) {
                val bundle = Bundle()
                bundle.putLong(KEY_INTERNAL_USER_ID, currentUser.id!!)
                bundle.putParcelable(KEY_ROOM, Parcels.wrap(conversation))

                controller.openLovelyDialogWithIdAndBundle(
                    ConversationsListController.ID_DELETE_CONVERSATION_DIALOG,
                    bundle
                )
            }

            dismiss()
        }

        binding.conversationOperationMakePublic.setOnClickListener {
            executeOperationsMenuController(OPS_CODE_MAKE_PUBLIC)
        }

        binding.conversationOperationMakePrivate.setOnClickListener {
            executeOperationsMenuController(OPS_CODE_MAKE_PRIVATE)
        }

        binding.conversationOperationChangePassword.setOnClickListener {
            executeEntryMenuController(OPS_CODE_CHANGE_PASSWORD)
        }

        binding.conversationOperationClearPassword.setOnClickListener {
            executeOperationsMenuController(OPS_CODE_CLEAR_PASSWORD)
        }

        binding.conversationOperationSetPassword.setOnClickListener {
            executeEntryMenuController(OPS_CODE_SET_PASSWORD)
        }

        binding.conversationOperationRename.setOnClickListener {
            executeEntryMenuController(OPS_CODE_RENAME_ROOM)
        }

        binding.conversationOperationMarkAsRead.setOnClickListener {
            executeOperationsMenuController(OPS_CODE_MARK_AS_READ)
        }

        binding.conversationOperationShareLink.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = TEXT_PLAIN
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    String.format(
                        activity.resources.getString(R.string.nc_share_subject),
                        activity.resources.getString(R.string.nc_app_product_name)
                    )
                )
                // password should not be shared!!
                putExtra(
                    Intent.EXTRA_TEXT,
                    ShareUtils.getStringForIntent(activity, null, userManager, conversation)
                )
            }

            val shareIntent = Intent.createChooser(sendIntent, null)
            activity.startActivity(shareIntent)

            dismiss()
        }
    }

    private fun executeOperationsMenuController(operation: ConversationOperationEnum) {
        val bundle = Bundle()
        bundle.putParcelable(KEY_ROOM, Parcels.wrap(conversation))
        bundle.putSerializable(KEY_OPERATION_CODE, operation)

        binding.operationItemsLayout.visibility = View.GONE

        dialogRouter = Conductor.attachRouter(activity, binding.root, null)

        dialogRouter!!.pushController(
            RouterTransaction.with(OperationsMenuController(bundle))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )

        controller.fetchData()
    }

    private fun executeEntryMenuController(operation: ConversationOperationEnum) {
        val bundle = Bundle()
        bundle.putParcelable(KEY_ROOM, Parcels.wrap(conversation))
        bundle.putSerializable(KEY_OPERATION_CODE, operation)

        binding.operationItemsLayout.visibility = View.GONE

        dialogRouter = Conductor.attachRouter(activity, binding.root, null)

        dialogRouter!!.pushController(

            // TODO refresh conversation list after EntryMenuController finished (throw event? / pass controller
            //  into EntryMenuController to execute fetch data... ?!)
            // for example if you set a password, the dialog items should be refreshed for the next time you open it
            // without to manually have to refresh the conversations list
            // also see BottomSheetLockEvent ??

            RouterTransaction.with(EntryMenuController(bundle))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    companion object {
        private const val TAG = "ConversationOperationDialog"
    }
}
