package chat.willow.burrow.configuration

import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.helper.INamed
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

data class BaseConfig(val network: NetworkConfig, val server: ServerConfig)
data class NetworkConfig(override val name: String): INamed
data class ServerConfig(override val name: String, val host: String, val port: Int, val motd: File): INamed

class BurrowConfig {
    private val LOGGER = loggerFor<BurrowConfig>()

    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    val all: BaseConfig
    val network: NetworkConfig
        get() = all.network
    val server: ServerConfig
        get() = all.server

    init {
        all = mapper.readValue(File("burrow.yaml"))

        LOGGER.info("Loaded config: $all")
    }
}