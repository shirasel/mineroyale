package me.shirasemaru.mineroyale.command

object PermissionNodes {
    const val ADMIN = "mineroyale.admin"
    const val COMMAND_MR = "mineroyale.command.mr"
    const val COMMAND_START = "mineroyale.command.start"
    const val COMMAND_STOP = "mineroyale.command.stop"
    const val COMMAND_RELOAD = "mineroyale.command.reload"
    const val COMMAND_ADDOP = "mineroyale.command.addop"

    fun resolve(input: String): String? =
        when (input.lowercase()) {
            "admin", ADMIN -> ADMIN
            "mr", COMMAND_MR -> COMMAND_MR
            "start", COMMAND_START -> COMMAND_START
            "stop", COMMAND_STOP -> COMMAND_STOP
            "reload", COMMAND_RELOAD -> COMMAND_RELOAD
            "addop", COMMAND_ADDOP -> COMMAND_ADDOP
            else -> null
        }

    fun displayName(permission: String): String =
        when (permission) {
            ADMIN -> "admin"
            COMMAND_MR -> "mr"
            COMMAND_START -> "start"
            COMMAND_STOP -> "stop"
            COMMAND_RELOAD -> "reload"
            COMMAND_ADDOP -> "addop"
            else -> permission
        }
}
