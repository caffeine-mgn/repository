package pw.binom.repo.repositories.maven.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pw.binom.date.DateTime
import pw.binom.date.of

object DateTimeSerializer : KSerializer<DateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): DateTime {
        val str = decoder.decodeString()
        val year = str.substring(0, 4).toInt()
        val month = str.substring(4, 6).toInt()
        val day = str.substring(6, 8).toInt()
        val h = str.substring(8, 10).toInt()
        val m = str.substring(10, 12).toInt()
        val s = str.substring(12, 14).toInt()
        return DateTime.of(
            timeZoneOffset = 0,
            year = year,
            month = month,
            dayOfMonth = day,
            hours = h,
            minutes = m,
            seconds = s,
            )
    }

    override fun serialize(encoder: Encoder, value: DateTime) {
        val sb = StringBuilder()
        val cal = value.calendar(0)
        sb
            .append(cal.year.toString().padStart(4, '0'))
            .append(cal.month.toString().padStart(2, '0'))
            .append(cal.dayOfMonth.toString().padStart(2, '0'))
            .append(cal.hours.toString().padStart(2, '0'))
            .append(cal.minutes.toString().padStart(2, '0'))
            .append(cal.seconds.toString().padStart(2, '0'))
        encoder.encodeString(sb.toString())
    }
}