package pw.binom.repo.properties

import kotlinx.serialization.Serializable
import pw.binom.validate.annotations.NotBlank
import pw.binom.validate.annotations.OneOf

@Serializable
@OneOf(
    "basicAuth",
    "bearerAuth",
)
data class HttpAuthProperties(
    val basicAuth: BasicAuth? = null,
    val bearerAuth: BearerAuth? = null,
) {
    @Serializable
    data class BasicAuth(
        @NotBlank
        val user: String,
        @NotBlank
        val password: String,
    )

    @Serializable
    data class BearerAuth(
        @NotBlank
        val token: String,
    )
}