package ch.skyfy.integrity.config

import ch.skyfy.jsonconfiglib.Validatable
import kotlinx.serialization.Serializable

@Serializable
data class IntegrityConfig(
    val modInfos: MutableSet<ModInfo> = mutableSetOf(),
    val resourcepacksInfos: MutableSet<ResourcepacksInfo> = mutableSetOf(),

    val allowResourcepacksUnzipped: Boolean = false
) : Validatable {}