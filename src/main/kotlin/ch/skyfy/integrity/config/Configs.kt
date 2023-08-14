package ch.skyfy.integrity.config

import ch.skyfy.integrity.IntegrityMod
import ch.skyfy.jsonconfiglib.ConfigData

object Configs {
    @JvmField
    val CONFIG = ConfigData.invokeSpecial<Config>(IntegrityMod.CONFIG_DIRECTORY.resolve("config.json"), true)

    @JvmField
    val CUSTOM_MODS_CONFIG = ConfigData.invokeSpecial<CustomModsConfig>(IntegrityMod.CONFIG_DIRECTORY.resolve("custom-mods-config.json"), true)

    @JvmField
    val CUSTOM_RESOURCEPACKS_CONFIG = ConfigData.invokeSpecial<CustomResourcepacksConfig>(IntegrityMod.CONFIG_DIRECTORY.resolve("custom-resourcepacks-config.json"), true)

    @JvmField
    val INTEGRITY_CONFIG = ConfigData.invokeSpecial<IntegrityConfig>(IntegrityMod.CONFIG_DIRECTORY.resolve("integrity.json"), true)
}
