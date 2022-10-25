package com.nextcloud.talk.shareditems.repositories

import android.text.TextUtils
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.models.json.chat.ChatShareOCS
import com.nextcloud.talk.models.json.chat.ChatShareOverall
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedItems
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.reactivex.Observable
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class SharedItemsRepositoryImplTest {

    @Before
    fun setup() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } returns false
    }

    @After
    fun tearDown() {
        unmockkStatic(TextUtils::class)
    }

    @Test
    fun testMedia_empty() {
        // arrange
        val ncApi: NcApi = mockk()
        val sut: SharedItemsRepository = SharedItemsRepositoryImpl(ncApi)

        val result = ChatShareOverall(ChatShareOCS(HashMap()))

        every { ncApi.getSharedItems(any(), any(), any(), any(), any()) } returns Observable.just(
            Response.success(
                result
            )
        )

        // act
        val observable: Observable<SharedItems>? =
            sut.media(SharedItemsRepository.Parameters("", "", "", ""), SharedItemType.MEDIA)

        // assert
        Assert.assertNotNull("Shared items result is null", observable)

        val sharedItems = observable!!.blockingFirst()
        Assert.assertTrue("Shared items not empty", sharedItems.items.isEmpty())
    }
}
