/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.json

import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.tags.ConversationTagDto
import com.nextcloud.talk.models.json.tags.ConversationTagErrorOverall
import com.nextcloud.talk.models.json.tags.ConversationTagsOverall
import com.nextcloud.talk.models.json.tags.CreateConversationTagRequestDto
import com.nextcloud.talk.utils.serialization.KotlinxSerializationConverterFactory
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Converter
import retrofit2.Retrofit

class ConversationTagsParsingTest {

    private val factory = KotlinxSerializationConverterFactory.create()
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://nextcloud.com")
        .addConverterFactory(factory)
        .build()

    @Test
    fun parsesConversationTagsResponse() {
        val json = """
            {"ocs":{"meta":{"status":"ok","statuscode":200,"message":"OK","unknown":1},"data":[
              {"id":"favorites","name":"","sortOrder":0,"collapsed":false,"type":"favorites"},
              {"id":"1","name":"Work","sortOrder":1,"collapsed":null,"type":"custom","extra":"ignored"}
            ]}}
        """.trimIndent()

        val converter = factory.responseBodyConverter(ConversationTagsOverall::class.java, emptyArray(), retrofit)
        val overall = converter!!.convert(json.toResponseBody()) as ConversationTagsOverall

        assertEquals(200, overall.ocs!!.meta!!.statusCode)
        assertEquals(
            listOf(
                ConversationTagDto(id = "favorites", type = ConversationTagDto.TYPE_FAVORITES),
                ConversationTagDto(id = "1", name = "Work", sortOrder = 1)
            ),
            overall.ocs!!.data
        )
    }

    @Test
    fun parsesConversationTagErrorResponse() {
        val converter =
            factory.responseBodyConverter(ConversationTagErrorOverall::class.java, emptyArray(), retrofit)
        val overall = converter!!.convert(
            """{"ocs":{"meta":{"status":"failure","statuscode":400},"data":{"error":"name"}}}""".toResponseBody()
        ) as ConversationTagErrorOverall

        assertEquals("name", overall.ocs!!.data!!.error)
    }

    @Test
    fun encodesConversationTagRequest() {
        @Suppress("UNCHECKED_CAST")
        val converter = factory.requestBodyConverter(
            CreateConversationTagRequestDto::class.java,
            emptyArray(),
            emptyArray(),
            retrofit
        ) as Converter<CreateConversationTagRequestDto, RequestBody>

        val buffer = Buffer()
        converter.convert(CreateConversationTagRequestDto("Work"))!!.writeTo(buffer)

        assertEquals("""{"name":"Work"}""", buffer.readUtf8())
    }

    @Test
    fun leavesLoganSquareTypesToOtherConverters() {
        assertNull(factory.responseBodyConverter(GenericOverall::class.java, emptyArray(), retrofit))
        assertNull(factory.responseBodyConverter(String::class.java, emptyArray(), retrofit))
        assertNotNull(factory.responseBodyConverter(ConversationTagsOverall::class.java, emptyArray(), retrofit))
    }
}
