/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus juliuslinus1@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.SeekBar
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.emoji2.widget.EmojiTextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import autodagger.AutoInjector
import coil.Coil.imageLoader
import coil.load
import coil.request.ImageRequest
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.callbacks.MentionAutocompleteCallback
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.chat.viewmodels.MessageInputViewModel
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.databinding.FragmentMessageInputBinding
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import com.nextcloud.talk.models.json.chat.ChatUtils
import com.nextcloud.talk.models.json.mention.Mention
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage
import com.nextcloud.talk.presenters.MentionAutocompletePresenter
import com.nextcloud.talk.ui.MicInputCloud
import com.nextcloud.talk.ui.dialog.AttachmentDialog
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.CharPolicy
import com.nextcloud.talk.utils.EmojiTextInputEditText
import com.nextcloud.talk.utils.ImageEmojiEditText
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.message.MessageUtils
import com.nextcloud.talk.utils.text.Spans
import com.otaliastudios.autocomplete.Autocomplete
import com.vanniktech.emoji.EmojiPopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Objects
import javax.inject.Inject

@Suppress("LongParameterList", "TooManyFunctions", "LargeClass", "LongMethod")
@AutoInjector(NextcloudTalkApplication::class)
class MessageInputFragment : Fragment() {

    companion object {
        fun newInstance() = MessageInputFragment()
        private val TAG: String = MessageInputFragment::class.java.simpleName
        private const val TYPING_DURATION_TO_SEND_NEXT_TYPING_MESSAGE = 10000L
        private const val TYPING_INTERVAL_TO_SEND_NEXT_TYPING_MESSAGE = 1000L
        private const val TYPING_STARTED_SIGNALING_MESSAGE_TYPE = "startedTyping"
        private const val TYPING_STOPPED_SIGNALING_MESSAGE_TYPE = "stoppedTyping"
        private const val QUOTED_MESSAGE_IMAGE_MAX_HEIGHT = 96f
        private const val MENTION_AUTO_COMPLETE_ELEVATION = 6f
        private const val MINIMUM_VOICE_RECORD_DURATION: Int = 1000
        private const val ANIMATION_DURATION: Long = 750
        private const val VOICE_RECORD_CANCEL_SLIDER_X: Int = -150
        private const val VOICE_RECORD_LOCK_THRESHOLD: Float = 100f
        private const val INCREMENT = 8f
        private const val CURSOR_KEY = "_cursor"
        private const val CONNECTION_ESTABLISHED_ANIM_DURATION: Long = 3000
        private const val FULLY_OPAQUE: Float = 1.0f
        private const val FULLY_TRANSPARENT: Float = 0.0f
    }

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var messageUtils: MessageUtils

    lateinit var binding: FragmentMessageInputBinding
    private lateinit var conversationInternalId: String
    private var typedWhileTypingTimerIsRunning: Boolean = false
    private var typingTimer: CountDownTimer? = null
    private lateinit var chatActivity: ChatActivity
    private var emojiPopup: EmojiPopup? = null
    private var mentionAutocomplete: Autocomplete<*>? = null
    private var xcounter = 0f
    private var ycounter = 0f
    private var collapsed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        conversationInternalId = arguments?.getString(ChatActivity.CONVERSATION_INTERNAL_ID).orEmpty()
        if (conversationInternalId.isEmpty()) {
            Log.e(TAG, "internalId for conversation passed to MessageInputFragment is empty")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMessageInputBinding.inflate(inflater)
        chatActivity = requireActivity() as ChatActivity
        themeMessageInputView()
        initMessageInputView()
        initSmileyKeyboardToggler()
        setupMentionAutocomplete()
        initVoiceRecordButton()
        restoreState()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (mentionAutocomplete != null && mentionAutocomplete!!.isPopupShowing) {
            mentionAutocomplete?.dismissPopup()
        }
        clearEditUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initObservers()

        binding.fragmentCreateThreadView.createThreadView.findViewById<EmojiTextInputEditText>(
            R.id
                .createThread
        ).doAfterTextChanged { text ->
            val threadTitle = text.toString()
            chatActivity.chatViewModel.messageDraft.threadTitle = threadTitle
        }
    }

    private fun initObservers() {
        Log.d(TAG, "LifeCyclerOwner is: ${viewLifecycleOwner.lifecycle}")
        chatActivity.messageInputViewModel.getReplyChatMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                chatActivity.chatViewModel.messageDraft.quotedMessageText = message.text
                chatActivity.chatViewModel.messageDraft.quotedDisplayName = message.actorDisplayName
                chatActivity.chatViewModel.messageDraft.quotedImageUrl = message.imageUrl
                chatActivity.chatViewModel.messageDraft.quotedJsonId = message.jsonMessageId
                replyToMessage(
                    message.text,
                    message.actorDisplayName,
                    message.imageUrl
                )
            } ?: clearReplyUi()
        }

        chatActivity.messageInputViewModel.getEditChatMessage.observe(viewLifecycleOwner) { message ->
            message?.let { setEditUI(it as ChatMessage) }
        }

        chatActivity.messageInputViewModel.createThreadViewState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MessageInputViewModel.CreateThreadStartState ->
                    binding.fragmentCreateThreadView.createThreadView.visibility = View.GONE

                is MessageInputViewModel.CreateThreadEditState -> {
                    binding.fragmentCreateThreadView.createThreadView.visibility = View.VISIBLE
                    binding.fragmentCreateThreadView.createThreadView
                        .findViewById<EmojiTextInputEditText>(R.id.createThread)?.setText(
                            chatActivity.chatViewModel.messageDraft.threadTitle
                        )
                }

                else -> {}
            }
            initVoiceRecordButton()
        }

        chatActivity.chatViewModel.leaveRoomViewState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ChatViewModel.LeaveRoomSuccessState -> sendStopTypingMessage()
                else -> {}
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            var wasOnline: Boolean
            networkMonitor.isOnline
                .onEach { isOnline ->
                    wasOnline = !binding.fragmentConnectionLost.isShown
                    val connectionGained = (!wasOnline && isOnline)
                    Log.d(TAG, "isOnline: $isOnline\nwasOnline: $wasOnline\nconnectionGained: $connectionGained")
                    if (connectionGained) {
                        chatActivity.messageInputViewModel.sendUnsentMessages(
                            chatActivity.conversationUser!!.getCredentials(),
                            ApiUtils.getUrlForChat(
                                chatActivity.chatApiVersion,
                                chatActivity.conversationUser!!.baseUrl!!,
                                chatActivity.roomToken
                            )
                        )
                    }
                    handleUI(isOnline, connectionGained)
                }.collect()
        }

        chatActivity.messageInputViewModel.callStartedFlow.observe(viewLifecycleOwner) {
            val (message, show) = it
            if (show) {
                binding.fragmentCallStarted.callAuthorChip.text = message.actorDisplayName
                binding.fragmentCallStarted.callAuthorChipSecondary.text = message.actorDisplayName
                val user = currentUserProvider.currentUser.blockingGet()
                val url: String = if (message.actorType == "guests" || message.actorType == "guest") {
                    ApiUtils.getUrlForGuestAvatar(user!!.baseUrl!!, message.actorDisplayName, true)
                } else {
                    ApiUtils.getUrlForAvatar(user!!.baseUrl!!, message.actorId, false)
                }

                val imageRequest: ImageRequest = ImageRequest.Builder(requireContext())
                    .data(url)
                    .crossfade(true)
                    .transformations(CircleCropTransformation())
                    .target(object : Target {
                        override fun onStart(placeholder: Drawable?) {
                            // unused atm
                        }

                        override fun onError(error: Drawable?) {
                            // unused atm
                        }

                        override fun onSuccess(result: Drawable) {
                            binding.fragmentCallStarted.callAuthorChip.chipIcon = result
                            binding.fragmentCallStarted.callAuthorChipSecondary.chipIcon = result
                        }
                    })
                    .build()

                imageLoader(requireContext()).enqueue(imageRequest)
                binding.fragmentCallStarted.root.visibility = View.VISIBLE
            } else {
                binding.fragmentCallStarted.root.visibility = View.GONE
            }
        }
    }

    private fun handleUI(isOnline: Boolean, connectionGained: Boolean) {
        if (isOnline) {
            if (connectionGained) {
                val animation: Animation = AlphaAnimation(FULLY_OPAQUE, FULLY_TRANSPARENT)
                animation.duration = CONNECTION_ESTABLISHED_ANIM_DURATION
                animation.interpolator = LinearInterpolator()
                binding.fragmentConnectionLost.setBackgroundColor(resources.getColor(R.color.hwSecurityGreen))
                binding.fragmentConnectionLost.text = getString(R.string.connection_established)
                binding.fragmentConnectionLost.startAnimation(animation)
                binding.fragmentConnectionLost.animation.setAnimationListener(object : AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {
                        // unused atm
                    }

                    override fun onAnimationEnd(animation: Animation?) {
                        binding.fragmentConnectionLost.visibility = View.GONE
                        binding.fragmentConnectionLost.setBackgroundColor(resources.getColor(R.color.hwSecurityRed))
                        binding.fragmentConnectionLost.text =
                            getString(R.string.connection_lost_sent_messages_are_queued)
                    }

                    override fun onAnimationRepeat(animation: Animation?) {
                        // unused atm
                    }
                })
            }

            binding.fragmentMessageInputView.attachmentButton.visibility = View.VISIBLE
            binding.fragmentMessageInputView.recordAudioButton.visibility =
                if (binding.fragmentMessageInputView.inputEditText.text.isEmpty()) View.VISIBLE else View.GONE
        } else {
            binding.fragmentMessageInputView.attachmentButton.visibility = View.INVISIBLE
            binding.fragmentMessageInputView.recordAudioButton.visibility = View.INVISIBLE
            binding.fragmentConnectionLost.clearAnimation()
            binding.fragmentConnectionLost.visibility = View.GONE
            binding.fragmentConnectionLost.setBackgroundColor(resources.getColor(R.color.hwSecurityRed))
            binding.fragmentConnectionLost.visibility = View.VISIBLE
        }
    }

    private fun restoreState() {
        CoroutineScope(Dispatchers.IO).launch {
            chatActivity.chatViewModel.updateMessageDraft()

            withContext(Dispatchers.Main) {
                val draft = chatActivity.chatViewModel.messageDraft
                binding.fragmentMessageInputView.messageInput.setText(draft.messageText)
                binding.fragmentMessageInputView.messageInput.setSelection(draft.messageCursor)

                if (draft.threadTitle?.isNotEmpty() == true) {
                    chatActivity.messageInputViewModel.startThreadCreation()
                }

                if (draft.messageText != "") {
                    binding.fragmentMessageInputView.messageInput.requestFocus()
                }

                if (isInReplyState()) {
                    replyToMessage(
                        chatActivity.chatViewModel.messageDraft.quotedMessageText,
                        chatActivity.chatViewModel.messageDraft.quotedDisplayName,
                        chatActivity.chatViewModel.messageDraft.quotedImageUrl
                    )
                }
            }
        }
    }

    private fun initMessageInputView() {
        if (!chatActivity.active) return

        val filters = arrayOfNulls<InputFilter>(1)
        val lengthFilter = CapabilitiesUtil.getMessageMaxLength(chatActivity.spreedCapabilities)

        binding.fragmentEditView.editMessageView.visibility = View.GONE
        binding.fragmentMessageInputView.setPadding(0, 0, 0, 0)

        filters[0] = InputFilter.LengthFilter(lengthFilter)
        binding.fragmentMessageInputView.inputEditText?.filters = filters

        binding.fragmentMessageInputView.inputEditText?.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // unused atm
            }

            @Suppress("Detekt.TooGenericExceptionCaught")
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                updateOwnTypingStatus(s)

                if (s.length >= lengthFilter) {
                    binding.fragmentMessageInputView.inputEditText?.error = String.format(
                        Objects.requireNonNull<Resources>(resources).getString(R.string.nc_limit_hit),
                        lengthFilter.toString()
                    )
                } else {
                    binding.fragmentMessageInputView.inputEditText?.error = null
                }

                val editable = binding.fragmentMessageInputView.inputEditText?.editableText

                if (editable != null && binding.fragmentMessageInputView.inputEditText != null) {
                    val mentionSpans = editable.getSpans(
                        0,
                        binding.fragmentMessageInputView.inputEditText!!.length(),
                        Spans.MentionChipSpan::class.java
                    )
                    var mentionSpan: Spans.MentionChipSpan
                    for (i in mentionSpans.indices) {
                        mentionSpan = mentionSpans[i]
                        if (start >= editable.getSpanStart(mentionSpan) &&
                            start < editable.getSpanEnd(mentionSpan)
                        ) {
                            if (editable.subSequence(
                                    editable.getSpanStart(mentionSpan),
                                    editable.getSpanEnd(mentionSpan)
                                ).toString().trim() != mentionSpan.label
                            ) {
                                editable.removeSpan(mentionSpan)
                            }
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable) {
                val cursor = binding.fragmentMessageInputView.messageInput.selectionStart
                val text = binding.fragmentMessageInputView.messageInput.text.toString()
                chatActivity.chatViewModel.messageDraft.messageCursor = cursor
                chatActivity.chatViewModel.messageDraft.messageText = text
            }
        })

        // Image keyboard support
        // See: https://developer.android.com/guide/topics/text/image-keyboard

        (binding.fragmentMessageInputView.inputEditText as ImageEmojiEditText).onCommitContentListener = {
            chatActivity.chatViewModel.uploadFile(
                fileUri = it.toString(),
                isVoiceMessage = false,
                caption = "",
                roomToken = chatActivity.roomToken,
                replyToMessageId = chatActivity.getReplyToMessageId(),
                displayName = chatActivity.currentConversation?.displayName!!
            )
        }

        if (chatActivity.sharedText.isNotEmpty()) {
            binding.fragmentMessageInputView.inputEditText?.setText(chatActivity.sharedText)
        }

        binding.fragmentMessageInputView.setAttachmentsListener {
            AttachmentDialog(requireActivity(), requireActivity() as ChatActivity).show()
        }

        binding.fragmentMessageInputView.attachmentButton.setOnLongClickListener {
            chatActivity.showGalleryPicker()
            true
        }

        binding.fragmentMessageInputView.button?.setOnClickListener {
            submitMessage(false)
        }

        binding.fragmentMessageInputView.editMessageButton.setOnClickListener {
            val editable = binding.fragmentMessageInputView.inputEditText!!.editableText
            replaceMentionChipSpans(editable)
            val inputEditText = editable.toString()

            val message = chatActivity.messageInputViewModel.getEditChatMessage.value as ChatMessage
            if (message.message!!.trim() != inputEditText.trim()) {
                if (message.messageParameters != null) {
                    val editedMessage = messageUtils.processEditMessageParameters(
                        message.messageParameters!!,
                        message,
                        inputEditText
                    )
                    editMessageAPI(message, editedMessage.toString())
                } else {
                    editMessageAPI(message, inputEditText.toString())
                }
            }
            clearEditUI()
        }
        binding.fragmentEditView.clearEdit.setOnClickListener {
            clearEditUI()
        }
        binding.fragmentCreateThreadView.abortCreateThread.setOnClickListener {
            cancelCreateThread()
        }

        if (CapabilitiesUtil.hasSpreedFeatureCapability(chatActivity.spreedCapabilities, SpreedFeatures.SILENT_SEND)) {
            binding.fragmentMessageInputView.button?.setOnLongClickListener {
                showSendButtonMenu()
                true
            }
        }

        binding.fragmentMessageInputView.button?.contentDescription =
            resources.getString(R.string.nc_description_send_message_button)

        binding.fragmentCallStarted.joinAudioCall.setOnClickListener {
            chatActivity.joinAudioCall()
        }

        binding.fragmentCallStarted.joinVideoCall.setOnClickListener {
            chatActivity.joinVideoCall()
        }

        binding.fragmentCallStarted.callStartedCloseBtn.setOnClickListener {
            collapsed = !collapsed
            binding.fragmentCallStarted.callAuthorLayout.visibility = if (collapsed) View.GONE else View.VISIBLE
            binding.fragmentCallStarted.callBtnLayout.visibility = if (collapsed) View.GONE else View.VISIBLE
            binding.fragmentCallStarted.callAuthorChipSecondary.visibility = if (collapsed) View.VISIBLE else View.GONE
            binding.fragmentCallStarted.callStartedSecondaryText.visibility = if (collapsed) View.VISIBLE else View.GONE
            setDropDown(collapsed)
        }

        binding.fragmentMessageInputView.findViewById<ImageButton>(R.id.cancelReplyButton)?.setOnClickListener {
            cancelReply()
        }
    }

    private fun setDropDown(collapsed: Boolean) {
        val drawable = if (collapsed) {
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_keyboard_arrow_up)
        } else {
            AppCompatResources.getDrawable(requireContext(), R.drawable.ic_keyboard_arrow_down)
        }

        binding.fragmentCallStarted.callStartedCloseBtn.setImageDrawable(drawable)
    }

    @Suppress("ClickableViewAccessibility", "CyclomaticComplexMethod", "LongMethod")
    private fun initVoiceRecordButton() {
        handleButtonsVisibility()

        binding.fragmentMessageInputView.inputEditText.doAfterTextChanged {
            handleButtonsVisibility()
        }

        var prevDx = 0f
        var voiceRecordStartTime = 0L
        var voiceRecordEndTime: Long
        binding.fragmentMessageInputView.recordAudioButton.setOnTouchListener { v, event ->
            v?.performClick()
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!chatActivity.isRecordAudioPermissionGranted()) {
                        chatActivity.requestRecordAudioPermissions()
                        return@setOnTouchListener true
                    }
                    if (!chatActivity.permissionUtil.isFilesPermissionGranted()) {
                        UploadAndShareFilesWorker.requestStoragePermission(chatActivity)
                        return@setOnTouchListener true
                    }

                    val base = SystemClock.elapsedRealtime()
                    voiceRecordStartTime = System.currentTimeMillis()
                    binding.fragmentMessageInputView.audioRecordDuration.base = base
                    chatActivity.messageInputViewModel.setRecordingTime(base)
                    binding.fragmentMessageInputView.audioRecordDuration.start()
                    chatActivity.chatViewModel.startAudioRecording(requireContext(), chatActivity.currentConversation!!)
                    showRecordAudioUi(true)
                }

                MotionEvent.ACTION_CANCEL -> {
                    Log.d(TAG, "ACTION_CANCEL")
                    if (chatActivity.chatViewModel.getVoiceRecordingInProgress.value == false ||
                        !chatActivity.isRecordAudioPermissionGranted()
                    ) {
                        return@setOnTouchListener true
                    }

                    showRecordAudioUi(false)
                    if (chatActivity.chatViewModel.getVoiceRecordingLocked.value != true) { // can also be null
                        chatActivity.chatViewModel.stopAndDiscardAudioRecording()
                    }
                }

                MotionEvent.ACTION_UP -> {
                    Log.d(TAG, "ACTION_UP")
                    if (chatActivity.chatViewModel.getVoiceRecordingInProgress.value == false ||
                        chatActivity.chatViewModel.getVoiceRecordingLocked.value == true ||
                        !chatActivity.isRecordAudioPermissionGranted()
                    ) {
                        return@setOnTouchListener false
                    }
                    showRecordAudioUi(false)

                    voiceRecordEndTime = System.currentTimeMillis()
                    val voiceRecordDuration = voiceRecordEndTime - voiceRecordStartTime
                    if (voiceRecordDuration < MINIMUM_VOICE_RECORD_DURATION) {
                        Snackbar.make(
                            binding.root,
                            requireContext().getString(R.string.nc_voice_message_hold_to_record_info),
                            Snackbar.LENGTH_SHORT
                        ).show()
                        chatActivity.chatViewModel.stopAndDiscardAudioRecording()
                        return@setOnTouchListener false
                    } else {
                        chatActivity.chatViewModel.stopAndSendAudioRecording(
                            roomToken = chatActivity.roomToken,
                            replyToMessageId = chatActivity.getReplyToMessageId(),
                            displayName = chatActivity.currentConversation!!.displayName
                        )
                    }
                    resetSlider()
                }

                MotionEvent.ACTION_MOVE -> {
                    if (chatActivity.chatViewModel.getVoiceRecordingInProgress.value == false ||
                        !chatActivity.isRecordAudioPermissionGranted()
                    ) {
                        return@setOnTouchListener false
                    }

                    if (event.x < VOICE_RECORD_CANCEL_SLIDER_X) {
                        chatActivity.chatViewModel.stopAndDiscardAudioRecording()
                        showRecordAudioUi(false)
                        resetSlider()
                        return@setOnTouchListener true
                    }
                    if (event.x < 0f) {
                        val dX = event.x
                        if (dX < prevDx) { // left
                            binding.fragmentMessageInputView.slideToCancelDescription.x -= INCREMENT
                            xcounter += INCREMENT
                        } else { // right
                            binding.fragmentMessageInputView.slideToCancelDescription.x += INCREMENT
                            xcounter -= INCREMENT
                        }

                        prevDx = dX
                    }

                    if (event.y < 0f) {
                        chatActivity.chatViewModel.postToRecordTouchObserver(INCREMENT)
                        ycounter += INCREMENT
                    }

                    if (ycounter >= VOICE_RECORD_LOCK_THRESHOLD) {
                        resetSlider()
                        binding.fragmentMessageInputView.recordAudioButton.isEnabled = false
                        chatActivity.chatViewModel.setVoiceRecordingLocked(true)
                        binding.fragmentMessageInputView.recordAudioButton.isEnabled = true
                    }
                }
            }
            v?.onTouchEvent(event) != false
        }
    }

    private fun handleButtonsVisibility() {
        fun View.setVisible(isVisible: Boolean) {
            visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        val isEditModeActive = binding.fragmentEditView.editMessageView.isVisible
        val isThreadCreateModeActive = binding.fragmentCreateThreadView.createThreadView.isVisible
        val inputContainsText = binding.fragmentMessageInputView.messageInput.text.isNotEmpty()

        binding.fragmentMessageInputView.apply {
            when {
                isEditModeActive -> {
                    messageSendButton.setVisible(false)
                    recordAudioButton.setVisible(false)
                }
                inputContainsText || isThreadCreateModeActive -> {
                    messageSendButton.setVisible(true)
                    recordAudioButton.setVisible(false)
                }
                else -> {
                    messageSendButton.setVisible(false)
                    recordAudioButton.setVisible(true)
                }
            }
        }
    }

    private fun resetSlider() {
        binding.fragmentMessageInputView.audioRecordDuration.stop()
        binding.fragmentMessageInputView.audioRecordDuration.clearAnimation()
        binding.fragmentMessageInputView.slideToCancelDescription.x += xcounter
        chatActivity.chatViewModel.postToRecordTouchObserver(-ycounter)
        xcounter = 0f
        ycounter = 0f
    }

    private fun setupMentionAutocomplete() {
        val elevation = MENTION_AUTO_COMPLETE_ELEVATION
        resources.let {
            val backgroundDrawable = it.getColor(R.color.bg_default, null).toDrawable()
            val presenter = MentionAutocompletePresenter(
                requireContext(),
                chatActivity.roomToken,
                chatActivity.chatApiVersion
            )
            val callback = MentionAutocompleteCallback(
                requireContext(),
                chatActivity.conversationUser!!,
                binding.fragmentMessageInputView.inputEditText,
                viewThemeUtils
            )

            if (mentionAutocomplete == null && binding.fragmentMessageInputView.inputEditText != null) {
                mentionAutocomplete =
                    Autocomplete.on<Mention>(binding.fragmentMessageInputView.inputEditText)
                        .with(elevation)
                        .with(backgroundDrawable)
                        .with(CharPolicy('@'))
                        .with(presenter)
                        .with(callback)
                        .build()
            }
        }
    }

    private fun showRecordAudioUi(show: Boolean) {
        if (show) {
            val animation: Animation = AlphaAnimation(FULLY_OPAQUE, FULLY_TRANSPARENT)
            animation.duration = ANIMATION_DURATION
            animation.interpolator = LinearInterpolator()
            animation.repeatCount = Animation.INFINITE
            animation.repeatMode = Animation.REVERSE
            binding.fragmentMessageInputView.microphoneEnabledInfo.startAnimation(animation)

            binding.fragmentMessageInputView.microphoneEnabledInfo.visibility = View.VISIBLE
            binding.fragmentMessageInputView.microphoneEnabledInfoBackground.visibility = View.VISIBLE
            binding.fragmentMessageInputView.audioRecordDuration.visibility = View.VISIBLE
            binding.fragmentMessageInputView.slideToCancelDescription.visibility = View.VISIBLE
            binding.fragmentMessageInputView.attachmentButton.visibility = View.GONE
            binding.fragmentMessageInputView.smileyButton.visibility = View.GONE
            binding.fragmentMessageInputView.messageInput.visibility = View.GONE
            binding.fragmentMessageInputView.messageInput.hint = ""
        } else {
            binding.fragmentMessageInputView.microphoneEnabledInfo.clearAnimation()

            binding.fragmentMessageInputView.microphoneEnabledInfo.visibility = View.GONE
            binding.fragmentMessageInputView.microphoneEnabledInfoBackground.visibility = View.GONE
            binding.fragmentMessageInputView.audioRecordDuration.visibility = View.GONE
            binding.fragmentMessageInputView.slideToCancelDescription.visibility = View.GONE
            binding.fragmentMessageInputView.attachmentButton.visibility = View.VISIBLE
            binding.fragmentMessageInputView.smileyButton.visibility = View.VISIBLE
            binding.fragmentMessageInputView.messageInput.visibility = View.VISIBLE
            binding.fragmentMessageInputView.messageInput.hint =
                requireContext().resources?.getString(R.string.nc_hint_enter_a_message)
        }
    }

    private fun initSmileyKeyboardToggler() {
        val smileyButton = binding.fragmentMessageInputView.findViewById<ImageButton>(R.id.smileyButton)

        emojiPopup = binding.fragmentMessageInputView.inputEditText?.let {
            EmojiPopup(
                rootView = binding.root,
                editText = it,
                onEmojiPopupShownListener = {
                    smileyButton?.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_keyboard_24)
                    )
                },
                onEmojiPopupDismissListener = {
                    smileyButton?.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_insert_emoticon_black_24dp)
                    )
                },
                onEmojiClickListener = {
                    binding.fragmentMessageInputView.inputEditText?.editableText?.append(" ")
                }
            )
        }

        smileyButton?.setOnClickListener {
            emojiPopup?.toggle()
        }
    }

    private fun replyToMessage(quotedMessageText: String?, quotedActorDisplayName: String?, quotedImageUrl: String?) {
        Log.d(TAG, "Reply")
        val view = binding.fragmentMessageInputView
        view.findViewById<ImageButton>(R.id.cancelReplyButton)?.visibility =
            View.VISIBLE

        val quotedMessage = view.findViewById<EmojiTextView>(R.id.quotedMessage)

        quotedMessage?.maxLines = 2
        quotedMessage?.ellipsize = TextUtils.TruncateAt.END
        quotedMessage?.text = quotedMessageText
        view.findViewById<EmojiTextView>(R.id.quotedMessageAuthor)?.text =
            quotedActorDisplayName ?: requireContext().getText(R.string.nc_nick_guest)

        chatActivity.conversationUser?.let {
            val quotedMessageImage = view.findViewById<ImageView>(R.id.quotedMessageImage)
            quotedImageUrl?.let { previewImageUrl ->
                quotedMessageImage?.visibility = View.VISIBLE

                val px = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    QUOTED_MESSAGE_IMAGE_MAX_HEIGHT,
                    resources.displayMetrics
                )

                quotedMessageImage?.maxHeight = px.toInt()
                val layoutParams = quotedMessageImage?.layoutParams as FlexboxLayout.LayoutParams
                layoutParams.flexGrow = 0f
                quotedMessageImage.layoutParams = layoutParams
                quotedMessageImage.load(previewImageUrl) {
                    addHeader("Authorization", chatActivity.credentials!!)
                }
            } ?: run {
                view.findViewById<ImageView>(R.id.quotedMessageImage)?.visibility = View.GONE
            }
        }

        val quotedChatMessageView = view.findViewById<RelativeLayout>(R.id.quotedChatMessageView)
        quotedChatMessageView?.visibility = View.VISIBLE
    }

    fun updateOwnTypingStatus(typedText: CharSequence) {
        fun sendStartTypingSignalingMessage() {
            val concurrentSafeHashMap = chatActivity.webSocketInstance?.getUserMap()
            if (concurrentSafeHashMap != null) {
                for ((sessionId, _) in concurrentSafeHashMap) {
                    val ncSignalingMessage = NCSignalingMessage()
                    ncSignalingMessage.to = sessionId
                    ncSignalingMessage.type = TYPING_STARTED_SIGNALING_MESSAGE_TYPE
                    chatActivity.signalingMessageSender!!.send(ncSignalingMessage)
                }
            }
        }

        if (isTypingStatusEnabled()) {
            if (typedText.isEmpty()) {
                sendStopTypingMessage()
            } else if (typingTimer == null) {
                sendStartTypingSignalingMessage()

                typingTimer = object : CountDownTimer(
                    TYPING_DURATION_TO_SEND_NEXT_TYPING_MESSAGE,
                    TYPING_INTERVAL_TO_SEND_NEXT_TYPING_MESSAGE
                ) {
                    override fun onTick(millisUntilFinished: Long) {
                        // unused
                    }

                    override fun onFinish() {
                        if (typedWhileTypingTimerIsRunning) {
                            sendStartTypingSignalingMessage()
                            cancel()
                            start()
                            typedWhileTypingTimerIsRunning = false
                        } else {
                            sendStopTypingMessage()
                        }
                    }
                }.start()
            } else {
                typedWhileTypingTimerIsRunning = true
            }
        }
    }

    private fun sendStopTypingMessage() {
        if (isTypingStatusEnabled()) {
            typingTimer = null
            typedWhileTypingTimerIsRunning = false

            val concurrentSafeHashMap = chatActivity.webSocketInstance?.getUserMap()
            if (concurrentSafeHashMap != null) {
                for ((sessionId, _) in concurrentSafeHashMap) {
                    val ncSignalingMessage = NCSignalingMessage()
                    ncSignalingMessage.to = sessionId
                    ncSignalingMessage.type = TYPING_STOPPED_SIGNALING_MESSAGE_TYPE
                    chatActivity.signalingMessageSender?.send(ncSignalingMessage)
                }
            }
        }
    }

    private fun isTypingStatusEnabled(): Boolean =
        !CapabilitiesUtil.isTypingStatusPrivate(chatActivity.conversationUser!!)

    private fun submitMessage(sendWithoutNotification: Boolean) {
        if (binding.fragmentMessageInputView.inputEditText != null) {
            val editable = binding.fragmentMessageInputView.inputEditText!!.editableText
            replaceMentionChipSpans(editable)
            binding.fragmentMessageInputView.inputEditText?.setText("")
            sendStopTypingMessage()
            sendMessage(
                editable.toString(),
                sendWithoutNotification
            )
            cancelReply()
            cancelCreateThread()
        }
    }

    private fun sendMessage(message: String, sendWithoutNotification: Boolean) {
        chatActivity.messageInputViewModel.sendChatMessage(
            credentials = chatActivity.conversationUser!!.getCredentials(),
            url = ApiUtils.getUrlForChat(
                chatActivity.chatApiVersion,
                chatActivity.conversationUser!!.baseUrl!!,
                chatActivity.roomToken
            ),
            message = message,
            displayName = chatActivity.conversationUser!!.displayName ?: "",
            replyTo = chatActivity.getReplyToMessageId(),
            sendWithoutNotification = sendWithoutNotification,
            threadTitle = chatActivity.chatViewModel.messageDraft.threadTitle
        )
    }

    private fun replaceMentionChipSpans(editable: Editable) {
        val mentionSpans = editable.getSpans(
            0,
            editable.length,
            Spans.MentionChipSpan::class.java
        )
        for (mentionSpan in mentionSpans) {
            var mentionId = mentionSpan.id
            val shouldQuote = mentionId.contains(" ") ||
                mentionId.contains("@") ||
                mentionId.startsWith("guest/") ||
                mentionId.startsWith("group/") ||
                mentionId.startsWith("email/") ||
                mentionId.startsWith("team/")
            if (shouldQuote) {
                mentionId = "\"$mentionId\""
            }
            editable.replace(
                editable.getSpanStart(mentionSpan),
                editable.getSpanEnd(mentionSpan),
                "@$mentionId"
            )
        }
    }

    private fun showSendButtonMenu() {
        val popupMenu = PopupMenu(
            ContextThemeWrapper(requireContext(), R.style.ChatSendButtonMenu),
            binding.fragmentMessageInputView.button,
            Gravity.END
        )
        popupMenu.inflate(R.menu.chat_send_menu)

        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.send_without_notification -> submitMessage(true)
            }
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popupMenu.setForceShowIcon(true)
        }
        popupMenu.show()
    }

    private fun editMessageAPI(message: ChatMessage, editedMessageText: String) {
        // FIXME Fix API checking with guests?
        val apiVersion: Int = ApiUtils.getChatApiVersion(chatActivity.spreedCapabilities, intArrayOf(1))

        if (message.isTemporary) {
            chatActivity.messageInputViewModel.editTempChatMessage(
                message,
                editedMessageText
            )
        } else {
            chatActivity.messageInputViewModel.editChatMessage(
                chatActivity.credentials!!,
                ApiUtils.getUrlForChatMessage(
                    apiVersion,
                    chatActivity.conversationUser!!.baseUrl!!,
                    chatActivity.roomToken,
                    message.id
                ),
                editedMessageText
            )
        }
    }

    private fun setEditUI(message: ChatMessage) {
        val editedMessage = ChatUtils.getParsedMessage(message.message, message.messageParameters)
        binding.fragmentEditView.editMessage.text = editedMessage
        binding.fragmentMessageInputView.inputEditText.setText(editedMessage)
        if (mentionAutocomplete != null && mentionAutocomplete!!.isPopupShowing) {
            mentionAutocomplete?.dismissPopup()
        }
        val end = binding.fragmentMessageInputView.inputEditText.text.length
        binding.fragmentMessageInputView.inputEditText.setSelection(end)
        binding.fragmentMessageInputView.messageSendButton.visibility = View.GONE
        binding.fragmentMessageInputView.recordAudioButton.visibility = View.GONE
        binding.fragmentMessageInputView.editMessageButton.visibility = View.VISIBLE
        binding.fragmentEditView.editMessageView.visibility = View.VISIBLE
        binding.fragmentMessageInputView.attachmentButton.visibility = View.GONE
    }

    private fun clearEditUI() {
        binding.fragmentMessageInputView.editMessageButton.visibility = View.GONE
        binding.fragmentMessageInputView.inputEditText.setText("")
        binding.fragmentEditView.editMessageView.visibility = View.GONE
        binding.fragmentMessageInputView.attachmentButton.visibility = View.VISIBLE
        chatActivity.messageInputViewModel.edit(null)
        handleButtonsVisibility()
    }

    private fun themeMessageInputView() {
        binding.fragmentMessageInputView.button?.let { viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY) }

        binding.fragmentMessageInputView.findViewById<ImageButton>(R.id.cancelReplyButton)?.let {
            viewThemeUtils.platform
                .themeImageButton(it)
        }

        binding.fragmentMessageInputView.findViewById<MaterialButton>(R.id.playPauseBtn)?.let {
            viewThemeUtils.material.colorMaterialButtonText(it)
        }

        binding.fragmentMessageInputView.findViewById<SeekBar>(R.id.seekbar)?.let {
            viewThemeUtils.platform.themeHorizontalSeekBar(it)
        }

        binding.fragmentMessageInputView.findViewById<ImageView>(R.id.deleteVoiceRecording)?.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }
        binding.fragmentMessageInputView.findViewById<ImageView>(R.id.sendVoiceRecording)?.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }

        binding.fragmentMessageInputView.findViewById<ImageView>(R.id.microphoneEnabledInfo)?.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }

        binding.fragmentMessageInputView.findViewById<LinearLayout>(R.id.voice_preview_container)?.let {
            viewThemeUtils.talk.themeOutgoingMessageBubble(it, true, false)
        }

        binding.fragmentMessageInputView.findViewById<MicInputCloud>(R.id.micInputCloud)?.let {
            viewThemeUtils.talk.themeMicInputCloud(it)
        }
        binding.fragmentMessageInputView.findViewById<ImageView>(R.id.editMessageButton)?.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }
        binding.fragmentEditView.clearEdit.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }
        binding.fragmentCreateThreadView.abortCreateThread.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }

        binding.fragmentCallStarted.callStartedBackground.apply {
            viewThemeUtils.talk.themeOutgoingMessageBubble(this, grouped = true, false)
        }

        binding.fragmentCallStarted.callAuthorChip.apply {
            viewThemeUtils.material.colorChipBackground(this)
        }

        binding.fragmentCallStarted.callAuthorChipSecondary.apply {
            viewThemeUtils.material.colorChipBackground(this)
        }

        binding.fragmentCallStarted.callStartedCloseBtn.apply {
            viewThemeUtils.platform.colorImageView(this, ColorRole.PRIMARY)
        }
    }

    private fun cancelCreateThread() {
        chatActivity.cancelCreateThread()
        chatActivity.messageInputViewModel.stopThreadCreation()
        binding.fragmentCreateThreadView.createThreadView.visibility = View.GONE
    }

    private fun cancelReply() {
        chatActivity.cancelReply()
        clearReplyUi()
    }

    private fun clearReplyUi() {
        val quote = binding.fragmentMessageInputView.findViewById<RelativeLayout>(R.id.quotedChatMessageView)
        quote.visibility = View.GONE
        binding.fragmentMessageInputView.findViewById<ImageButton>(R.id.attachmentButton)?.visibility = View.VISIBLE
    }

    private fun isInReplyState(): Boolean {
        val jsonId = chatActivity.chatViewModel.messageDraft.quotedJsonId
        return jsonId != null
    }
}
