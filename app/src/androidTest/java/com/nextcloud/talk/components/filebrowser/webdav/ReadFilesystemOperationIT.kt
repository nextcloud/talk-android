package com.nextcloud.talk.components.filebrowser.webdav

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.nextcloud.talk.components.filebrowser.models.BrowserFile
import com.nextcloud.talk.data.user.model.User
import junit.framework.Assert.assertEquals
import okhttp3.OkHttpClient
import org.junit.Test

class ReadFilesystemOperationIT {
    @Test
    fun showContent() {
        val arguments = InstrumentationRegistry.getArguments()
        val url = Uri.parse(arguments.getString("TEST_SERVER_URL"))
        val username = arguments.getString("TEST_SERVER_USERNAME")
        val password = arguments.getString("TEST_SERVER_PASSWORD")

        val client = OkHttpClient()
        val user = User().apply {
            baseUrl = url.toString()
            userId = username
            this.username = username
            token = password
        }
        val sut = ReadFilesystemOperation(client, user, "/", 1)
        val data = sut.readRemotePath().data as List<BrowserFile>
        assertEquals(1, data.size)
    }
}
