package me.shirasemaru.mineroyale12111

import me.shirasemaru.mineroyale12111.bootstrap.PluginScope
import me.shirasemaru.mineroyale12111.command.MrCommand
import me.shirasemaru.mineroyale12111.command.SpecCommand
import me.shirasemaru.mineroyale12111.game.GameManager
import me.shirasemaru.mineroyale12111.listener.EndCrystalListener
import me.shirasemaru.mineroyale12111.listener.GameListener
import me.shirasemaru.mineroyale12111.listener.HealthRegainListener
import me.shirasemaru.mineroyale12111.listener.PlayerJoinListener
import me.shirasemaru.mineroyale12111.listener.SpectatorListener
import me.shirasemaru.mineroyale12111.service.game.MessageService
import org.bukkit.plugin.java.JavaPlugin

class Mineroyale12111 : JavaPlugin() {

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

        logger.info("Mineroyale12111 を有効化しました。")
    }

    override fun onDisable() {
        if (::gameManager.isInitialized) {
            gameManager.stopGame()
        }

        logger.info("Mineroyale12111 を無効化しました。")
    }
}
