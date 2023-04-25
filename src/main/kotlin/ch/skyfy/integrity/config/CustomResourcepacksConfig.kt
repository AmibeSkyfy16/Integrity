package ch.skyfy.integrity.config

import ch.skyfy.jsonconfiglib.Validatable
import kotlinx.serialization.Serializable

@Serializable
data class CustomResourcepacksConfig(
    val globalAllowedMissingResourcepacks: MutableList<ModInfo> = mutableListOf(),
    val globalAllowedExtraResourcepacks: MutableList<ModInfo> = mutableListOf(),
    val allowedMissingResourcepacksPerPlayer: MutableMap<String, MutableList<ModInfo>> = mutableMapOf(),
    val allowedExtraResourcepacksPerPlayer: MutableMap<String, MutableList<ModInfo>> = mutableMapOf()
): Validatable