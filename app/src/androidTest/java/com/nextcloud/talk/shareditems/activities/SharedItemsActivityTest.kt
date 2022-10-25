package com.nextcloud.talk.shareditems.activities

import android.content.Intent
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.junit.Test

import com.nextcloud.talk.R

class SharedItemsActivityTest {

    @Test
    fun launch() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), SharedItemsActivity::class.java).apply {
            putExtras(
                bundleOf(
                    BundleKeys.KEY_ROOM_TOKEN to "",
                    BundleKeys.KEY_CONVERSATION_NAME to "",
                    BundleKeys.KEY_USER_ENTITY to User(1L, ", ", ", ", "", token = "")
                )
            )
        }
        launchActivity<SharedItemsActivity>(intent).use { scenario ->
            Espresso.onView(withId(R.id.emptyContainer)).check { view, _ -> view.isVisible }
        }
    }
}
