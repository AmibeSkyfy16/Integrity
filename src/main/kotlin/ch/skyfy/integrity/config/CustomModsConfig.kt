package ch.skyfy.integrity.config

import ch.skyfy.jsonconfiglib.Validatable
import kotlinx.serialization.Serializable

@Serializable
data class CustomModsConfig(
    val globalAllowedMissingMods: MutableList<ModInfo> = mutableListOf(),
    val globalAllowedExtraMods: MutableList<ModInfo> = mutableListOf(),
    val allowedMissingModsPerPlayer: MutableMap<String, MutableList<ModInfo>> = mutableMapOf(),
    val allowedExtraModsPerPlayer: MutableMap<String, MutableList<ModInfo>> = mutableMapOf()
): Validatable