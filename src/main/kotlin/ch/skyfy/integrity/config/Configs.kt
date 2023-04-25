package ch.skyfy.integrity.config

import ch.skyfy.integrity.IntegrityMod
import ch.skyfy.jsonconfiglib.ConfigData

object Configs {
    @JvmField
    val CONFIG = ConfigData.invokeSpecial<Config>(IntegrityMod.CONFIG_DIRECTORY.resolve("config.json"), true)

    @JvmField
    val CUSTOM_MODS_CONFIG = ConfigData.invokeSpecial<CustomModsConfig>(IntegrityMod.CONFIG_DIRECTORY.resolve("custom-mods-config.json"), true)

    @JvmField
    val MODPACK_MODS_LIST = ConfigData.invokeSpecial<ModpackModsList>(IntegrityMod.CONFIG_DIRECTORY.resolve("modpack-mods-list.json"), true)
}
