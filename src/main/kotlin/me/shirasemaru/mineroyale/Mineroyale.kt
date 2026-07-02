package me.shirasemaru.mineroyale

import me.shirasemaru.mineroyale.bootstrap.PluginScope
import me.shirasemaru.mineroyale.command.MrCommand
import me.shirasemaru.mineroyale.command.SpecCommand
import me.shirasemaru.mineroyale.config.ConfiguredWorldNotFoundException
import me.shirasemaru.mineroyale.game.GameManager
import me.shirasemaru.mineroyale.listener.EndCrystalListener
import me.shirasemaru.mineroyale.listener.GameListener
import me.shirasemaru.mineroyale.listener.HealthRegainListener
import me.shirasemaru.mineroyale.listener.PlayerJoinListener
import me.shirasemaru.mineroyale.listener.SpectatorListener
import me.shirasemaru.mineroyale.service.game.MessageService
import kotlinx.coroutines.cancel
import org.bukkit.plugin.java.JavaPlugin

class Mineroyale : JavaPlugin() {

    private lateinit var gameManager: GameManager
    private lateinit var messageService: MessageService
    private lateinit var pluginScope: PluginScope

    override fun onEnable() {
        saveDefaultConfig()
        try {
            pluginScope = PluginScope.create(this)
        } catch (error: ConfiguredWorldNotFoundException) {
            logger.severe("Mineroyale を有効化できませんでした。")
            logger.severe("config.yml の world.name='${error.worldName}' に一致するワールドが見つかりません。")
            logger.severe("server.properties やワールドフォルダ名を確認し、config.yml の world.name を修正してください。")
            server.pluginManager.disablePlugin(this)
            return
        }

        gameManager = pluginScope.gameManager
        messageService = pluginScope.messageService

        server.pluginManager.registerEvents(PlayerJoinListener(gameManager), this)
        server.pluginManager.registerEvents(HealthRegainListener(gameManager), this)
        server.pluginManager.registerEvents(GameListener(this, pluginScope.configManager, gameManager), this)
        server.pluginManager.registerEvents(SpectatorListener(gameManager), this)
        server.pluginManager.registerEvents(EndCrystalListener(gameManager), this)

        val mrCommand = MrCommand(
            gameManager = gameManager,
            messageService = messageService,
            permissionService = pluginScope.permissionService,
            offlinePlayerResolver = pluginScope.offlinePlayerResolver
        )
        getCommand("mr")?.apply {
            setExecutor(mrCommand)
            tabCompleter = mrCommand
        }
        getCommand("spec")?.setExecutor(SpecCommand(gameManager, messageService))

        logger.info("Mineroyale を有効化しました。")
    }

    override fun onDisable() {
        if (::gameManager.isInitialized) {
            gameManager.stopGame()
        }
        if (::pluginScope.isInitialized) {
            pluginScope.coroutineScope.cancel()
        }

        logger.info("Mineroyale を無効化しました。")
    }
}
