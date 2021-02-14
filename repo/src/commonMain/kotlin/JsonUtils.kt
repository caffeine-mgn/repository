import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object JsonUtils {
    fun merge(a: JsonElement, b: JsonElement): JsonElement {
        require(a::class == b::class) { "Element $b must have same type of node $a" }
        return when (a) {
            is JsonObject -> {
                b as JsonObject
                val keys = a.keys + b.keys
                JsonObject(
                    keys.mapNotNull {
                        val aa = a[it]
                        val bb = b[it]
                        val obj = when {
                            aa == null && bb != null -> bb
                            aa != null && bb == null -> aa
                            aa != null && bb != null -> merge(aa, bb)
                            else -> return@mapNotNull null
                        }
                        it to obj
                    }.toMap()
                )
            }
            is JsonArray -> JsonArray(a + b as JsonArray)
            else -> throw IllegalArgumentException("Can't merge $a and $b")
        }
    }
}