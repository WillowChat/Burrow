package unit.chat.willow.burrow.configuration

import chat.willow.kale.helper.INamed

fun serverName(name: String = "🐰"): INamed {
    return object : INamed {
        override val name = name
    }
}