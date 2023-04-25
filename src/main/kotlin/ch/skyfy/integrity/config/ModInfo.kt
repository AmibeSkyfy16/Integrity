package ch.skyfy.integrity.config

import ch.skyfy.jsonconfiglib.Validatable
import kotlinx.serialization.Serializable

@Serializable
data class ModInfo(
    val fileName: String,
    val fileHash: String
) : Validatable
