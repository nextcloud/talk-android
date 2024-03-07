package com.nextcloud.talk.models.json.converters

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.joda.time.DateTime

object KotlinxJodaTimeConverter : KSerializer<DateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DateTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): DateTime =
        DateTime.parse(decoder.decodeString())

}
