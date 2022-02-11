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
import com.nextcloud.talk.controllers.bottomsheet.EntryMenuController
import com.nextcloud.talk.controllers.bottomsheet.OperationsMenuController
import com.nextcloud.talk.databinding.DialogConversationOperationsBinding
import com.nextcloud.talk.jobs.LeaveConversationWorker
import com.nextcloud.talk.models.database.CapabilitiesUtil
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.ShareUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_OPERATION_CODE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.database.user.UserUtils
import org.parceler.Parcels
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ConversationOperationDialog(
    val activity: Activity,
    val controller: ConversationsListController,
    val currentUser: UserEntity,
    val conversation: Conversation
) : BottomSheetDialog(activity) {

    private var dialogRouter: Router? = null

    private lateinit var binding: DialogConversationOperationsBinding

    @Inject
    @JvmField
    var ncApi: NcApi? = null

    @Inject
    @JvmField
    var userUtils: UserUtils? = null

    init {
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogConversationOperationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        initHeaderDescription()
        initItemsVisibility()
        initClickListeners()
    }

    private fun initHeaderDescription() {
        if (!TextUtils.isEmpty(conversation.getDisplayName())) {
            binding.conversationOperationHeader.text = conversation.getDisplayName()
        } else if (!TextUtils.isEmpty(conversation.getName())) {
            binding.conversationOperationHeader.text = conversation.getName()
        }
    }

    private fun initItemsVisibility() {
        val hasFavoritesCapability = CapabilitiesUtil.hasSpreedFeatureCapability(currentUser, "favorites")
        val canModerate = conversation.canModerate(currentUser)

        binding.conversationOperationRemoveFavorite.visibility = setVisibleIf(
            hasFavoritesCapability && conversation.isFavorite()
        )
        binding.conversationOperationAddFavorite.visibility = setVisibleIf(
            hasFavoritesCapability && !conversation.isFavorite()
        )

        binding.conversationOperationMarkAsRead.visibility = setVisibleIf(
            conversation.unreadMessages > 0 && CapabilitiesUtil.canSetChatReadMarker(currentUser)
        )

        binding.conversationOperationRename.visibility = setVisibleIf(
            conversation.isNameEditable(currentUser)
        )

        binding.conversationOperationMakePublic.visibility = setVisibleIf(
            canModerate && !conversation.isPublic
        )

        binding.conversationOperationChangePassword.visibility = setVisibleIf(
            canModerate && conversation.isHasPassword && conversation.isPublic
        )

        binding.conversationOperationClearPassword.visibility = setVisibleIf(
            canModerate && conversation.isHasPassword && conversation.isPublic
        )

        binding.conversationOperationSetPassword.visibility = setVisibleIf(
            canModerate && !conversation.isHasPassword && conversation.isPublic
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
            conversation.canLeave()
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
            executeOperationsMenuController(98)
        }

        binding.conversationOperationRemoveFavorite.setOnClickListener {
            executeOperationsMenuController(97)
        }

        binding.conversationOperationLeave.setOnClickListener {
            val dataBuilder = Data.Builder()
            dataBuilder.putString(KEY_ROOM_TOKEN, conversation.getToken())
            dataBuilder.putLong(KEY_INTERNAL_USER_ID, currentUser.id)
            val data = dataBuilder.build()

            val leaveConversationWorker =
                OneTimeWorkRequest.Builder(LeaveConversationWorker::class.java).setInputData(
                    data
                ).build()
            WorkManager.getInstance().enqueue(leaveConversationWorker)

            dismiss()
        }

        binding.conversationOperationDelete.setOnClickListener {
            if (!TextUtils.isEmpty(conversation.getToken())) {
                val bundle = Bundle()
                bundle.putLong(KEY_INTERNAL_USER_ID, currentUser.id)
                bundle.putParcelable(KEY_ROOM, Parcels.wrap(conversation))

                controller.openLovelyDialogWithIdAndBundle(
                    ConversationsListController.ID_DELETE_CONVERSATION_DIALOG,
                    bundle
                )
            }

            dismiss()
        }

        binding.conversationOperationMakePublic.setOnClickListener {
            executeOperationsMenuController(3)
        }

        binding.conversationOperationMakePrivate.setOnClickListener {
            executeOperationsMenuController(8)
        }

        binding.conversationOperationChangePassword.setOnClickListener {
            executeEntryMenuController(4)
        }

        binding.conversationOperationClearPassword.setOnClickListener {
            executeOperationsMenuController(5)
        }

        binding.conversationOperationSetPassword.setOnClickListener {
            executeEntryMenuController(6)
        }

        binding.conversationOperationRename.setOnClickListener {
            executeEntryMenuController(2)
        }

        binding.conversationOperationShareLink.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
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
                    ShareUtils.getStringForIntent(activity, null, userUtils, conversation)
                )
            }

            val shareIntent = Intent.createChooser(sendIntent, null)
            activity.startActivity(shareIntent)

            dismiss()
        }
    }

    private fun executeOperationsMenuController(operationCode: Int) {
        val bundle = Bundle()
        bundle.putParcelable(KEY_ROOM, Parcels.wrap(conversation))
        bundle.putInt(KEY_OPERATION_CODE, operationCode)

        binding.operationItemsLayout.visibility = View.GONE

        dialogRouter = Conductor.attachRouter(activity, binding.root, null)

        dialogRouter!!.pushController(
            RouterTransaction.with(OperationsMenuController(bundle))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )

        controller.fetchData()
    }

    private fun executeEntryMenuController(operationCode: Int) {
        val bundle = Bundle()
        bundle.putParcelable(KEY_ROOM, Parcels.wrap(conversation))
        bundle.putInt(KEY_OPERATION_CODE, operationCode)

        binding.operationItemsLayout.visibility = View.GONE

        dialogRouter = Conductor.attachRouter(activity, binding.root, null)

        dialogRouter!!.pushController(

            // TODO: refresh conversation list after EntryMenuController finished (throw event? / pass controller
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
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    companion object {
        private const val TAG = "ConversationOperationDialog"
    }
}