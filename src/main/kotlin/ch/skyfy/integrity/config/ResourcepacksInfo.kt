package ch.skyfy.integrity.config

import ch.skyfy.jsonconfiglib.Validatable
import kotlinx.serialization.Serializable

@Serializable
data class ResourcepacksInfo(
    val fileName: String,
    val fileHash: String
) : Validatable
