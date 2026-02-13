package no.synth.where.data.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import no.synth.where.data.geo.LatLng

object LatLngSerializer : KSerializer<LatLng> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LatLng") {
        element<Double>("latitude")
        element<Double>("longitude")
    }

    override fun serialize(encoder: Encoder, value: LatLng) {
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, value.latitude)
            encodeDoubleElement(descriptor, 1, value.longitude)
        }
    }

    override fun deserialize(decoder: Decoder): LatLng {
        return decoder.decodeStructure(descriptor) {
            var latitude = 0.0
            var longitude = 0.0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> latitude = decodeDoubleElement(descriptor, 0)
                    1 -> longitude = decodeDoubleElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            LatLng(latitude, longitude)
        }
    }
}
