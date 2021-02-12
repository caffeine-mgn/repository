import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object JsonUtils {
    fun merge(a: JsonElement, b: JsonElement): JsonElement {
        require(a::class == b::class) { "Element $b must have same type of node $a" }
        return when (a) {
            is JsonObject -> JsonObject(a + b as JsonObject)
            is JsonArray -> JsonArray(a + b as JsonArray)
            else -> throw IllegalArgumentException("Can't merge $a and $b")
        }
    }
}