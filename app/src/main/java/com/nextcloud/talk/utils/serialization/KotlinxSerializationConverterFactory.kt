/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.serialization

import com.bluelinelabs.logansquare.LoganSquare
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.lang.reflect.Type

/**
 * Retrofit [Converter.Factory] that uses kotlinx serialization only for types annotated with
 * [Serializable] which have no LoganSquare mapper. All other types are left to the next factory,
 * so it must be added before the LoganSquare converter factory.
 */
class KotlinxSerializationConverterFactory private constructor() : Converter.Factory() {

    /**
     * Retrofit's stock kotlinx serialization converter, configured with [KotlinxJson]. It accepts every type, so it
     * is only called for types that passed [isHandled].
     */
    private val delegate = KotlinxJson.asConverterFactory("application/json; charset=UTF-8".toMediaType())

    /**
     * Called by Retrofit to find a converter that decodes a response body into [type]. Returns the kotlinx
     * converter for handled types and null otherwise, so Retrofit asks the next factory (LoganSquare).
     */
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? =
        if (isHandled(type)) {
            delegate.responseBodyConverter(type, annotations, retrofit)
        } else {
            null
        }

    /**
     * Called by Retrofit to find a converter that encodes a `@Body` parameter of [type] into a JSON request body.
     * Returns the kotlinx converter for handled types and null otherwise, so Retrofit asks the next factory.
     */
    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? =
        if (isHandled(type)) {
            delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
        } else {
            null
        }

    /**
     * Decides whether kotlinx serialization handles [type]: its class must be annotated with [Serializable] and
     * have no LoganSquare mapper. Classes annotated for both (e.g. GenericMetaDto) stay with LoganSquare.
     * Only the raw type is checked, so for `List<Foo>` the class `List` is checked, not `Foo`.
     */
    private fun isHandled(type: Type): Boolean {
        val rawType = getRawType(type)
        return rawType.isAnnotationPresent(Serializable::class.java) && !LoganSquare.supports(rawType)
    }

    companion object {
        /** Creates the factory. Register it with Retrofit before the LoganSquare converter factory. */
        @JvmStatic
        fun create(): KotlinxSerializationConverterFactory = KotlinxSerializationConverterFactory()
    }
}
