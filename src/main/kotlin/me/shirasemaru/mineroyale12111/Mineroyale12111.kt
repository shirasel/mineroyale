package me.shirasemaru.mineroyale12111

import me.shirasemaru.mineroyale12111.command.MrCommand
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.game.GameManager
import me.shirasemaru.mineroyale12111.listener.GameListener
import me.shirasemaru.mineroyale12111.listener.HealthRegainListener
import me.shirasemaru.mineroyale12111.listener.PlayerJoinListener
import me.shirasemaru.mineroyale12111.listener.SpectatorListener
import me.shirasemaru.mineroyale12111.service.game.CountdownService
import me.shirasemaru.mineroyale12111.service.game.MessageService
import me.shirasemaru.mineroyale12111.service.game.VictoryService
import me.shirasemaru.mineroyale12111.service.player.PlayerRegistry
import me.shirasemaru.mineroyale12111.service.player.PlayerSetupService
import me.shirasemaru.mineroyale12111.service.player.SpectatorService
import me.shirasemaru.mineroyale12111.service.tracking.CompassTrackingService
import me.shirasemaru.mineroyale12111.ui.ScoreboardManager
import org.bukkit.plugin.java.JavaPlugin

class Mineroyale12111 : JavaPlugin() {

    private lateinit var gameManager: GameManager
    private lateinit var scoreboardManager: ScoreboardManager
    private lateinit var configManager: ConfigManager
    private lateinit var messageService: MessageService

    override fun onEnable() {
        saveDefaultConfig()
        configManager = ConfigManager(this)
        configManager.load()
        scoreboardManager = ScoreboardManager()
        messageService = MessageService()

        gameManager = GameManager(
            this,
            configManager,
            PlayerRegistry(),
            PlayerSetupService(configManager),
            SpectatorService(),
            CountdownService(this),
            messageService,
            scoreboardManager,
            VictoryService(this, messageService),
            CompassTrackingService(this, configManager)
        )

        server.pluginManager.registerEvents(PlayerJoinListener(gameManager), this)
        server.pluginManager.registerEvents(HealthRegainListener(gameManager), this)
        server.pluginManager.registerEvents(GameListener(this, configManager, gameManager), this)
        server.pluginManager.registerEvents(SpectatorListener(gameManager), this)

        val mrCommand = MrCommand(gameManager, messageService)
        getCommand("mr")?.setExecutor(mrCommand)
        getCommand("mr")?.setTabCompleter(mrCommand)

        logger.info("Mineroyale12111 を有効化しました。")
    }

    override fun onDisable() {
        if (::gameManager.isInitialized) {
            gameManager.stopGame()
        }

        logger.info("Mineroyale12111 を無効化しました。")
    }
}
