package ch.skyfy.integrity.config

import ch.skyfy.jsonconfiglib.Validatable
import kotlinx.serialization.Serializable

@Serializable
data class ModpackModsList(val list: MutableList<ModInfo> = mutableListOf()) : Validatable {}