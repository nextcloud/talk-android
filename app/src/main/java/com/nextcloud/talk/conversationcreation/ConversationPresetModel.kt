/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation

import com.nextcloud.talk.models.json.conversations.ConversationPreset

/**
 * A conversation type offered by the server, with the parameters it applies on creation.
 */
data class ConversationPresetModel(
    val identifier: String,
    val name: String,
    val description: String,
    val parameters: Map<String, Int>
) {
    companion object {
        fun mapToConversationPresetModel(preset: ConversationPreset): ConversationPresetModel? {
            val identifier = preset.identifier ?: return null
            return ConversationPresetModel(
                identifier = identifier,
                name = preset.name.orEmpty(),
                description = preset.description.orEmpty(),
                parameters = preset.parameters.orEmpty()
            )
        }
    }
}

/**
 * Identifiers of the conversation types the server offers.
 */
object ConversationPresetId {
    const val DEFAULT = "default"
    const val FORCED = "forced"
    const val VOICE_ROOM = "voiceroom"
    const val PRESENTATION = "presentation"
    const val WEBINAR = "webinar"
    const val CLASSIFIED = "classified"
    const val CHANNEL = "channel"
    const val ANNOUNCEMENT = "announcement"

    /**
     * Types whose conversations are locked down: their visibility can not be changed and only local
     * users, groups and teams can be invited.
     */
    val lockedDown = setOf(CLASSIFIED)
}

/**
 * The presets that are offered for selection. The forced preset is applied by the server and is
 * never a conversation type of its own.
 */
fun List<ConversationPresetModel>.selectable(): List<ConversationPresetModel> =
    filterNot { it.identifier == ConversationPresetId.FORCED }

/**
 * The parameters of a single preset, empty when the server does not offer it.
 */
fun List<ConversationPresetModel>.parametersOf(identifier: String): Map<String, Int> =
    firstOrNull { it.identifier == identifier }?.parameters.orEmpty()

/**
 * The parameters a conversation of the given type is created with: the administrator configured
 * defaults, then the values of the selected preset, then the parameters the user chose, and finally
 * the ones an administrator pinned, which the server enforces on top of any request anyway.
 */
fun List<ConversationPresetModel>.parametersFor(
    identifier: String,
    chosenByUser: Map<String, Int> = emptyMap()
): CreateConversationParams =
    CreateConversationParams()
        .withParameters(appliedParametersOf(identifier))
        .withParameters(chosenByUser)
        .withParameters(parametersOf(ConversationPresetId.FORCED))

private fun List<ConversationPresetModel>.appliedParametersOf(identifier: String): Map<String, Int> =
    parametersOf(ConversationPresetId.DEFAULT) + parametersOf(identifier)

/**
 * The parameters of the given type that are worth showing to the user, leaving out those an
 * administrator pinned for every conversation.
 */
fun List<ConversationPresetModel>.parametersToPreviewFor(identifier: String): Map<String, Int> {
    val forced = parametersOf(ConversationPresetId.FORCED).keys
    return appliedParametersOf(identifier).filterKeys { it !in forced }
}

/**
 * Sources an autocomplete result can come from, as the participants API names them.
 */
object ParticipantSource {
    const val USERS = "users"
    const val GROUPS = "groups"
    const val EMAILS = "emails"
    const val CIRCLES = "circles"
    const val FEDERATED = "federated"
    const val PHONES = "phones"

    /**
     * Sources that stay on this instance. Locked down conversations accept only these.
     */
    val local = setOf(USERS, GROUPS, CIRCLES)
}
