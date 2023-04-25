package ch.skyfy.integrity

import ch.skyfy.integrity.command.ReloadFilesCmd
import ch.skyfy.integrity.config.Configs
import ch.skyfy.integrity.config.ModInfo
import ch.skyfy.integrity.config.ModpackModsList
import ch.skyfy.integrity.utils.ModUtils
import ch.skyfy.integrity.utils.ModUtils.equalsIgnoreOrder
import ch.skyfy.jsonconfiglib.ConfigManager
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

        val received: MutableSet<String> = mutableSetOf()

        val MODPACK_MODS_LIST_CHECK = mutableMapOf<String, Boolean>()

        val MODPACK_MODS_LIST_PACKET_ID = Identifier(MOD_ID, "send_modpack_mods_list")
    }

    init {
        if (FabricLoader.getInstance().environmentType == EnvType.SERVER) {
            ConfigManager.loadConfigs(arrayOf(Configs::class.java))
        }
    }

    override fun onInitialize() {
        registerCommands()
        registerCallbacks()

        if (FabricLoader.getInstance().environmentType == EnvType.SERVER) {
            ServerPlayNetworking.registerGlobalReceiver(MODPACK_MODS_LIST_PACKET_ID) { server, player, handler, buf, responseSender ->
                received.add(player.uuidAsString)

                val modpackModsList = Json.decodeFromString<ModpackModsList>(buf.readString())

                if (Configs.MODPACK_MODS_LIST.serializableData.list equalsIgnoreOrder modpackModsList.list) {
                    MODPACK_MODS_LIST_CHECK[player.uuidAsString] = true
                } else {

                    val extraMods = mutableSetOf<ModInfo>()
                    modpackModsList.list.forEach { modpackModList ->
                        if (Configs.MODPACK_MODS_LIST.serializableData.list.none { it.fileName == modpackModList.fileName && it.fileHash == modpackModList.fileHash }) {
                            extraMods.add(modpackModList.copy())
                        }
                    }

                    val missingMods = mutableSetOf<ModInfo>()
                    Configs.MODPACK_MODS_LIST.serializableData.list.forEach { modpackModList ->
                        if (modpackModsList.list.none { it.fileName == modpackModList.fileName && it.fileHash == modpackModList.fileHash }) {
                            missingMods.add(modpackModList.copy())
                        }
                    }

                    if (extraMods.size > 0) {
                        LOGGER.info("The following extra mods have been found for player ${player.name.string}")
                        extraMods.forEach {
                            LOGGER.info(it.toString())
                        }
                    }

                    if (missingMods.size > 0) {
                        LOGGER.info("The following missing mods have been found for player ${player.name.string}")
                        extraMods.forEach {
                            LOGGER.info(it.toString())
                        }
                    }

                    val allowedExtraMods = Configs.CUSTOM_MODS_CONFIG.serializableData.globalAllowedExtraMods
                    Configs.CUSTOM_MODS_CONFIG.serializableData.allowedExtraModsPerPlayer[ModUtils.getPlayerNameWithUUID(player)]?.let {
                        allowedExtraMods.addAll(it)
                    }

                    if (allowedExtraMods equalsIgnoreOrder extraMods.toList()) {
                        LOGGER.info("All the extra mods are allowed for player ${player.name.string}")
                    } else {
                        val extraUnauthorizedMods = mutableListOf<ModInfo>()
                        extraMods.forEach { modInfo ->
                            if(allowedExtraMods.none { it.fileName == modInfo.fileName && it.fileHash == modInfo.fileHash }){
                                extraUnauthorizedMods.add(modInfo.copy())
                            }
                        }
                        if(extraUnauthorizedMods.size > 0){
                            LOGGER.warn("The following unauthorized extra mods have been found for player ${player.name.string}")
                            extraUnauthorizedMods.forEach {
                                LOGGER.warn(it.toString())
                            }

                            player.networkHandler.disconnect(Text.literal("There are some mods that the server does not want you to have, but that you have anyway !").setStyle(Style.EMPTY.withColor(Formatting.RED)))
                            MODPACK_MODS_LIST_CHECK[player.uuidAsString] = false
                            return@registerGlobalReceiver
                        }
                    }

                    val allowedMissingMods = Configs.CUSTOM_MODS_CONFIG.serializableData.globalAllowedMissingMods
                    Configs.CUSTOM_MODS_CONFIG.serializableData.allowedMissingModsPerPlayer[ModUtils.getPlayerNameWithUUID(player)]?.let {
                        allowedExtraMods.addAll(it)
                    }

                    if (allowedMissingMods equalsIgnoreOrder missingMods.toList()) {
                        LOGGER.info("All the missing mods are allowed for player ${player.name.string}")
                    } else {
                        val missingUnauthorizedMods = mutableListOf<ModInfo>()
                        missingMods.forEach { modInfo ->
                            if(allowedExtraMods.none { it.fileName == modInfo.fileName && it.fileHash == modInfo.fileHash }){
                                missingUnauthorizedMods.add(modInfo.copy())
                            }
                        }
                        if(missingUnauthorizedMods.size > 0){
                            LOGGER.warn("The following unauthorized missing mods have been found for player ${player.name.string}")
                            missingUnauthorizedMods.forEach {
                                LOGGER.warn(it.toString())
                            }

                            player.networkHandler.disconnect(Text.literal("There are mods that the server wants you to have, but you don't !").setStyle(Style.EMPTY.withColor(Formatting.RED)))
                            MODPACK_MODS_LIST_CHECK[player.uuidAsString] = false
                            return@registerGlobalReceiver
                        }
                    }

                    MODPACK_MODS_LIST_CHECK[player.uuidAsString] = true
                }

                println("welcome on the server !")

            }

            ServerPlayConnectionEvents.JOIN.register { handler, sender, client ->
                launch {
                    delay(20.seconds)
                    if (!received.contains(handler.player.uuidAsString)) {
                        println("The server did not receive your mod list. player ${handler.player.name.string} will be kick")
                        handler.player.networkHandler.disconnect(Text.literal("Mod Integrity is not installed !"))
                    }
                }
            }
        }

        if (FabricLoader.getInstance().environmentType == EnvType.CLIENT) {
            ClientPlayConnectionEvents.JOIN.register { handler, sender, client ->

                val modpackModsList = ModpackModsList()
                val modsFolderPath = FabricLoader.getInstance().gameDir.resolve("mods")
                modsFolderPath.toFile().listFiles { dir -> dir.extension == "jar" }.forEach {
                    val hc = Files.asByteSource(it).hash(Hashing.sha256())
                    modpackModsList.list.add(ModInfo(it.name, hc.toString()))
                }
                val pbb = PacketByteBufs.create()
                pbb.writeString(Json.encodeToString(modpackModsList))

                ClientPlayNetworking.send(MODPACK_MODS_LIST_PACKET_ID, pbb)
            }
        }
    }

    private fun registerCommands() = CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> ReloadFilesCmd.register(dispatcher) }

    private fun registerCallbacks() {}

}


