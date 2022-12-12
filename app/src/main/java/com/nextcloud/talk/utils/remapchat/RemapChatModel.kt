package com.nextcloud.talk.utils.remapchat

import android.os.Bundle
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.Router

data class RemapChatModel(
    val router: Router,
    val controllerChangeHandler: ControllerChangeHandler,
    val chatControllerTag: String,
    val bundle: Bundle
)
