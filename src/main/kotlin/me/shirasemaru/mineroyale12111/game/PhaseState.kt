package me.shirasemaru.mineroyale12111.game

enum class PhaseState(
    val displayName: String
) {
    IDLE("待機中"),
    PREPARING("縮小待機"),
    SHRINKING("縮小中"),
    FINAL_MOVING("最終移動")
}
