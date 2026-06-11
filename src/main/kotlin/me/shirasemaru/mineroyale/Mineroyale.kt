package me.shirasemaru.mineroyale

import me.shirasemaru.mineroyale.bootstrap.PluginScope
import me.shirasemaru.mineroyale.command.MrCommand
import me.shirasemaru.mineroyale.command.SpecCommand
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
        pluginScope = PluginScope.create(this)
        gameManager = pluginScope.gameManager
        messageService = pluginScope.messageService

        server.pluginManager.registerEvents(PlayerJoinListener(gameManager), this)
        server.pluginManager.registerEvents(HealthRegainListener(gameManager), this)
        server.pluginManager.registerEvents(GameListener(this, pluginScope.configManager, gameManager), this)
        server.pluginManager.registerEvents(SpectatorListener(gameManager), this)
        server.pluginManager.registerEvents(EndCrystalListener(gameManager), this)

        val mrCommand = MrCommand(gameManager, messageService)
        getCommand("mr")?.setExecutor(mrCommand)
        getCommand("mr")?.setTabCompleter(mrCommand)
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
