package com.nextcloud.talk.models.domain

import com.nextcloud.talk.models.json.conversations.Conversation

class ConversationModel(
    var roomId: String?,
    var token: String? = null,
    var name: String? = null,
    var displayName: String? = null,
    var description: String? = null,
    var type: ConversationType? = null,
    var lastPing: Long = 0,
    var participantType: ParticipantType? = null,
    var hasPassword: Boolean = false,
    var sessionId: String? = null,
    var actorId: String? = null,
    var actorType: String? = null,
    var password: String? = null,
    var favorite: Boolean = false,
    var lastActivity: Long = 0,
    var unreadMessages: Int = 0,
    var unreadMention: Boolean = false,
    // var lastMessage: .....? = null,
    var objectType: ObjectType? = null,
    var notificationLevel: NotificationLevel? = null,
    var conversationReadOnlyState: ConversationReadOnlyState? = null,
    var lobbyState: LobbyState? = null,
    var lobbyTimer: Long? = null,
    var lastReadMessage: Int = 0,
    var hasCall: Boolean = false,
    var callFlag: Int = 0,
    var canStartCall: Boolean = false,
    var canLeaveConversation: Boolean? = null,
    var canDeleteConversation: Boolean? = null,
    var unreadMentionDirect: Boolean? = null,
    var notificationCalls: Int? = null,
    var permissions: Int = 0,
    var messageExpiration: Int = 0,
    var status: String? = null,
    var statusIcon: String? = null,
    var statusMessage: String? = null,
    var statusClearAt: Long? = 0,
    var callRecording: Int = 0,
    var avatarVersion: String? = null,
    var hasCustomAvatar: Boolean? = null
) {

    companion object {
        fun mapToConversationModel(
            conversation: Conversation
        ): ConversationModel {
            return ConversationModel(
                roomId = conversation.roomId,
                token = conversation.token,
                name = conversation.name,
                displayName = conversation.displayName,
                description = conversation.description,
                type = conversation.type?.let { ConversationType.valueOf(it.name) },
                lastPing = conversation.lastPing,
                participantType = conversation.participantType?.let { ParticipantType.valueOf(it.name) },
                hasPassword = conversation.hasPassword,
                sessionId = conversation.sessionId,
                actorId = conversation.actorId,
                actorType = conversation.actorType,
                password = conversation.password,
                favorite = conversation.favorite,
                lastActivity = conversation.lastActivity,
                unreadMessages = conversation.unreadMessages,
                unreadMention = conversation.unreadMention,
                // lastMessage = conversation.lastMessage,     to do...
                objectType = conversation.objectType?.let { ObjectType.valueOf(it.name) },
                notificationLevel = conversation.notificationLevel?.let {
                    NotificationLevel.valueOf(
                        it.name
                    )
                },
                conversationReadOnlyState = conversation.conversationReadOnlyState?.let {
                    ConversationReadOnlyState.valueOf(
                        it.name
                    )
                },
                lobbyState = conversation.lobbyState?.let { LobbyState.valueOf(it.name) },
                lobbyTimer = conversation.lobbyTimer,
                lastReadMessage = conversation.lastReadMessage,
                hasCall = conversation.hasCall,
                callFlag = conversation.callFlag,
                canStartCall = conversation.canStartCall,
                canLeaveConversation = conversation.canLeaveConversation,
                canDeleteConversation = conversation.canDeleteConversation,
                unreadMentionDirect = conversation.unreadMentionDirect,
                notificationCalls = conversation.notificationCalls,
                permissions = conversation.permissions,
                messageExpiration = conversation.messageExpiration,
                status = conversation.status,
                statusIcon = conversation.statusIcon,
                statusMessage = conversation.statusMessage,
                statusClearAt = conversation.statusClearAt,
                callRecording = conversation.callRecording,
                avatarVersion = conversation.avatarVersion,
                hasCustomAvatar = conversation.hasCustomAvatar
            )
        }
    }
}

enum class ConversationType {
    DUMMY,
    ROOM_TYPE_ONE_TO_ONE_CALL,
    ROOM_GROUP_CALL,
    ROOM_PUBLIC_CALL,
    ROOM_SYSTEM,
    FORMER_ONE_TO_ONE
}

enum class ParticipantType {
    DUMMY, OWNER, MODERATOR, USER, GUEST, USER_FOLLOWING_LINK, GUEST_MODERATOR
}

enum class ObjectType {
    DEFAULT,
    SHARE_PASSWORD,
    FILE,
    ROOM
}

enum class NotificationLevel {
    DEFAULT, ALWAYS, MENTION, NEVER
}

enum class ConversationReadOnlyState {
    CONVERSATION_READ_WRITE, CONVERSATION_READ_ONLY
}

enum class LobbyState {
    LOBBY_STATE_ALL_PARTICIPANTS, LOBBY_STATE_MODERATORS_ONLY
}
