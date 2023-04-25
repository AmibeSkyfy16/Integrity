package ch.skyfy.integrity.command

import ch.skyfy.integrity.IntegrityMod
import ch.skyfy.integrity.config.Configs
import ch.skyfy.jsonconfiglib.ConfigManager
import com.mojang.brigadier.Command
import com.mojang.brigadier.Command.SINGLE_SUCCESS
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import java.util.concurrent.CompletableFuture

class ReloadFilesCmd : Command<ServerCommandSource> {

    companion object {
        fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
            val cmd = literal("integrity").requires { source -> source.hasPermissionLevel(4) }
                .then(
                    literal("reload").then(
                        argument("fileName", StringArgumentType.string()).suggests(Companion::getConfigFiles).executes(ReloadFilesCmd())
                    )
                )
            dispatcher.register(cmd)
        }

        @Suppress("UNUSED_PARAMETER")
        private fun <S : ServerCommandSource> getConfigFiles(commandContext: CommandContext<S>, suggestionsBuilder: SuggestionsBuilder): CompletableFuture<Suggestions> {
            val list = mutableListOf<String>()
            list.add(Configs.CUSTOM_MODS_CONFIG.relativePath.fileName.toString())
            list.add(Configs.MODPACK_MODS_LIST.relativePath.fileName.toString())
            list.add("ALL")
            return CommandSource.suggestMatching(list, suggestionsBuilder)
        }
    }

    override fun run(context: CommandContext<ServerCommandSource>): Int {
        val fileName = StringArgumentType.getString(context, "fileName")
        val list = mutableListOf<Boolean>()
        if (fileName == "ALL") {
            list.add(ConfigManager.reloadConfig(Configs.CUSTOM_MODS_CONFIG))
            list.add(ConfigManager.reloadConfig(Configs.MODPACK_MODS_LIST))
        } else {
            when (fileName) {
                "custom-mods-config.json" -> list.add(ConfigManager.reloadConfig(Configs.CUSTOM_MODS_CONFIG))
                "modpack-mods-list.json" -> list.add(ConfigManager.reloadConfig(Configs.MODPACK_MODS_LIST))
            }
        }

        if (list.contains(false)) {
            context.source.sendFeedback(Text.literal("Configuration could not be reloaded"), false)
            IntegrityMod.LOGGER.warn("Configuration could not be reloaded")
        } else {
            context.source.sendFeedback(Text.literal("The configuration was successfully reloaded"), false)
            IntegrityMod.LOGGER.info("The configuration was successfully reloaded")
        }

        return SINGLE_SUCCESS
    }

}