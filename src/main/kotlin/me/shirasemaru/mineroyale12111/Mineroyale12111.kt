package me.shirasemaru.mineroyale12111

import me.shirasemaru.mineroyale12111.command.MrCommand
import me.shirasemaru.mineroyale12111.config.ConfigManager
import me.shirasemaru.mineroyale12111.game.GameManager
import me.shirasemaru.mineroyale12111.listener.GameListener
import me.shirasemaru.mineroyale12111.listener.HealthRegainListener
import me.shirasemaru.mineroyale12111.listener.PlayerJoinListener
import me.shirasemaru.mineroyale12111.listener.SpectatorListener
import me.shirasemaru.mineroyale12111.ui.ScoreboardManager
import org.bukkit.plugin.java.JavaPlugin

class Mineroyale12111 : JavaPlugin() {

    private lateinit var gameManager: GameManager
    private lateinit var scoreboardManager: ScoreboardManager
    private lateinit var configManager: ConfigManager

    override fun onEnable() {

        /* ==========================
           Config生成
         ========================== */
        saveDefaultConfig()
        configManager = ConfigManager(this)
        configManager.load()

        /* ==========================
           マネージャー初期化（順番重要）
         ========================== */
        scoreboardManager = ScoreboardManager()

        gameManager = GameManager(
            this,
            configManager,
            scoreboardManager
        )

        /* ==========================
           Listener登録
         ========================== */

        server.pluginManager.registerEvents(
            PlayerJoinListener(gameManager),
            this
        )

        server.pluginManager.registerEvents(
            HealthRegainListener(gameManager),
            this
        )

        server.pluginManager.registerEvents(
            GameListener(gameManager),
            this
        )

        server.pluginManager.registerEvents(
            SpectatorListener(gameManager),
            this
        )

        /* ==========================
           コマンド登録
         ========================== */
        getCommand("mr")?.setExecutor(
            MrCommand(gameManager)
        )

        logger.info("Mineroyale12111 が有効化されました！")
    }

    override fun onDisable() {

        if (::gameManager.isInitialized) {
            // ★ 安全停止（勝者表示なし）
            gameManager.endGame(null)
        }

        logger.info("Mineroyale12111 が無効化されました。")
    }
}
