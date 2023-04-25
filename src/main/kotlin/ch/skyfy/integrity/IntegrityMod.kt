package ch.skyfy.integrity

import ch.skyfy.integrity.command.ReloadFilesCmd
import ch.skyfy.integrity.config.*
import ch.skyfy.integrity.utils.ModUtils
import ch.skyfy.integrity.utils.ModUtils.equalsIgnoreOrder
import ch.skyfy.jsonconfiglib.ConfigManager
import ch.skyfy.jsonconfiglib.updateMap
import com.google.common.hash.Hashing
import com.google.common.io.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.*
import kotlin.time.Duration.Companion.seconds

@Suppress("MemberVisibilityCanBePrivate")
class IntegrityMod(override val coroutineContext: CoroutineContext = Dispatchers.Default) : CoroutineScope, ModInitializer {

    companion object {
        const val MOD_ID: String = "integrity"
        val CONFIG_DIRECTORY: Path = FabricLoader.getInstance().configDir.resolve(MOD_ID)
        val LOGGER: Logger = LogManager.getLogger(IntegrityMod::class.java)

        val INTEGRITY_DATA_RECEIVED: MutableSet<String> = mutableSetOf()

        val MODS_INTEGRITY = mutableMapOf<String, Boolean>()
        val RESOURCEPACKS_INTEGRITY = mutableMapOf<String, Boolean>()

        val INTEGRITY_PACKET_ID = Identifier(MOD_ID, "integrity")
    }

    init {
        if (FabricLoader.getInstance().environmentType == EnvType.SERVER) {
            ConfigManager.loadConfigs(arrayOf(Configs::class.java))
        }
    }

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    override fun onInitialize() {
        registerCommands()
        registerCallbacks()

        if (FabricLoader.getInstance().environmentType == EnvType.SERVER) {
            ServerPlayNetworking.registerGlobalReceiver(INTEGRITY_PACKET_ID) { server, player, handler, buf, responseSender ->
                INTEGRITY_DATA_RECEIVED.add(player.uuidAsString)

                val integrityConfig = Json.decodeFromString<IntegrityConfig>(buf.readString())

                if (Configs.INTEGRITY_CONFIG.serializableData.modInfos equalsIgnoreOrder integrityConfig.modInfos) {
                    MODS_INTEGRITY[player.uuidAsString] = true
                } else
                {
                    // It is possible that a user has added one or more mods to the modpack.
                    val extraMods = mutableSetOf<ModInfo>()
                    integrityConfig.modInfos.forEach { modpackModList ->
                        if (Configs.INTEGRITY_CONFIG.serializableData.modInfos.none { it.fileName == modpackModList.fileName && it.fileHash == modpackModList.fileHash }) {
                            extraMods.add(modpackModList.copy())
                        }
                    }

                    // It is also possible that a user has removed one or more mods from the modpack.
                    val missingMods = mutableSetOf<ModInfo>()
                    Configs.INTEGRITY_CONFIG.serializableData.modInfos.forEach { modpackModList ->
                        if (integrityConfig.modInfos.none { it.fileName == modpackModList.fileName && it.fileHash == modpackModList.fileHash }) {
                            missingMods.add(modpackModList.copy())
                        }
                    }

                    // Displaying mods that have been added to the modpack
                    if (extraMods.size > 0) {
                        LOGGER.info("The following extra mods have been found for player ${player.name.string}")
                        extraMods.forEach {
                            LOGGER.info(it.toString())
                        }
                    }

                    // Displaying mods that have been removed from the modpack
                    if (missingMods.size > 0) {
                        LOGGER.info("The following missing mods have been found for player ${player.name.string}")
                        missingMods.forEach {
                            LOGGER.info(it.toString())
                        }
                    }

                    // You may agree that a particular user or everyone should add an extra mod to the modpack,
                    // so you can add the mod in question to the "custom-mods-config.json"
                    // file so that the player can still login with the extra mod
                    val allowedExtraMods = Configs.CUSTOM_MODS_CONFIG.serializableData.globalAllowedExtraMods
                    Configs.CUSTOM_MODS_CONFIG.serializableData.allowedExtraModsPerPlayer[ModUtils.getPlayerNameWithUUID(player)]?.let {
                        allowedExtraMods.addAll(it)
                    }

                    if (allowedExtraMods equalsIgnoreOrder extraMods.toList()) {
                        LOGGER.info("All the extra mods are allowed for player ${player.name.string}")
                    } else {
                        val extraUnauthorizedMods = mutableListOf<ModInfo>()
                        extraMods.forEach { modInfo ->
                            if (allowedExtraMods.none { it.fileName == modInfo.fileName && it.fileHash == modInfo.fileHash }) {
                                extraUnauthorizedMods.add(modInfo.copy())
                            }
                        }
                        if (extraUnauthorizedMods.size > 0) {
                            LOGGER.warn("The following unauthorized extra mods have been found for player ${player.name.string}")
                            extraUnauthorizedMods.forEach {
                                LOGGER.warn(it.toString())
                            }

                            MODS_INTEGRITY[player.uuidAsString] = false
                            player.networkHandler.disconnect(Text.literal("There are some mods that the server does not want you to have, but that you have anyway !").setStyle(Style.EMPTY.withColor(Formatting.RED)))
                            return@registerGlobalReceiver
                        }
                    }

                    // You may agree that a particular user or everyone should remove a mod to the modpack,
                    // so you can add the mod in question to the "custom-mods-config.json"
                    // file so that the player can still login with the missing mod
                    val allowedMissingMods = Configs.CUSTOM_MODS_CONFIG.serializableData.globalAllowedMissingMods
                    Configs.CUSTOM_MODS_CONFIG.serializableData.allowedMissingModsPerPlayer[ModUtils.getPlayerNameWithUUID(player)]?.let {
                        allowedExtraMods.addAll(it)
                    }

                    if (allowedMissingMods equalsIgnoreOrder missingMods.toList()) {
                        LOGGER.info("All the missing mods are allowed for player ${player.name.string}")
                    } else {
                        val missingUnauthorizedMods = mutableListOf<ModInfo>()
                        missingMods.forEach { modInfo ->
                            if (allowedExtraMods.none { it.fileName == modInfo.fileName && it.fileHash == modInfo.fileHash }) {
                                missingUnauthorizedMods.add(modInfo.copy())
                            }
                        }
                        if (missingUnauthorizedMods.size > 0) {
                            LOGGER.warn("The following unauthorized missing mods have been found for player ${player.name.string}")
                            missingUnauthorizedMods.forEach {
                                LOGGER.warn(it.toString())
                            }

                            MODS_INTEGRITY[player.uuidAsString] = false
                            player.networkHandler.disconnect(Text.literal("There are mods that the server wants you to have, but you don't !").setStyle(Style.EMPTY.withColor(Formatting.RED)))
                            return@registerGlobalReceiver
                        }
                    }

                    MODS_INTEGRITY[player.uuidAsString] = true
                }

                LOGGER.info("modpack of player ${player.name.string} has integrity for mods. Now checking resourcepacks")


                if (!Configs.INTEGRITY_CONFIG.serializableData.allowResourcepacksUnzipped && integrityConfig.resourcepacksInfos.any { it.fileHash == "IMPOSSIBLE HASH" }) {
                    RESOURCEPACKS_INTEGRITY[player.uuidAsString] = false
                    player.networkHandler.disconnect(Text.literal("There are some resourcepacks that the server does not want you to have, but that you have anyway ! (Server doesn't like unzipped resourcepacks !)").setStyle(Style.EMPTY.withColor(Formatting.RED)))
                    return@registerGlobalReceiver
                }

                if (Configs.INTEGRITY_CONFIG.serializableData.resourcepacksInfos equalsIgnoreOrder integrityConfig.resourcepacksInfos) {
                    RESOURCEPACKS_INTEGRITY[player.uuidAsString] = true
                } else {
                    // It is possible that a user has added one or more mods to the modpack.
                    val extraResourcepacks = mutableSetOf<ResourcepacksInfo>()
                    integrityConfig.resourcepacksInfos.forEach { resourcepacksInfo ->
                        if (Configs.INTEGRITY_CONFIG.serializableData.resourcepacksInfos.none { it.fileName == resourcepacksInfo.fileName && it.fileHash == resourcepacksInfo.fileHash }) {
                            extraResourcepacks.add(resourcepacksInfo.copy())
                        }
                    }

                    // It is also possible that a user has removed one or more mods from the modpack.
                    val missingResourcepacks = mutableSetOf<ResourcepacksInfo>()
                    Configs.INTEGRITY_CONFIG.serializableData.resourcepacksInfos.forEach { resourcepacksInfo ->
                        if (integrityConfig.resourcepacksInfos.none { it.fileName == resourcepacksInfo.fileName && it.fileHash == resourcepacksInfo.fileHash }) {
                            missingResourcepacks.add(resourcepacksInfo.copy())
                        }
                    }

                    // Displaying mods that have been added to the modpack
                    if (extraResourcepacks.size > 0) {
                        LOGGER.info("The following extra resourcepacks have been found for player ${player.name.string}")
                        extraResourcepacks.forEach {
                            LOGGER.info(it.toString())
                        }
                    }

                    // Displaying mods that have been removed from the modpack
                    if (missingResourcepacks.size > 0) {
                        LOGGER.info("The following missing resourcepacks have been found for player ${player.name.string}")
                        missingResourcepacks.forEach {
                            LOGGER.info(it.toString())
                        }
                    }

                    // You may agree that a particular user or everyone should add an extra mod to the modpack,
                    // so you can add the mod in question to the "custom-mods-config.json"
                    // file so that the player can still login with the extra mod
                    val allowedExtraResourcepacks = Configs.CUSTOM_RESOURCEPACKS_CONFIG.serializableData.globalAllowedExtraResourcepacks
                    Configs.CUSTOM_RESOURCEPACKS_CONFIG.serializableData.allowedExtraResourcepacksPerPlayer[ModUtils.getPlayerNameWithUUID(player)]?.let {
                        allowedExtraResourcepacks.addAll(it)
                    }

                    if (allowedExtraResourcepacks equalsIgnoreOrder extraResourcepacks.toList()) {
                        LOGGER.info("All the extra resourcepacks are allowed for player ${player.name.string}")
                    } else {
                        val extraUnauthorizedResourcepacks = mutableListOf<ResourcepacksInfo>()
                        extraResourcepacks.forEach { resourcepacksInfo ->
                            if (allowedExtraResourcepacks.none { it.fileName == resourcepacksInfo.fileName && it.fileHash == resourcepacksInfo.fileHash }) {
                                extraUnauthorizedResourcepacks.add(resourcepacksInfo.copy())
                            }
                        }
                        if (extraUnauthorizedResourcepacks.size > 0) {
                            LOGGER.warn("The following unauthorized extra resourcepacks have been found for player ${player.name.string}")
                            extraUnauthorizedResourcepacks.forEach {
                                LOGGER.warn(it.toString())
                            }

                            RESOURCEPACKS_INTEGRITY[player.uuidAsString] = false
                            player.networkHandler.disconnect(Text.literal("There are some resourcepacks that the server does not want you to have, but that you have anyway !").setStyle(Style.EMPTY.withColor(Formatting.RED)))
                            return@registerGlobalReceiver
                        }
                    }

                    // You may agree that a particular user or everyone should remove a mod to the modpack,
                    // so you can add the mod in question to the "custom-mods-config.json"
                    // file so that the player can still login with the missing mod
                    val allowedMissingResourcepacks = Configs.CUSTOM_RESOURCEPACKS_CONFIG.serializableData.globalAllowedMissingResourcepacks
                    Configs.CUSTOM_RESOURCEPACKS_CONFIG.serializableData.allowedMissingResourcepacksPerPlayer[ModUtils.getPlayerNameWithUUID(player)]?.let {
                        allowedExtraResourcepacks.addAll(it)
                    }

                    if (allowedMissingResourcepacks equalsIgnoreOrder missingResourcepacks.toList()) {
                        LOGGER.info("All the missing mods are allowed for player ${player.name.string}")
                    } else {
                        val missingUnauthorizedResourcepacks = mutableListOf<ResourcepacksInfo>()
                        missingResourcepacks.forEach { resourcepacksInfo ->
                            if (allowedExtraResourcepacks.none { it.fileName == resourcepacksInfo.fileName && it.fileHash == resourcepacksInfo.fileHash }) {
                                missingUnauthorizedResourcepacks.add(resourcepacksInfo.copy())
                            }
                        }
                        if (missingUnauthorizedResourcepacks.size > 0) {
                            LOGGER.warn("The following unauthorized missing resourcepacks have been found for player ${player.name.string}")
                            missingUnauthorizedResourcepacks.forEach {
                                LOGGER.warn(it.toString())
                            }

                            RESOURCEPACKS_INTEGRITY[player.uuidAsString] = false
                            player.networkHandler.disconnect(Text.literal("There are resourcepacks that the server wants you to have, but you don't !").setStyle(Style.EMPTY.withColor(Formatting.RED)))
                            return@registerGlobalReceiver
                        }
                    }

                    RESOURCEPACKS_INTEGRITY[player.uuidAsString] = true
                }

                LOGGER.info("modpack of player ${player.name.string} has integrity for resourcepacks !")
            }

            ServerPlayConnectionEvents.JOIN.register { handler, sender, client ->
                val playerNameWithUUID = ModUtils.getPlayerNameWithUUID(handler.player)

                // In order to make it easier for the user to configure his files, we add the player's ID beforehand
                Configs.CUSTOM_MODS_CONFIG.updateMap(CustomModsConfig::allowedExtraModsPerPlayer) {
                    if (!it.contains(playerNameWithUUID)) it[playerNameWithUUID] = mutableListOf()
                }
                Configs.CUSTOM_MODS_CONFIG.updateMap(CustomModsConfig::allowedMissingModsPerPlayer) {
                    if (!it.contains(playerNameWithUUID)) it[playerNameWithUUID] = mutableListOf()
                }

                // If after 25 seconds, the server didn't receive the integrity data, the player will be disconnected
                launch {
                    delay(25.seconds)
                    if (!INTEGRITY_DATA_RECEIVED.contains(handler.player.uuidAsString)) {
                        LOGGER.info("NO INTEGRITY DATA RECEIVED FOR PLAYER ${handler.player.name.string}")
                        handler.player.networkHandler.disconnect(Text.literal("Mod Integrity is probably not installed cause no integrity data received !"))
                    }
                }
            }
        }

        if (FabricLoader.getInstance().environmentType == EnvType.CLIENT) {
            ClientPlayConnectionEvents.JOIN.register { handler, sender, client ->

                val integrityConfig = IntegrityConfig()
                val modsFolderPath = FabricLoader.getInstance().gameDir.resolve("mods")
                modsFolderPath.toFile().listFiles { dir -> dir.extension == "jar" }.forEach {
                    val hc = Files.asByteSource(it).hash(Hashing.sha256())
                    integrityConfig.modInfos.add(ModInfo(it.name, hc.toString()))
                }

                val resourcepacksFolderPath = FabricLoader.getInstance().gameDir.resolve("resourcepacks")
                resourcepacksFolderPath.toFile().listFiles { dir -> true }.forEach {
                    if (it.isDirectory) {
                        integrityConfig.resourcepacksInfos.add(ResourcepacksInfo(it.name, "IMPOSSIBLE HASH"))
                        return@forEach
                    }
                    val hc = Files.asByteSource(it).hash(Hashing.sha256())
                    integrityConfig.resourcepacksInfos.add(ResourcepacksInfo(it.name, hc.toString()))
                }


                val pbb = PacketByteBufs.create()
                pbb.writeString(Json.encodeToString(integrityConfig))

                ClientPlayNetworking.send(INTEGRITY_PACKET_ID, pbb)
            }
        }
    }

    private fun registerCommands() = CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> ReloadFilesCmd.register(dispatcher) }

    private fun registerCallbacks() {}

}


