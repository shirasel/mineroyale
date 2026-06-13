package me.shirasemaru.mineroyale.config

class ConfiguredWorldNotFoundException(
    val worldName: String
) : IllegalStateException(
    "Configured world '$worldName' was not found. Please check world.name in config.yml."
)
