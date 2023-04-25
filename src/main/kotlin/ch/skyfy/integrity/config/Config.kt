package ch.skyfy.integrity.config

import ch.skyfy.jsonconfiglib.Validatable
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val useless: String = "useless"
) : Validatable
